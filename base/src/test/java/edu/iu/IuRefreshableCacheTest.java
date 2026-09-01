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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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

	private static IuRefreshableCache<String, String> cache(Supplier<IuRefreshableCacheConfiguration> config,
			UnsafeFunction<String, String> refresh) {
		return new IuRefreshableCache<>(config, refresh, key -> true);
	}

	private static IuRefreshableCache<String, String> cache(IuRefreshableCacheConfiguration config,
			UnsafeFunction<String, String> refresh) {
		return cache(() -> config, refresh);
	}

	private static IuRefreshableCacheConfiguration config(Duration refreshTtl, Duration cacheTtl) {
		return config(refreshTtl, cacheTtl, IuRefreshableCacheConfiguration.CALL_TTL);
	}

	private static IuRefreshableCacheConfiguration config(Duration refreshTtl, Duration cacheTtl, Duration callTtl) {
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
				return 1;
			}
		};
	}

	private static void awaitCalls(AtomicInteger calls, int expected) throws InterruptedException {
		final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
		while (calls.get() < expected && System.nanoTime() < expires)
			Thread.sleep(5L);
		assertEquals(expected, calls.get());
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
								key -> key, key -> true))
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
				key -> key + "/" + calls.incrementAndGet(), key -> !key.equals("write"))) {
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
		}, key -> !key.equals("write"))) {
			assertEquals("read/1", cache.apply("read"));
			assertEquals("write failed",
					assertThrows(IllegalStateException.class, () -> cache.apply("write")).getMessage());
			assertEquals("read/1", cache.apply("read"));
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
			Thread.sleep(125L);
			assertEquals("value2", cache.apply("key"));
			awaitCalls(calls, 3);
			assertEquals("value2", cache.apply("key"));
		}
	}

	@Test
	public void testFailedBackgroundRefreshServesLastGoodValue() throws Throwable {
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
		try (final var cached = cache(
				config(Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ofMillis(75L)), key -> {
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
		final var refreshTtl = Duration.ofMillis(50L);
		final var callTtl = Duration.ofMillis(150L);
		final var configuration = new IuRefreshableCacheConfiguration() {
			@Override
			public Duration getRefreshTtl() {
				return refreshTtl;
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
			Thread.sleep(225L);
			assertEquals("v1", cache.apply("key"));
			awaitCalls(calls, 3);
			awaitValue(cache, "key", "v3");
			released.set(true);
			Thread.sleep(100L);
			assertEquals("v3", cache.apply("key"));
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
		}, key -> true) {
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
		final var fail = new AtomicBoolean();
		final var calls = new AtomicInteger();
		final var ttl = Duration.ofMillis(75L);
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(ttl, Duration.ofSeconds(30L)), key -> "call/" + calls.incrementAndGet(), key -> true) {
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
			assertEquals("Remote invocation handler is closed",
					assertThrows(IllegalStateException.class, () -> cache.apply("key")).getMessage());
		}
		try (final var cache = new IuRefreshableCache<String, String>(
				() -> config(Duration.ofMinutes(5L), Duration.ofMinutes(30L)), key -> key, key -> true) {
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
}
