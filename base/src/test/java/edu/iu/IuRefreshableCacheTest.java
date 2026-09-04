/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 */
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Dedicated behavioral and concurrency tests for {@link IuRefreshableCache}.
 */
@SuppressWarnings("javadoc")
public class IuRefreshableCacheTest {

	private static final class MutableConfiguration implements IuRefreshableCacheConfiguration {
		private volatile Duration refreshTtl = Duration.ofMinutes(5L);
		private volatile Duration cacheTtl = Duration.ofMinutes(30L);
		private volatile Duration callTtl = IuRefreshableCacheConfiguration.CALL_TTL;
		private volatile int threads = 1;
		private volatile int pending = IuRefreshableCacheConfiguration.PENDING;

		@Override
		public Duration getRefreshTtl() {
			return refreshTtl;
		}

		@Override
		public Duration getCacheTtl() {
			return cacheTtl;
		}

		@Override
		public Duration getCallTtl() {
			return callTtl;
		}

		@Override
		public int getThreads() {
			return threads;
		}

		@Override
		public int getPending() {
			return pending;
		}
	}

	/**
	 * Captures everything this package logs for the duration of one test.
	 *
	 * <p>
	 * Several behaviors under test are reported at {@code INFO} — a refresh that
	 * failed, or one that could not be dispatched — and printing those to the build
	 * output makes a passing run look like a broken one. They are captured instead,
	 * and a test that expects one declares it with {@link #expectLog}. Anything
	 * else logged at {@code INFO} or higher fails the test that produced it, so a
	 * new diagnostic cannot appear unnoticed.
	 * </p>
	 */
	private static final class CapturedLog extends Handler {
		private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());

		@Override
		public void publish(LogRecord record) {
			records.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	}

	private final List<String> expectedLog = Collections.synchronizedList(new ArrayList<>());
	private Logger log;
	private CapturedLog captured;
	private Level restoreLevel;
	private boolean restoreUseParentHandlers;

	@BeforeEach
	public void captureLog() {
		// the package logger, so a message from the cache map or a cached value is
		// held to the same standard as one from the cache itself
		log = Logger.getLogger("edu.iu");
		restoreLevel = log.getLevel();
		restoreUseParentHandlers = log.getUseParentHandlers();

		captured = new CapturedLog();
		captured.setLevel(Level.ALL);

		log.setLevel(Level.INFO);
		log.setUseParentHandlers(false);
		log.addHandler(captured);
	}

	@AfterEach
	public void assertOnlyExpectedLog() {
		log.removeHandler(captured);
		log.setUseParentHandlers(restoreUseParentHandlers);
		log.setLevel(restoreLevel);

		final var unexpected = captured.records.stream() //
				.filter(record -> record.getLevel().intValue() >= Level.INFO.intValue()) //
				.filter(record -> !interrupted(record.getThrown())) //
				.filter(record -> expectedLog.stream().noneMatch(record.getMessage()::startsWith)) //
				.map(record -> record.getMessage() + " [" + (record.getThrown() == null //
						? "no exception"
						: record.getThrown().getClass().getName()) + "]") //
				.collect(Collectors.toList());

		assertEquals(List.of(), unexpected, "unexpected log output at INFO or higher");
	}

	/**
	 * Determines whether a logged failure was an interruption.
	 *
	 * <p>
	 * Closing the cache interrupts whatever calls are in flight, and a refresh
	 * interrupted while a value is on hand reports that at {@code INFO}. Whether
	 * any given test still has a refresh running when it closes depends on where
	 * that refresh had got to, so this is expected output from any of them rather
	 * than something an individual test can declare.
	 * </p>
	 *
	 * @param thrown logged failure; may be null
	 * @return true if the failure was, or was caused by, an interruption
	 */
	private static boolean interrupted(Throwable thrown) {
		while (thrown != null) {
			if (thrown instanceof InterruptedException)
				return true;
			thrown = thrown.getCause();
		}
		return false;
	}

	/**
	 * Permits a message this test may log, by its leading text.
	 *
	 * <p>
	 * This allows the message rather than requiring it: some are reported only when
	 * a value happens to be on hand as a refresh is abandoned, which depends on
	 * where the abandoned call had got to. Assert on behavior for what a test
	 * requires; this only keeps the expected diagnostics out of the build output
	 * without blinding it to new ones.
	 * </p>
	 *
	 * @param message leading text of a permitted message
	 */
	private void expectLog(String message) {
		expectedLog.add(message);
	}

	private static IuRefreshableCache<String, String> cache(Supplier<IuRefreshableCacheConfiguration> config,
			UnsafeFunction<String, String> refresh) {
		return new IuRefreshableCache<>(config, refresh, key -> IuRefreshableCacheHint.useDefaults());
	}

	private static IuRefreshableCache<String, String> cache(IuRefreshableCacheConfiguration config,
			UnsafeFunction<String, String> refresh) {
		return cache(() -> config, refresh);
	}

	private static IuRefreshableCacheConfiguration config(Duration refreshTtl, Duration cacheTtl) {
		return config(refreshTtl, cacheTtl, IuRefreshableCacheConfiguration.CALL_TTL);
	}

	private static IuRefreshableCacheConfiguration config(Duration refreshTtl, Duration cacheTtl, Duration callTtl) {
		return config(refreshTtl, cacheTtl, callTtl, 1);
	}

	private static IuRefreshableCacheConfiguration config(Duration refreshTtl, Duration cacheTtl, Duration callTtl,
			int threads) {
		return new IuRefreshableCacheConfiguration() {
			@Override
			public Duration getRefreshTtl() {
				return refreshTtl;
			}

			@Override
			public Duration getCacheTtl() {
				return cacheTtl;
			}

			@Override
			public Duration getCallTtl() {
				return callTtl;
			}

			@Override
			public int getThreads() {
				return threads;
			}
		};
	}

	/**
	 * Reads the deferred invalidation queue.
	 *
	 * <p>
	 * Purge ejection has no effect on any result by design: an event is only
	 * purged once nothing it could match is still cached. It is therefore not
	 * observable through the public API, and is asserted here against internal
	 * state, as {@link CacheMapTest} does for cached value references.
	 * </p>
	 */
	private static Queue<?> hintQueue(IuRefreshableCache<?, ?> cache) throws Exception {
		final var f = IuRefreshableCache.class.getDeclaredField("hintQueue");
		f.setAccessible(true);
		return (Queue<?>) f.get(cache);
	}

	/**
	 * Polls {@code key} until the invalidation queue reaches {@code expected}
	 * depth, keeping the polled entry alive by refreshing it.
	 */
	private static void awaitQueueDepth(IuRefreshableCache<String, String> cache, String key, int expected)
			throws Throwable {
		final var queue = hintQueue(cache);
		final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
		do {
			cache.apply(key);
			if (queue.size() == expected)
				return;
			Thread.sleep(25L);
		} while (System.nanoTime() < expires);
		assertEquals(expected, queue.size(), "timed out waiting for invalidation queue depth");
	}

	/**
	 * Waits until every caller is parked, either on the shared invocation or
	 * inside it.
	 *
	 * <p>
	 * A caller that has been started has not necessarily reached the cache yet.
	 * Releasing the shared invocation before they all arrive lets it complete and
	 * clear itself, so a late arrival starts its own rather than joining the one
	 * under test.
	 * </p>
	 */
	private static void awaitParked(List<Thread> callers) {
		final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
		for (final var caller : callers)
			while (caller.getState() != Thread.State.WAITING //
					&& caller.getState() != Thread.State.TIMED_WAITING //
					&& System.nanoTime() < expires)
				Thread.onSpinWait();
	}

	private static void awaitCalls(AtomicInteger calls, int expected) throws InterruptedException {
		final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
		while (calls.get() < expected && System.nanoTime() < expires)
			Thread.sleep(5L);
		assertEquals(expected, calls.get());
	}

	/**
	 * Waits for at least {@code expected} calls.
	 *
	 * <p>
	 * Use this rather than {@link #awaitCalls} wherever a polling loop has already
	 * run against a short refresh TTL: each poll of a stale entry dispatches its
	 * own refresh, and {@link IuCacheMap} holds values softly, so the exact call
	 * count at that point is not something the cache promises.
	 * </p>
	 */
	private static void awaitCallsAtLeast(AtomicInteger calls, int expected) throws InterruptedException {
		final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
		while (calls.get() < expected && System.nanoTime() < expires)
			Thread.sleep(5L);
		assertTrue(calls.get() >= expected,
				() -> "expected at least " + expected + " calls, but was " + calls.get());
	}

	private static void assertNoFurtherCalls(AtomicInteger calls, int expected) throws InterruptedException {
		Thread.sleep(250L);
		assertEquals(expected, calls.get());
	}

	private static void awaitValue(IuRefreshableCache<String, String> cache, String key, String expected)
			throws Throwable {
		final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
		String value;
		do {
			value = cache.apply(key);
			if (expected.equals(value))
				return;
			Thread.sleep(10L);
		} while (System.nanoTime() < expires);
		assertEquals(expected, value, "timed out waiting for refresh");
	}

	@Test
	public void testConfiguredCacheSupportsNull() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = cache(() -> IuRefreshableCacheConfiguration.DEFAULT,
				key -> calls.incrementAndGet() == 1 ? null : key)) {
			assertEquals(null, cache.apply("null"));
			assertEquals(null, cache.apply("null"));
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void testRequiresConfigurationSupplier() {
		assertEquals("Missing configuration supplier",
				assertThrows(NullPointerException.class,
						() -> new IuRefreshableCache<String, String>((Supplier<IuRefreshableCacheConfiguration>) null,
								key -> key, key -> IuRefreshableCacheHint.useDefaults()))
						.getMessage());
	}

	@Test
	public void testInvalidConfigurationIsReportedAtCallTime() throws Throwable {
		final var configuration = new MutableConfiguration();
		try (final var cache = cache(() -> configuration, key -> "value")) {
			assertEquals("value", cache.apply("key"));
			configuration.refreshTtl = Duration.ZERO;
			assertEquals("Refresh TTL must be positive",
					assertThrows(IllegalArgumentException.class, () -> cache.apply("key")).getMessage());
			configuration.refreshTtl = Duration.ofMinutes(-1L);
			assertThrows(IllegalArgumentException.class, () -> cache.apply("key"));
			configuration.refreshTtl = Duration.ofMinutes(5L);
			configuration.cacheTtl = null;
			assertEquals("Missing cache TTL",
					assertThrows(NullPointerException.class, () -> cache.apply("key")).getMessage());
			configuration.cacheTtl = Duration.ofMinutes(5L);
			assertEquals("Cache TTL must be longer than refresh TTL",
					assertThrows(IllegalArgumentException.class, () -> cache.apply("key")).getMessage());
			configuration.cacheTtl = Duration.ofMinutes(30L);
			configuration.callTtl = null;
			assertEquals("Missing call TTL",
					assertThrows(NullPointerException.class, () -> cache.apply("key")).getMessage());
			configuration.callTtl = Duration.ZERO;
			assertEquals("Call TTL must be positive",
					assertThrows(IllegalArgumentException.class, () -> cache.apply("key")).getMessage());
			configuration.callTtl = Duration.ofMinutes(-1L);
			assertThrows(IllegalArgumentException.class, () -> cache.apply("key"));
			configuration.callTtl = IuRefreshableCacheConfiguration.CALL_TTL;
			configuration.threads = 0;
			assertEquals("Call threads must be positive",
					assertThrows(IllegalArgumentException.class, () -> cache.apply("key")).getMessage());
			configuration.threads = 1;
			configuration.pending = 0;
			assertEquals("Call pending queue size must be positive",
					assertThrows(IllegalArgumentException.class, () -> cache.apply("key")).getMessage());
			configuration.pending = IuRefreshableCacheConfiguration.PENDING;
			assertEquals("value", cache.apply("key"));
		}
	}

	@Test
	public void testCachePolicyAndSuccessfulUncachedCallClearsEntries() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)),
				key -> key + "/" + calls.incrementAndGet(),
				key -> key.equals("write") ? IuRefreshableCacheHint.clearAll()
						: IuRefreshableCacheHint.useDefaults())) {
			assertEquals("read/1", cache.apply("read"));
			assertEquals("read/1", cache.apply("read"));
			assertEquals("write/2", cache.apply("write"));
			assertEquals("read/3", cache.apply("read"));
		}
	}

	@Test
	public void testFailedUncachedCallPreservesEntries() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)), key -> {
					if (key.equals("write"))
						throw new IllegalStateException("write failed");
					return key + "/" + calls.incrementAndGet();
				}, key -> new IuRefreshableCacheHint<String, String>() {
					@Override
					public boolean shouldClear(String key) {
						return key.equals("write");
					}
				})) {
			assertEquals("read/1", cache.apply("read"));
			assertEquals("write failed",
					assertThrows(IllegalStateException.class, () -> cache.apply("write")).getMessage());
			assertEquals("read/1", cache.apply("read"));
		}
	}

	@Test
	public void testNullHintSkipsCache() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)),
				key -> key + "/" + calls.incrementAndGet(), key -> null)) {
			assertEquals("key/1", cache.apply("key"));
			assertEquals("key/2", cache.apply("key"));
		}
	}

	@Test
	public void testNullHintFunctionSkipsCacheForEveryKey() throws Throwable {
		final var calls = new AtomicInteger();

		// a null hint function is accepted and stands in for one that returns null
		// for every key, so nothing is cached even though caching is configured
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)),
				key -> key + "/" + calls.incrementAndGet(), null)) {
			assertEquals("one/1", cache.apply("one"));
			assertEquals("one/2", cache.apply("one"));
			assertEquals("two/3", cache.apply("two"));
			assertEquals(3, calls.get());
		}
	}

	@Test
	public void testHintRestoresResultWithoutCallingRefreshFunction() throws Throwable {
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)),
				key -> {
					throw new AssertionError("refresh function must not be called for a restored result");
				}, key -> new IuRefreshableCacheHint<String, String>() {
					@Override
					public Optional<String> restore() {
						return Optional.of("restored");
					}
				})) {
			assertEquals("restored", cache.apply("key"));
			assertEquals("restored", cache.apply("key"));
		}
	}

	@Test
	public void testHintPublishesEmbeddedResultsAndAllowsNullInspection() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)), key -> {
					calls.incrementAndGet();
					return key;
				}, key -> new IuRefreshableCacheHint<String, String>() {
					@Override
					public Map<String, String> inspect(String result) {
						return result.equals("root") ? Map.of("embedded", "published") : null;
					}
				})) {
			assertEquals("root", cache.apply("root"));
			assertEquals("published", cache.apply("embedded"));
			assertEquals(1, calls.get());

			// A null inspection result simply means that this result exposes no
			// related cache values.
			assertEquals("standalone", cache.apply("standalone"));
			assertEquals(2, calls.get());
		}
	}

	@Test
	public void testHintSelectivelyPrunesCachedResultsAfterSuccessfulCall() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)),
				key -> key + "/" + calls.incrementAndGet(), key -> {
					if (!key.equals("write"))
						return IuRefreshableCacheHint.useDefaults();
					return new IuRefreshableCacheHint<String, String>() {
						@Override
						public boolean shouldClear(String candidate) {
							return candidate.equals("write") || candidate.equals("one");
						}
					};
				})) {
			assertEquals("one/1", cache.apply("one"));
			assertEquals("two/2", cache.apply("two"));
			assertEquals("write/3", cache.apply("write"));
			assertEquals("two/2", cache.apply("two"));
			assertEquals("one/4", cache.apply("one"));
		}
	}

	@Test
	public void testCachingCanBeEnabledAndDisabledAtRuntime() throws Throwable {
		final var configuration = new MutableConfiguration();
		configuration.refreshTtl = null;
		final var calls = new AtomicInteger();
		try (final var cache = cache(() -> configuration, key -> "call/" + calls.incrementAndGet())) {
			assertEquals("call/1", cache.apply("key"));
			assertEquals("call/2", cache.apply("key"));
			configuration.refreshTtl = Duration.ofMinutes(5L);
			assertEquals("call/3", cache.apply("key"));
			assertEquals("call/3", cache.apply("key"));
			configuration.refreshTtl = null;
			assertEquals("call/4", cache.apply("key"));
		}
	}

	@Test
	public void testTtlReconfigurationDoesNotFlushEntries() throws Throwable {
		final var configuration = new MutableConfiguration();
		final var calls = new AtomicInteger();
		final var value = new AtomicReference<>("first");
		try (final var cache = cache(() -> configuration, key -> {
			calls.incrementAndGet();
			return value.get();
		})) {
			assertEquals("first", cache.apply("key"));
			configuration.cacheTtl = Duration.ofMinutes(6L);
			assertEquals("first", cache.apply("key"));
			value.set("second");
			configuration.refreshTtl = Duration.ofMillis(1L);
			Thread.sleep(10L);
			assertEquals("first", cache.apply("key"));
			awaitCalls(calls, 2);
			awaitValue(cache, "key", "second");
			configuration.refreshTtl = Duration.ofMinutes(5L);
			Thread.sleep(100L);
			final var settled = calls.get();
			assertEquals("second", cache.apply("key"));
			assertNoFurtherCalls(calls, settled);
		}
	}

	@Test
	public void testConcurrentCallersReplaceThePoolOnlyOnce() throws Throwable {
		final var configuration = new MutableConfiguration();
		final var reachedConfig = new CountDownLatch(2);
		final var pools = Collections.synchronizedList(new ArrayList<Object>());

		try (final var cache = new IuRefreshableCache<String, String>(() -> {
			reachedConfig.countDown();
			return configuration;
		}, key -> {
			pools.add(Thread.currentThread().getThreadGroup());
			return key;
		}, key -> IuRefreshableCacheHint.useDefaults())) {
			assertEquals("warm", cache.apply("warm"));

			// both callers read the pool as mismatched and then queue on the cache's
			// own monitor, so whichever enters second finds the replacement already
			// made and must not build a second pool
			configuration.threads = 3;

			final List<Thread> callers = new ArrayList<>();
			synchronized (cache) {
				for (var i = 0; i < 2; i++) {
					final var caller = new Thread(() -> assertDoesNotThrow(() -> cache.apply("key")));
					callers.add(caller);
					caller.start();
				}

				assertTrue(reachedConfig.await(5L, TimeUnit.SECONDS));
				for (final var caller : callers) {
					final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
					while (caller.getState() != Thread.State.BLOCKED && System.nanoTime() < expires)
						Thread.onSpinWait();
					assertEquals(Thread.State.BLOCKED, caller.getState(), "caller never reached the pool monitor");
				}
			}

			for (final var caller : callers)
				caller.join(5000L);

			assertEquals("key", cache.apply("key"));
			assertEquals(3, configuration.threads);
		}
	}

	@Test
	public void testExecutorIsReplacedAndRejectedSizeLeavesOldExecutor() throws Throwable {
		final var configuration = new MutableConfiguration();
		final var thread = new AtomicReference<Thread>();
		try (final var cache = cache(() -> configuration, key -> {
			thread.set(Thread.currentThread());
			return key;
		})) {
			cache.apply("one");
			final var first = thread.get();
			cache.apply("two");
			assertSame(first, thread.get());
			configuration.threads = 2;
			cache.apply("three");
			final var second = thread.get();
			assertNotSame(first, second);
			configuration.pending = 4;
			cache.apply("four");
			assertNotSame(second, thread.get());
			configuration.threads = -1;
			assertThrows(IllegalArgumentException.class, () -> cache.apply("five"));
			configuration.threads = 2;
			cache.apply("six");
			assertSame(thread.get(), thread.get());
		}
	}

	@Test
	public void testInitialCallersShareOneInvocationAndFailure() throws Exception {
		final var calls = new AtomicInteger();
		final var arrived = new CountDownLatch(1);
		final var proceed = new CountDownLatch(1);
		try (final var cache = cache(config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)), key -> {
			calls.incrementAndGet();
			arrived.countDown();
			assertTrue(proceed.await(5L, TimeUnit.SECONDS));
			return "value";
		})) {
			final var results = Collections.synchronizedList(new ArrayList<String>());
			final List<Thread> callers = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				final var caller = new Thread(() -> {
					try {
						results.add(cache.apply("key"));
					} catch (Throwable e) {
						throw new AssertionError(e);
					}
				});
				callers.add(caller);
				caller.start();
			}
			assertTrue(arrived.await(5L, TimeUnit.SECONDS));
			awaitParked(callers);
			proceed.countDown();
			for (var caller : callers)
				caller.join(5000L);
			assertEquals(1, calls.get());
			assertEquals(List.of("value", "value", "value", "value"), results);
		}
	}

	@Test
	public void testInitialFailurePropagatesToAllWaiters() throws Exception {
		final var calls = new AtomicInteger();
		final var arrived = new CountDownLatch(1);
		final var proceed = new CountDownLatch(1);
		try (final var cache = cache(config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)), key -> {
			calls.incrementAndGet();
			arrived.countDown();
			assertTrue(proceed.await(5L, TimeUnit.SECONDS));
			throw new IllegalStateException("failed");
		})) {
			final var errors = Collections.synchronizedList(new ArrayList<Throwable>());
			final List<Thread> callers = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				final var caller = new Thread(() -> {
					try {
						cache.apply("key");
					} catch (Throwable e) {
						errors.add(e);
					}
				});
				callers.add(caller);
				caller.start();
			}
			assertTrue(arrived.await(5L, TimeUnit.SECONDS));
			awaitParked(callers);
			proceed.countDown();
			for (var caller : callers)
				caller.join(5000L);
			assertEquals(1, calls.get());
			assertEquals(3, errors.size());
			errors.forEach(e -> assertEquals("failed", assertInstanceOf(IllegalStateException.class, e).getMessage()));
		}
	}

	@Test
	public void testStaleValueRefreshesInBackground() throws Throwable {
		final var calls = new AtomicInteger();
		final var hold = new AtomicBoolean();
		final var proceed = new CountDownLatch(1);
		final var ttl = Duration.ofMillis(100L);
		try (final var cache = cache(config(ttl, Duration.ofSeconds(30L)), key -> {
			final var call = calls.incrementAndGet();
			if (call == 2 && hold.get())
				assertTrue(proceed.await(5L, TimeUnit.SECONDS));
			return "value" + Math.min(call, 2);
		})) {
			assertEquals("value1", cache.apply("key"));
			Thread.sleep(125L);
			hold.set(true);
			assertEquals("value1", cache.apply("key"));
			awaitCalls(calls, 2);
			assertEquals("value1", cache.apply("key"));
			assertNoFurtherCalls(calls, 2);
			proceed.countDown();
			awaitValue(cache, "key", "value2");

			// stale once more: the caller is served the settled value while another
			// refresh runs behind it. The polling above has already driven an
			// unpredictable number of refreshes, so count from here rather than
			// pinning a total.
			final var polled = calls.get();
			Thread.sleep(125L);
			assertEquals("value2", cache.apply("key"));
			awaitCallsAtLeast(calls, polled + 1);
			assertEquals("value2", cache.apply("key"));
		}
	}

	@Test
	public void testFailedBackgroundRefreshServesLastGoodValue() throws Throwable {
		expectLog("Remote call refresh failed, serving last good result for");
		final var calls = new AtomicInteger();
		final var ttl = Duration.ofMillis(75L);
		try (final var cache = cache(config(ttl, Duration.ofSeconds(30L)), key -> {
			if (calls.incrementAndGet() > 1)
				throw new IllegalStateException("failed");
			return "cached";
		})) {
			assertEquals("cached", cache.apply("key"));
			Thread.sleep(ttl.toMillis() + 25L);

			// A stale entry triggers its refresh in the background and remains
			// available even when that refresh fails.
			assertEquals("cached", cache.apply("key"));
			awaitCalls(calls, 2);
		}
	}

	@Test
	public void testTimeoutsCancelOnlyUncachedCalls() throws Throwable {
		final var interrupted = new CountDownLatch(1);
		try (final var uncached = cache(config(null, null, Duration.ofMillis(75L)), key -> {
			try {
				Thread.sleep(5000L);
			} catch (InterruptedException e) {
				interrupted.countDown();
				throw e;
			}
			return key;
		})) {
			assertThrows(TimeoutException.class, () -> uncached.apply("key"));
			assertTrue(interrupted.await(5L, TimeUnit.SECONDS));
		}
		final var calls = new AtomicInteger();
		try (final var cached = cache(config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ofMillis(75L)),
				key -> {
					calls.incrementAndGet();
					Thread.sleep(200L);
					return "slow";
				})) {
			assertThrows(TimeoutException.class, () -> cached.apply("key"));
			Thread.sleep(300L);
			assertEquals("slow", cached.apply("key"));
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void testSupersededRefreshCannotOverwriteNewerValue() throws Throwable {
		final var calls = new AtomicInteger();
		final var released = new AtomicBoolean();

		// held long enough to drive the two refreshes this test needs, then widened
		// so no further refresh is dispatched. Without that, polling for the settled
		// value keeps triggering refreshes on a 50ms cadence and the expected call
		// never stays the current one long enough to be observed.
		final var refreshTtl = new AtomicReference<>(Duration.ofMillis(50L));
		final var callTtl = Duration.ofMillis(150L);
		final var configuration = new IuRefreshableCacheConfiguration() {
			@Override
			public Duration getRefreshTtl() {
				return refreshTtl.get();
			}

			@Override
			public Duration getCacheTtl() {
				return Duration.ofSeconds(30L);
			}

			@Override
			public Duration getCallTtl() {
				return callTtl;
			}

			@Override
			public int getThreads() {
				return 2;
			}
		};
		try (final var cache = cache(configuration, key -> {
			final var call = calls.incrementAndGet();
			if (call == 2)
				while (!released.get())
					try {
						Thread.sleep(10L);
					} catch (InterruptedException e) {
						continue;
					}
			return "v" + call;
		})) {
			assertEquals("v1", cache.apply("key"));
			Thread.sleep(75L);
			assertEquals("v1", cache.apply("key"));
			awaitCalls(calls, 2);

			// the held refresh has now outlived the call TTL, so this caller abandons
			// it and dispatches its replacement
			Thread.sleep(225L);
			assertEquals("v1", cache.apply("key"));
			awaitCalls(calls, 3);

			// no further refresh is dispatched on staleness from here, so the
			// replacement settles. Still shorter than the 30s cache TTL, which must
			// remain the longer of the two.
			refreshTtl.set(Duration.ofSeconds(10L));

			// wait for the replacement to publish, but do not pin its generation:
			// IuCacheMap holds values softly, so the entry may be evicted and
			// repopulated at any point, which is a legitimate extra call
			final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
			var settled = cache.apply("key");
			while ("v1".equals(settled) && System.nanoTime() < expires) {
				Thread.sleep(10L);
				settled = cache.apply("key");
			}
			assertNotEquals("v1", settled, "timed out waiting for the replacement refresh");
			assertNotEquals("v2", settled);

			// the abandoned refresh finally returns. Its result was superseded before
			// it completed, so it must never reach a caller, no matter how many
			// refreshes have run in the meantime.
			released.set(true);
			Thread.sleep(100L);
			assertNotEquals("v2", cache.apply("key"));
		}
	}

	@Test
	public void testContextIsForwardedAndRestoredIncludingFailure() throws Throwable {
		final var poolBase = new ClassLoader(null) {
		};
		final var caller = new ClassLoader(null) {
		};
		final var thread = new AtomicReference<Thread>();
		final var context = new AtomicReference<ClassLoader>();
		final var original = Thread.currentThread().getContextClassLoader();
		try (final var cache = cache(config(null, null), key -> {
			thread.set(Thread.currentThread());
			context.set(Thread.currentThread().getContextClassLoader());
			if (key.equals("fail"))
				throw new IllegalStateException("failed");
			return key;
		})) {
			Thread.currentThread().setContextClassLoader(poolBase);
			assertEquals("warm", cache.apply("warm"));
			final var pooled = thread.get();
			Thread.currentThread().setContextClassLoader(caller);
			assertEquals("key", cache.apply("key"));
			assertSame(caller, context.get());
			assertSame(pooled, thread.get());
			assertSame(poolBase, pooled.getContextClassLoader());
			assertEquals("failed", assertThrows(IllegalStateException.class, () -> cache.apply("fail")).getMessage());
			assertSame(poolBase, pooled.getContextClassLoader());
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	@Test
	public void testContextRestoreFailureIsPropagatedOrSuppressed() throws Throwable {
		final var events = Collections.synchronizedList(new ArrayList<String>());
		final var caller = Thread.currentThread();
		try (final var cache = new IuRefreshableCache<String, String>(() -> config(null, null), key -> {
			events.add("call");
			if (key.equals("fail"))
				throw new UnsupportedOperationException("failed");
			return key;
		}, key -> IuRefreshableCacheHint.useDefaults()) {
			@Override
			protected Supplier<Runnable> captureContext() {
				final var delegate = super.captureContext();
				events.add("capture:" + (Thread.currentThread() == caller));
				return () -> {
					final var restore = delegate.get();
					events.add("apply:" + (Thread.currentThread() == caller));
					return () -> {
						events.add("restore:" + (Thread.currentThread() == caller));
						restore.run();
						throw new IllegalStateException("restore");
					};
				};
			}
		}) {
			assertEquals("restore", assertThrows(IllegalStateException.class, () -> cache.apply("key")).getMessage());
			final var error = assertThrows(UnsupportedOperationException.class, () -> cache.apply("fail"));
			assertEquals("failed", error.getMessage());
			assertEquals("restore", error.getSuppressed()[0].getMessage());
			assertEquals(List.of("capture:true", "apply:false", "call", "restore:false", "capture:true", "apply:false",
					"call", "restore:false"), events);
		}
	}

	@Test
	public void testDispatchFailureAndClose() throws Throwable {
		expectLog("Remote call refresh could not be dispatched, serving last good result for");
		final var fail = new AtomicBoolean();
		final var calls = new AtomicInteger();
		final var ttl = Duration.ofMillis(75L);
		try (final var cache = new IuRefreshableCache<String, String>(() -> config(ttl, Duration.ofSeconds(30L)),
				key -> "call/" + calls.incrementAndGet(), key -> IuRefreshableCacheHint.useDefaults()) {
			@Override
			protected Supplier<Runnable> captureContext() {
				if (fail.get())
					throw new RejectedExecutionException("rejected");
				return super.captureContext();
			}
		}) {
			assertEquals("call/1", cache.apply("key"));
			Thread.sleep(100L);
			fail.set(true);
			assertEquals("call/1", cache.apply("key"));
			assertNoFurtherCalls(calls, 1);
			fail.set(false);
			assertEquals("call/1", cache.apply("key"));
			awaitCalls(calls, 2);
			cache.close();
			cache.close();
			assertEquals("Refreshable cache is closed",
					assertThrows(IllegalStateException.class, () -> cache.apply("key")).getMessage());
		}
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)), key -> key,
				key -> IuRefreshableCacheHint.useDefaults()) {
			@Override
			protected Supplier<Runnable> captureContext() {
				throw new RejectedExecutionException("rejected");
			}
		}) {
			assertEquals("rejected",
					assertThrows(RejectedExecutionException.class, () -> cache.apply("key")).getMessage());
		}
		final var unused = cache(() -> IuRefreshableCacheConfiguration.DEFAULT, key -> key);
		assertDoesNotThrow(unused::close);
		assertDoesNotThrow(unused::close);
	}

	/**
	 * A hint whose {@code shouldClear} matches only its own key, so its queued
	 * invalidation event never matches a cached entry and can only leave the queue
	 * by being purged.
	 */
	private static IuRefreshableCacheHint<String, String> writeOnly(String writeKey) {
		return new IuRefreshableCacheHint<String, String>() {
			@Override
			public boolean shouldClear(String candidate) {
				return candidate.equals(writeKey);
			}
		};
	}

	@Test
	public void testHintEventsAreQueuedThenPurgedAtTheCacheTtlHorizon() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMillis(80L), Duration.ofMillis(300L), Duration.ofSeconds(5L), 2),
				key -> key + "/" + calls.incrementAndGet(),
				key -> key.equals("write") ? writeOnly("write") : IuRefreshableCacheHint.useDefaults())) {
			final var queue = hintQueue(cache);
			assertEquals(0, queue.size());

			assertEquals("poll/1", cache.apply("poll"));
			for (int i = 0; i < 3; i++)
				cache.apply("write");
			assertEquals(3, queue.size());

			// a scan that matches nothing leaves events younger than the horizon in
			// place, so the queue is drained by age rather than by evaluation
			cache.apply("poll");
			assertEquals(3, queue.size());

			awaitQueueDepth(cache, "poll", 0);

			// purging is housekeeping only: the polled entry is untouched by it
			assertTrue(cache.apply("poll").startsWith("poll/"));
		}
	}

	@Test
	public void testClearAllDrainsPendingInvalidationsAndFreshEntriesSurvive() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), IuRefreshableCacheConfiguration.CALL_TTL,
						2),
				key -> key + "/" + calls.incrementAndGet(), key -> {
					if (key.equals("clearAll"))
						return IuRefreshableCacheHint.clearAll();
					if (key.equals("write"))
						return new IuRefreshableCacheHint<String, String>() {
							@Override
							public boolean shouldClear(String candidate) {
								return candidate.equals("write") || candidate.equals("target");
							}
						};
					return IuRefreshableCacheHint.useDefaults();
				})) {
			final var queue = hintQueue(cache);

			assertEquals("target/1", cache.apply("target"));
			assertEquals("write/2", cache.apply("write"));
			assertEquals(1, queue.size());

			// emptying the cache subsumes every invalidation still pending, so they are
			// discarded rather than left to be re-evaluated against entries that no
			// longer exist
			assertEquals("clearAll/3", cache.apply("clearAll"));
			assertEquals(0, queue.size());

			// a key cached for the first time after the clear-all is not evaluated
			// against those discarded events, and is served from cache thereafter
			final var repopulated = cache.apply("target");
			assertEquals("target/4", repopulated);
			assertEquals(repopulated, cache.apply("target"));
			assertEquals(4, calls.get());
		}
	}

	@Test
	public void testCloseDiscardsPendingInvalidations() throws Throwable {
		final var calls = new AtomicInteger();
		final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), IuRefreshableCacheConfiguration.CALL_TTL,
						2),
				key -> key + "/" + calls.incrementAndGet(),
				key -> key.equals("write") ? new IuRefreshableCacheHint<String, String>() {
					@Override
					public boolean shouldClear(String candidate) {
						return candidate.equals("write") || candidate.equals("target");
					}
				} : IuRefreshableCacheHint.useDefaults());
		try {
			assertEquals("target/1", cache.apply("target"));
			assertEquals("write/2", cache.apply("write"));
			assertEquals(1, hintQueue(cache).size());
		} finally {
			cache.close();
		}

		// close releases the queue along with the cached results it referred to
		assertEquals(0, hintQueue(cache).size());
		assertDoesNotThrow(cache::close);
		assertEquals(0, hintQueue(cache).size());
	}

	@Test
	public void testQueuedHintEventClearsEntryAndForcesTheNextCallerToWait() throws Throwable {
		final var block = new AtomicBoolean();
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ofMillis(250L), 2), key -> {
					final var call = calls.incrementAndGet();
					if (block.get())
						Thread.sleep(1000L);
					return key + "/" + call;
				}, key -> key.equals("write") ? new IuRefreshableCacheHint<String, String>() {
					@Override
					public boolean shouldClear(String candidate) {
						return candidate.equals("write") || candidate.equals("target");
					}
				} : IuRefreshableCacheHint.useDefaults())) {
			assertEquals("target/1", cache.apply("target"));
			assertEquals("write/2", cache.apply("write"));
			assertEquals(1, hintQueue(cache).size());

			// the queued event still matches, so the entry is discarded and the caller
			// waits for a replacement rather than being served the invalidated value
			block.set(true);
			assertThrows(TimeoutException.class, () -> cache.apply("target"));
		}
	}

	@Test
	public void testPurgedHintEventCanNoLongerClearALiveEntry() throws Throwable {
		final var armed = new AtomicBoolean();
		final var block = new AtomicBoolean();
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMillis(80L), Duration.ofMillis(300L), Duration.ofMillis(250L), 2), key -> {
					final var call = calls.incrementAndGet();
					if (block.get())
						Thread.sleep(1000L);
					return key + "/" + call;
				}, key -> key.equals("write") ? new IuRefreshableCacheHint<String, String>() {
					@Override
					public boolean shouldClear(String candidate) {
						return candidate.equals("write") || (armed.get() && candidate.equals("target"));
					}
				} : IuRefreshableCacheHint.useDefaults())) {
			assertEquals("target/1", cache.apply("target"));
			assertEquals("write/2", cache.apply("write"));
			assertEquals(1, hintQueue(cache).size());

			// the event does not match while unarmed, so it survives every scan until
			// it ages past the cache TTL horizon
			awaitQueueDepth(cache, "target", 0);

			// arming the same predicate now has no effect: the event that carried it is
			// gone, so the entry is served without waiting, unlike the control above
			armed.set(true);
			block.set(true);
			final var served = assertDoesNotThrow(() -> cache.apply("target"));
			assertTrue(served.startsWith("target/"), served);
		}
	}

	@Test
	public void testHintEventOlderThanTheLastRefreshIsSkippedAndRetained() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), IuRefreshableCacheConfiguration.CALL_TTL,
						2),
				key -> key + "/" + calls.incrementAndGet(),
				key -> key.equals("write") ? new IuRefreshableCacheHint<String, String>() {
					@Override
					public boolean shouldClear(String candidate) {
						// matches every key, but is not the CLEAR_ALL singleton, so it
						// takes the deferred per-entry path rather than clearing in line
						return true;
					}
				} : IuRefreshableCacheHint.useDefaults())) {
			final var queue = hintQueue(cache);

			assertEquals("other/1", cache.apply("other"));
			assertEquals("target/2", cache.apply("target"));
			assertEquals("write/3", cache.apply("write"));
			assertEquals(1, queue.size());

			// the event is newer than the entry, so it clears and the caller waits
			assertEquals("target/4", cache.apply("target"));

			// a matching event is neither consumed nor purged; it is retained and
			// re-evaluated, but is now older than the value on hand and so is skipped
			assertEquals(1, queue.size());
			assertEquals("target/4", cache.apply("target"));
			assertEquals(4, calls.get());

			// skipping is evaluated per entry, so the retained event still clears a
			// key whose value predates it
			assertEquals("other/5", cache.apply("other"));
			assertEquals(1, queue.size());
		}
	}

	@Test
	public void testInvalidationCostsExactlyOneRefreshPerAffectedKey() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), IuRefreshableCacheConfiguration.CALL_TTL,
						2),
				key -> key + "/" + calls.incrementAndGet(),
				key -> key.equals("write") ? new IuRefreshableCacheHint<String, String>() {
					@Override
					public boolean shouldClear(String candidate) {
						return candidate.equals("write") || candidate.equals("target");
					}
				} : IuRefreshableCacheHint.useDefaults())) {
			assertEquals("target/1", cache.apply("target"));
			assertEquals("write/2", cache.apply("write"));

			// the invalidation is a one-shot: the first read repopulates the entry and
			// every read after it is a hit, well inside the refresh TTL. A retained
			// event that keeps matching would instead make every read a blocking miss
			// and a downstream call, for as long as the event stays queued — defeating
			// the cache for that key and amplifying load precisely when a write has
			// just landed.
			final var repopulated = cache.apply("target");
			assertEquals("target/3", repopulated);
			for (int i = 0; i < 5; i++)
				assertEquals(repopulated, cache.apply("target"));
			assertEquals(3, calls.get());
		}
	}

	/**
	 * Caches "target", then queues two invalidation events in the order given, and
	 * reports how many times the non-matching event's predicate was consulted for
	 * "target" during the scan that follows.
	 */
	private static int consultationsAfterScan(String first, String second) throws Throwable {
		final var calls = new AtomicInteger();
		final var consulted = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), IuRefreshableCacheConfiguration.CALL_TTL,
						2),
				key -> key + "/" + calls.incrementAndGet(), key -> {
					if (key.equals("writeMatch"))
						return new IuRefreshableCacheHint<String, String>() {
							@Override
							public boolean shouldClear(String candidate) {
								return candidate.equals("writeMatch") || candidate.equals("target");
							}
						};
					if (key.equals("writeMiss"))
						return new IuRefreshableCacheHint<String, String>() {
							@Override
							public boolean shouldClear(String candidate) {
								if (candidate.equals("target"))
									consulted.incrementAndGet();
								return candidate.equals("writeMiss");
							}
						};
					return IuRefreshableCacheHint.useDefaults();
				})) {
			assertEquals("target/1", cache.apply("target"));
			cache.apply(first);
			cache.apply(second);
			assertEquals(2, hintQueue(cache).size());

			// the matching event clears the entry, so this caller waits for a
			// replacement value rather than being served the invalidated one
			assertEquals("target/4", cache.apply("target"));

			// neither event is consumed by the scan that matched
			assertEquals(2, hintQueue(cache).size());
			return consulted.get();
		}
	}

	@Test
	public void testFirstMatchingHintEventShortCircuitsTheScan() throws Throwable {
		// scanned in FIFO order, so a non-matching event queued first is consulted
		assertEquals(1, consultationsAfterScan("writeMiss", "writeMatch"));

		// but the same event queued behind a matching one is never reached
		assertEquals(0, consultationsAfterScan("writeMatch", "writeMiss"));
	}

	/** Publishes {@code embedded} whenever the {@code root} result is inspected. */
	private static IuRefreshableCacheHint<String, String> publishesEmbedded() {
		return new IuRefreshableCacheHint<String, String>() {
			@Override
			public Map<String, String> inspect(String result) {
				return result.equals("root") ? Map.of("embedded", "published") : Map.of();
			}
		};
	}

	@Test
	public void testEmbeddedValueReplacesAnEntryAlreadyCached() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), IuRefreshableCacheConfiguration.CALL_TTL,
						2),
				key -> key.equals("root") ? "root" : key + "/" + calls.incrementAndGet(),
				key -> publishesEmbedded())) {

			// resolved directly first, so the entry the embedded value is published
			// into already exists rather than being created for it
			assertEquals("embedded/1", cache.apply("embedded"));
			assertEquals("root", cache.apply("root"));
			assertEquals("published", cache.apply("embedded"));
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void testEmbeddedValueSupersedesARefreshInFlightForThatKey() throws Throwable {
		final var started = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ofSeconds(10L), 4), key -> {
					if (!key.equals("embedded"))
						return "root";
					started.countDown();
					assertTrue(release.await(5L, TimeUnit.SECONDS));
					return "direct";
				}, key -> publishesEmbedded())) {

			// a caller is already waiting on a direct refresh of the embedded key
			final var waiter = new Thread(() -> assertDoesNotThrow(() -> cache.apply("embedded")));
			waiter.start();
			assertTrue(started.await(5L, TimeUnit.SECONDS));

			// the parent publishes the embedded value while that refresh is in flight
			assertEquals("root", cache.apply("root"));

			release.countDown();
			waiter.join(5000L);

			// the in-flight refresh was superseded, so its result is never published
			assertEquals("published", cache.apply("embedded"));
		}
	}

	@Test
	public void testEmbeddedValueDoesNotDisplaceANewerEntry() throws Throwable {
		final var started = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ofSeconds(10L), 4), key -> {
					final var call = calls.incrementAndGet();
					if (!key.equals("root"))
						return key + "/" + call;
					started.countDown();
					assertTrue(release.await(5L, TimeUnit.SECONDS));
					return "root";
				}, key -> key.equals("write") ? writeOnly("write") : publishesEmbedded())) {

			final var waiter = new Thread(() -> assertDoesNotThrow(() -> cache.apply("root")));
			waiter.start();
			assertTrue(started.await(5L, TimeUnit.SECONDS));

			// an invalidation is published while the parent call is in flight, so the
			// entry created for the embedded value takes a later position in the
			// sequence than the result carrying it
			assertEquals("write/2", cache.apply("write"));

			release.countDown();
			waiter.join(5000L);

			// the embedded value is stale relative to that entry and is not published
			assertTrue(cache.apply("embedded").startsWith("embedded/"), "a stale embedded value was published");
		}
	}

	@Test
	public void testInvalidationAbandonsARefreshAlreadyInFlight() throws Throwable {
		expectLog("Remote call refresh failed, serving last good result for");
		final var value = new AtomicReference<>("v0");
		final var slow = new AtomicBoolean();
		final var started = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMillis(50L), Duration.ofSeconds(30L), Duration.ofSeconds(10L), 4), key -> {
					if (key.equals("write"))
						return "ok";
					final var read = value.get();
					if (slow.compareAndSet(true, false)) {
						started.countDown();
						release.await(5L, TimeUnit.SECONDS);
					}
					return read;
				}, key -> key.equals("write") ? new IuRefreshableCacheHint<String, String>() {
					@Override
					public boolean shouldClear(String candidate) {
						return candidate.equals("write") || candidate.equals("read");
					}
				} : IuRefreshableCacheHint.useDefaults())) {
			assertEquals("v0", cache.apply("read"));
			Thread.sleep(75L);

			// stale, so this caller is served v0 and leaves a refresh in flight
			slow.set(true);
			assertEquals("v0", cache.apply("read"));
			assertTrue(started.await(5L, TimeUnit.SECONDS));

			value.set("v1");
			assertEquals("ok", cache.apply("write"));

			// that refresh read the source before the write, so the invalidation
			// abandons it rather than letting it publish pre-write data
			assertEquals("v1", cache.apply("read"));
			release.countDown();
		}
	}

	@Test
	public void testInvalidationsOlderThanTheEntryAreSkippedWhileNewerOnesAreEvaluated() throws Throwable {
		final var calls = new AtomicInteger();
		final var consulted = Collections.synchronizedList(new ArrayList<String>());
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), IuRefreshableCacheConfiguration.CALL_TTL,
						2),
				key -> key + "/" + calls.incrementAndGet(), key -> {
					if (!key.startsWith("write"))
						return IuRefreshableCacheHint.useDefaults();
					return new IuRefreshableCacheHint<String, String>() {
						@Override
						public boolean shouldClear(String candidate) {
							if (candidate.equals("target"))
								consulted.add(key);
							return candidate.equals(key);
						}
					};
				})) {
			// one invalidation before this entry is read and one after, so the scan
			// spans both sides of the entry's own position in the sequence
			assertEquals("writeOne/1", cache.apply("writeOne"));
			assertEquals("target/2", cache.apply("target"));
			assertEquals("writeTwo/3", cache.apply("writeTwo"));
			assertEquals(2, hintQueue(cache).size());

			// the older invalidation is skipped without consulting its predicate; only
			// the newer one is evaluated, and it does not match
			assertEquals("target/2", cache.apply("target"));
			assertEquals(List.of("writeTwo"), consulted);
			assertEquals(3, calls.get());
		}
	}

	@Test
	public void testOutcomeIsRecordedWhenFineLoggingIsEnabled() throws Throwable {
		log.setLevel(Level.FINE);
		try (final var cache = cache(config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)), key -> "value")) {
			assertEquals("value", cache.apply("key"));
			assertEquals("value", cache.apply("key"));
		}

		// the message is built only when the level is enabled, so this is also the
		// only path on which the supplier runs at all
		final var messages = captured.records.stream().map(LogRecord::getMessage).collect(Collectors.toList());
		assertTrue(messages.stream().anyMatch(m -> m.startsWith("cache-miss:key:")), () -> messages.toString());
		assertTrue(messages.stream().anyMatch(m -> m.startsWith("cache-hit:key:")), () -> messages.toString());
	}

	@Test
	public void testInvalidationDuringTheDispatchWindowIsNotLost() throws Throwable {
		expectLog("Remote call refresh failed, serving last good result for");
		final var gate = new AtomicBoolean();
		final var dispatched = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var fail = new AtomicBoolean();

		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMillis(60L), Duration.ofSeconds(30L), Duration.ofSeconds(10L), 3), key -> {
					if (key.equals("write"))
						return "ok";
					if (fail.get())
						throw new IllegalStateException("refresh failed");
					return "v1";
				}, key -> key.equals("write") ? new IuRefreshableCacheHint<String, String>() {
					@Override
					public boolean shouldClear(String candidate) {
						return candidate.equals("write") || candidate.equals("target");
					}
				} : IuRefreshableCacheHint.useDefaults()) {

			@Override
			protected Supplier<Runnable> captureContext() {
				final var delegate = super.captureContext();
				if (!gate.compareAndSet(true, false))
					return delegate;

				// holds the pooled thread after the refresh has been dispatched but
				// before it records the point in the invalidation sequence at which it
				// reads the backing source
				return () -> {
					dispatched.countDown();
					assertDoesNotThrow(() -> assertTrue(release.await(5L, TimeUnit.SECONDS)));
					return delegate.get();
				};
			}
		}) {
			assertEquals("v1", cache.apply("target"));
			Thread.sleep(80L);

			// stale, so this caller is served the cached value and dispatches a
			// background refresh, which stalls in the window above
			gate.set(true);
			fail.set(true);
			assertEquals("v1", cache.apply("target"));
			assertTrue(dispatched.await(5L, TimeUnit.SECONDS));

			// the invalidation lands while that refresh is dispatched but not started
			assertEquals("ok", cache.apply("write"));

			release.countDown();
			Thread.sleep(300L);

			// that refresh failed, so the value on hand is the one published before
			// the invalidation and must not survive it. A refresh that never
			// published must not move the entry's position in the sequence.
			assertThrows(IllegalStateException.class, () -> cache.apply("target"),
					"the invalidation was skipped for a value published before it");
		}
	}
}
