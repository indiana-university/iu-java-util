/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.iu;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A refresh-ahead cache for values resolved by an {@link UnsafeFunction}, with
 * hint-directed caching, background refresh, downstream outage tolerance, and
 * deferred invalidation.
 *
 * <p>
 * This cache is a <em>defensive</em> short-term cache: it exists to keep an
 * actively read value close at hand and to absorb a brief downstream outage,
 * not to serve as a system of record. It is designed for values that are read
 * far more often than they change, resolved from a stable backing source, and
 * tolerable to serve slightly stale.
 * </p>
 *
 * <h2>Lookup lifecycle</h2>
 *
 * <p>
 * Every {@link #apply(Object)} invocation proceeds through the same steps.
 * </p>
 *
 * <ol>
 * <li><strong>Hint</strong>: the cache hint function is consulted for the key.
 * A null hint bypasses the cache entirely for that invocation.</li>
 * <li><strong>Configuration snapshot</strong>: the configuration supplier is
 * read and its values validated. An invalid value fails this invocation only;
 * the cache itself is left intact.</li>
 * <li><strong>Call pool</strong>: the thread pool is resolved, and replaced if
 * its size no longer matches the configuration.</li>
 * <li><strong>Cache decision</strong>: the invocation either bypasses the cache
 * (see <em>Uncached invocations</em>) or resolves against it (see <em>Cached
 * invocations</em>).</li>
 * </ol>
 *
 * <h3>Uncached invocations</h3>
 *
 * <p>
 * An invocation bypasses the cache when caching is disabled
 * ({@link IuRefreshableCacheConfiguration#getRefreshTtl() refresh TTL} is
 * null), the hint function returned null, or the hint's
 * {@link IuRefreshableCacheHint#shouldClear(Object) shouldClear} returns true
 * for its own key. The last case is the <em>write key</em> idiom: a key that
 * mutates the backing source is never itself cached, and on success publishes
 * an invalidation for the keys it affected.
 * </p>
 *
 * <p>
 * An uncached call is dispatched to the pool and joined in line, bounded by the
 * {@link IuRefreshableCacheConfiguration#getCallTtl() call TTL}; on timeout the
 * call is cancelled, since no other caller is waiting on it. Invalidation is
 * published only after the call <em>succeeds</em>, so a failed write never
 * discards good cached data.
 * </p>
 *
 * <h3>Cached invocations</h3>
 *
 * <p>
 * A cached invocation resolves to exactly one of four outcomes.
 * </p>
 *
 * <dl>
 * <dt>hit</dt>
 * <dd>The entry holds a value published less than one refresh TTL ago. It is
 * returned without any call.</dd>
 * <dt>hit, refreshing</dt>
 * <dd>The entry holds a value older than the refresh TTL. The caller returns
 * that value immediately and a refresh runs in the background. This is the
 * common steady-state path: readers never pay for a refresh.</dd>
 * <dt>miss</dt>
 * <dd>The entry holds no value — it was never populated, it was invalidated, or
 * the {@link IuRefreshableCacheConfiguration#getCacheTtl() cache TTL} elapsed
 * without a successful refresh. The caller waits, bounded by the call TTL. All
 * concurrent callers for the same key share one invocation and one result — or
 * one failure. On timeout the call is left running, so it can still populate
 * the cache for the callers behind it.</dd>
 * <dt>degraded</dt>
 * <dd>A refresh failed, or could not be dispatched because the pending queue
 * was full. The last good value continues to be served, at {@code INFO}, until
 * the cache TTL elapses. Only once there is no last good value does a caller
 * see the failure.</dd>
 * </dl>
 *
 * <h2>Time to live</h2>
 *
 * <p>
 * Three independent intervals govern an entry, measured from the moment its
 * last successful refresh was <em>published</em>.
 * </p>
 *
 * <dl>
 * <dt>{@link IuRefreshableCacheConfiguration#getRefreshTtl() refresh TTL}</dt>
 * <dd>How long a value is served without triggering a refresh. This is the
 * staleness bound under healthy conditions. Null disables caching entirely, and
 * the cache TTL is then unused.</dd>
 * <dt>{@link IuRefreshableCacheConfiguration#getCacheTtl() cache TTL}</dt>
 * <dd>How long a value remains available once refreshes stop succeeding; must
 * be longer than the refresh TTL. The interval between the two is the
 * <em>outage tolerance window</em>. A successful refresh restarts the window,
 * so a healthy entry never expires.</dd>
 * <dt>{@link IuRefreshableCacheConfiguration#getCallTtl() call TTL}</dt>
 * <dd>How long any single call may run. It also bounds a background refresh: a
 * refresh still in flight after the call TTL is cancelled and replaced, so a
 * hung call cannot block every later refresh for its key.</dd>
 * </dl>
 *
 * <p>
 * Publication time is stored rather than a derived expiration, so a
 * reconfigured refresh TTL applies immediately to entries already cached, in
 * both directions, without discarding them. The cache TTL is applied as each
 * entry is stored, so a reconfigured cache TTL takes effect for an entry at its
 * next successful refresh.
 * </p>
 *
 * <h2>Cache hints</h2>
 *
 * <p>
 * The hint function supplied at construction is consulted once per invocation,
 * before the cache is read, so it <em>should</em> be inexpensive: its cost is
 * paid on every cache hit. The {@link IuRefreshableCacheHint} it returns
 * directs three separate behaviors.
 * </p>
 *
 * <dl>
 * <dt>{@link IuRefreshableCacheHint#shouldClear(Object)}</dt>
 * <dd>Serves a dual role. Evaluated against the invocation's <em>own</em> key
 * it selects the uncached write-key path described above. Evaluated against
 * <em>other</em> keys, after that call succeeds, it selects which cached
 * entries the write invalidated.</dd>
 * <dt>{@link IuRefreshableCacheHint#restore()}</dt>
 * <dd>Supplies a value resolved elsewhere — typically by a peer node in the
 * same cluster — in place of calling the refresh function. It is consulted at
 * the start of every refresh, including the first, so a hint that always
 * restores never contacts the backing source at all. It is <em>not</em>
 * consulted on the uncached path.</dd>
 * <dt>{@link IuRefreshableCacheHint#inspect(Object)}</dt>
 * <dd>Extracts related values embedded in a result and publishes them under
 * their own keys, so a single call can populate many entries. Because a
 * restored value short-circuits the refresh function, inspection is skipped for
 * restored values.</dd>
 * </dl>
 *
 * <h2>Invalidation</h2>
 *
 * <p>
 * Invalidation is deferred rather than applied eagerly, because a hint
 * describes which keys it affects by predicate rather than by enumeration.
 * </p>
 *
 * <p>
 * {@link IuRefreshableCacheHint#clearAll()} is the exception: it is recognized
 * by identity, and immediately after a successful call it discards every
 * pending invalidation and then clears the entire cache, both in line. A hint
 * that merely returns true from {@code shouldClear} for every key is
 * <em>not</em> equivalent — it takes the deferred path below.
 * </p>
 *
 * <p>
 * Every other invalidating hint is queued, timestamped at the moment its call
 * succeeded. Each cached lookup then scans that queue, under the entry's own
 * monitor, and applies three rules to each event in turn.
 * </p>
 *
 * <dl>
 * <dt>purge</dt>
 * <dd>An event older than the cache TTL is ejected from the queue. Nothing it
 * could still match has survived that long, so this is housekeeping: it bounds
 * queue growth, and does not affect any result.</dd>
 * <dt>skip</dt>
 * <dd>An event that precedes the entry's own position in the invalidation
 * sequence is ignored for that entry, because the value on hand was published
 * after it. Ordering is by sequence rather than by timestamp, so two events
 * that fall in the same clock tick are still ordered, and an invalidation can
 * never tie with a publication and be lost. The event remains queued for other
 * entries.</dd>
 * <dt>clear</dt>
 * <dd>Otherwise the event's {@code shouldClear} is consulted for the key. On
 * the first match the entry's value is discarded and the scan stops, converting
 * the lookup into a miss; a refresh already in flight for that key is
 * abandoned, since it too predates the invalidation. The matching event is
 * <em>not</em> removed: it stays queued for other entries, but stops matching
 * this one, whose replacement takes a later position in the sequence.</dd>
 * </dl>
 *
 * <p>
 * An invalidation therefore costs one refresh per affected key, not one per
 * subsequent read.
 * </p>
 *
 * <p>
 * <strong>Implementation Note:</strong> apart from a clear-all and
 * {@link #close()}, which drain it outright, the queue is drained only by
 * cached lookups, and the scan stops at the first matching event. A workload
 * with no cached reads, or one whose reads match early in the queue, does not
 * purge. Scan cost is proportional to queue depth, which is bounded by the
 * invalidating call rate multiplied by the cache TTL — so a long cache TTL
 * combined with a high write rate is the configuration to avoid.
 * {@code shouldClear} is invoked while the entry monitor is held, so it
 * <em>must</em> return quickly and <em>must not</em> invoke a remote method or
 * touch this cache.
 * </p>
 *
 * <h2>Call pool</h2>
 *
 * <p>
 * All calls, foreground and background alike, run on a lazily created pool of
 * daemon threads sized by {@link IuRefreshableCacheConfiguration#getThreads()
 * threads} with a bounded backlog of
 * {@link IuRefreshableCacheConfiguration#getPending() pending} calls. Idle
 * threads time out, so an idle cache holds no threads. Changing either value
 * replaces the pool; the replaced pool is shut down gracefully so calls already
 * in flight run to completion.
 * </p>
 *
 * <p>
 * A full backlog rejects the dispatch. For a key with a last good value that is
 * absorbed — the stale value is served — so the pool bounds are a load-shedding
 * control, not only a resource control. Sizing them too tightly converts
 * pressure into staleness; sizing them too loosely converts it into latency.
 * </p>
 *
 * <p>
 * Because calls run on pooled threads, thread-bound context does not reach them
 * unless forwarded. See {@link #captureContext()}, which forwards the context
 * {@link ClassLoader} and may be overridden to forward more.
 * </p>
 *
 * <h2>Tuning</h2>
 *
 * <dl>
 * <dt>read latency</dt>
 * <dd>Governed by the refresh TTL. Short enough to keep values current, long
 * enough that most reads are hits.</dd>
 * <dt>staleness</dt>
 * <dd>Bounded by the refresh TTL while healthy, and by the cache TTL while
 * degraded.</dd>
 * <dt>outage tolerance</dt>
 * <dd>The cache TTL less the refresh TTL. Widen to survive a longer downstream
 * outage; narrow to fail faster on one.</dd>
 * <dt>downstream load</dt>
 * <dd>Roughly one call per key per refresh TTL, plus one per uncached
 * invocation. Lengthen the refresh TTL to reduce it; use
 * {@link IuRefreshableCacheHint#inspect(Object)} to populate many keys per
 * call.</dd>
 * <dt>invalidation scan cost</dt>
 * <dd>Proportional to cache TTL times the invalidating call rate. Reduce by
 * shortening the cache TTL, or by making invalidation coarse (clear-all)
 * instead of predicate-based.</dd>
 * <dt>concurrency and backpressure</dt>
 * <dd>Threads and pending, as described above.</dd>
 * <dt>failure latency</dt>
 * <dd>The call TTL, which is also the ceiling on how long a hung refresh
 * occupies a pool thread before it is cancelled.</dd>
 * </dl>
 *
 * <p>
 * Sound starting points: a refresh TTL of minutes, a cache TTL no longer than
 * {@code PT30M}, and a call TTL short enough that a stalled downstream is
 * noticed promptly. See {@link IuRefreshableCacheConfiguration} for defaults.
 * </p>
 *
 * <h2>Concurrency and lifecycle</h2>
 *
 * <p>
 * This class is thread-safe. Concurrent first callers for one key share a
 * single entry and a single invocation. Each entry's mutable state is guarded
 * by its own monitor, so lookups of distinct keys do not contend on it.
 * </p>
 *
 * <p>
 * The cache is shared by all callers regardless of the context they call from.
 * When forwarded context can change a result, fold the distinguishing context
 * into the cache key; otherwise a result resolved in one context is served in
 * another.
 * </p>
 *
 * <p>
 * The configuration supplier is read on every invocation, so an application may
 * reconfigure TTLs and pool limits in place without replacing the cache.
 * Instances own a lazily created executor and <em>must</em> be {@link #close()
 * closed} when no longer needed; {@link #close()} is idempotent and further
 * invocations fail with {@link IllegalStateException}.
 * </p>
 *
 * @param <K> cache key type
 * @param <V> cached value type
 */
public class IuRefreshableCache<K, V> implements UnsafeFunction<K, V>, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuRefreshableCache.class.getName());

	private static final ThreadGroup THREAD_GROUP;
	private static final ThreadFactory THREAD_FACTORY;

	static {
		THREAD_GROUP = new ThreadGroup("iu-refreshable-cache");
		THREAD_FACTORY = new ThreadFactory() {
			private int c;

			@Override
			public Thread newThread(Runnable r) {
				// daemon: a handler that was never closed must not block JVM exit
				final var thread = new Thread(THREAD_GROUP, r, "iu-refreshable-cache/" + ++c);
				thread.setDaemon(true);
				return thread;
			}
		};
	}

	/**
	 * Cached result class, the cache's value type.
	 *
	 * <p>
	 * Holds the last good result for one cache key, the {@link Instant} that result
	 * was published, the invalidation sequence it was published at, and the refresh
	 * that is currently in flight, if any. All mutable state is guarded by the
	 * instance monitor.
	 * </p>
	 *
	 * <p>
	 * The publication time is stored rather than a derived expiration, so that
	 * staleness is evaluated against the refresh TTL in effect when an entry is
	 * read. A reconfigured refresh TTL therefore applies to entries already cached,
	 * in both directions, without discarding them.
	 * </p>
	 *
	 * <p>
	 * The sequence is what deferred invalidation compares against, rather than a
	 * timestamp. It is drawn from the same monotonic counter that stamps
	 * invalidation events, so every value and every invalidation fall into one
	 * total order and no comparison depends on the resolution of the system clock.
	 * Like the publication time, it is a durable property of the published value:
	 * unlike the in-flight marker, it is not cleared when the refresh that set it
	 * completes.
	 * </p>
	 */
	protected class CachedResult {
		private Optional<V> value;
		private Instant refreshedAt;
		private long readSeq = eventSeq.get();
		private Future<V> refresh;
		private Instant refreshStartedAt;
		private volatile long generation;

		private CachedResult() {
		}

		/**
		 * Performs a refresh, publishing the result as the last good value on success.
		 *
		 * @param cache        cache the entry belongs to
		 * @param cacheHint    hint captured for {@code key} by the invocation that
		 *                     dispatched this refresh; a non-null
		 *                     {@link IuRefreshableCacheHint#restore() restored} value
		 *                     is published in place of invoking {@code call}
		 * @param key          cache key
		 * @param call         remote call
		 * @param generation refresh generation; a refresh that has been superseded by
		 *                   a later one neither publishes its result nor clears the
		 *                   refresh that replaced it
		 * @return remote call result
		 * @throws Exception if the remote call fails
		 */
		private V refresh(IuCacheMap<K, CachedResult> cache, IuRefreshableCacheHint<K, V> cacheHint, K key,
				Callable<V> call, long generation) throws Exception {

			// read before the backing source is, so every invalidation raised while
			// this call is in flight is newer than the result it produces. Held in a
			// local until that result is published: a refresh that fails, or that has
			// been superseded, must not move the entry it never wrote to.
			final var readAtSeq = eventSeq.get();

			try {
				final var restored = IuObject.convert(cacheHint, IuRefreshableCacheHint::restore);
				final var result = restored == null ? Optional.ofNullable(call.call()) : restored;

				final boolean current;
				synchronized (this) {
					current = generation == this.generation;
					if (current) {
						value = result;
						refreshedAt = Instant.now();
						readSeq = readAtSeq;
					}
				}

				// restart the outage tolerance window from this successful refresh, so a
				// healthy entry is never discarded while it is still being refreshed
				if (current)
					cache.put(key, this);

				return result.orElse(null);
			} catch (Exception e) {
				final boolean served;
				synchronized (this) {
					served = value != null;
				}
				if (served)
					LOG.log(Level.INFO, e, () -> "Remote call refresh failed, serving last good result for " + key);
				throw e;
			} finally {
				synchronized (this) {
					if (generation == this.generation) {
						refresh = null;
						refreshStartedAt = null;
					}
				}
			}
		}

		/**
		 * Accepts a value read at a known position in the invalidation sequence.
		 *
		 * <p>
		 * The embedded value is exactly as fresh as the result carrying it, so it is
		 * published with that result's own publication time and sequence position
		 * rather than with the current instant. A refresh already in flight for this
		 * key read the backing source no later than the result being published, so it
		 * is superseded and abandoned.
		 * </p>
		 *
		 * <p>
		 * Declined when this entry already holds a value read more recently than the
		 * one offered, so a slow call cannot publish stale values over fresher ones.
		 * An entry holding no value has nothing worth keeping and accepts whatever it
		 * is offered: it is stamped with the current sequence when it is created, so
		 * declining on sequence alone would reject every value read before the moment
		 * the entry happened to be allocated. The comparison is made under this
		 * entry's own monitor, so it cannot race the update it guards.
		 * </p>
		 *
		 * @param refreshedAt publication time of the result carrying this value
		 * @param readSeq     sequence position at which that result read the backing
		 *                    source
		 * @param v           value to publish
		 * @return true if the value was published; false if it was declined as stale
		 */
		synchronized boolean accept(Instant refreshedAt, long readSeq, V v) {
			if (value != null //
					&& readSeq < this.readSeq)
				return false;

			this.refreshedAt = refreshedAt;
			this.readSeq = readSeq;
			generation++;

			if (refresh != null) {
				refresh.cancel(false);
				refresh = null;
			}
			refreshStartedAt = null;

			value = Optional.ofNullable(v);
			return true;
		}
	}

	/**
	 * Captures the calling thread's context for forwarding to the asynchronous
	 * call.
	 *
	 * <p>
	 * Since every remote call runs on a pooled thread, thread-bound context does
	 * not reach it unless it is forwarded. This method drives that in three phases.
	 * </p>
	 *
	 * <ol>
	 * <li><strong>Capture</strong>: this method is invoked on the <em>calling</em>
	 * thread, and reads whatever context is to be forwarded.</li>
	 * <li><strong>Apply</strong>: the returned {@link Supplier} is invoked on the
	 * <em>pooled</em> thread immediately before the call, and returns a
	 * {@link Runnable} that undoes it.</li>
	 * <li><strong>Restore</strong>: that {@link Runnable} is invoked on the
	 * <em>pooled</em> thread after the call completes, whether or not it succeeded.
	 * The pool is shared, so a call <em>must</em> leave the thread as it found
	 * it.</li>
	 * </ol>
	 *
	 * <p>
	 * Default behavior is to forward the context {@link ClassLoader} and nothing
	 * else. In particular, no other {@link ThreadLocal}-bound state reaches the
	 * call. Subclasses <em>may</em> override to forward additional context, e.g. a
	 * security principal or a diagnostic context; such an override <em>should</em>
	 * delegate to {@code super.captureContext()} so the context {@link ClassLoader}
	 * continues to be forwarded.
	 * </p>
	 *
	 * <p>
	 * Because a stale entry is refreshed in the background, a refresh runs under
	 * the context captured from whichever caller happened to trigger it, and its
	 * result is then served to every caller. The cache is internal and shared by
	 * all contexts, so when forwarded context can change the result, override to
	 * fold the distinguishing context into the cache key; otherwise a result from
	 * one context is served to another.
	 * </p>
	 *
	 * <p>
	 * <strong>Implementation Note:</strong> capture runs on the calling thread
	 * while a lock on the cache entry is held, so it <em>should</em> return quickly
	 * and <em>must not</em> invoke a remote method. An apply phase that fails
	 * <em>should</em> leave the thread unmodified, since its restore
	 * {@link Runnable} is never invoked. A failed restore is propagated after a
	 * successful call; when the call also fails, the restore failure is suppressed
	 * on that call failure.
	 * </p>
	 *
	 * @return {@link Supplier} that applies the captured context to the pooled
	 *         thread and returns a {@link Runnable} that restores it
	 */
	protected Supplier<Runnable> captureContext() {
		final var context = Thread.currentThread().getContextClassLoader();
		return () -> {
			final var current = Thread.currentThread();
			final var restore = current.getContextClassLoader();

			current.setContextClassLoader(context);

			return () -> current.setContextClassLoader(restore);
		};
	}

	private <T> Future<T> call(ExecutorService exec, Callable<T> call) {
		final var context = captureContext();
		return exec.submit(() -> {
			final var restore = context.get();

			Exception throwing = null;
			try {
				return call.call();
			} catch (Exception e) {
				throw throwing = e;
			} finally {
				final var error = IuException.suppress(throwing, restore::run);
				if (error != null)
					throw IuException.checked(error);
			}
		});
	}

	private final Supplier<IuRefreshableCacheConfiguration> config;
	private final UnsafeFunction<K, V> refreshFunction;
	private final Function<K, IuRefreshableCacheHint<K, V>> cacheHintFunction;

	private final IuCacheMap<K, CachedResult> cache = new IuCacheMap<>(IuRefreshableCacheConfiguration.CACHE_TTL);

	private static class Exec {
		final int threads;
		final int pending;
		final ExecutorService pool;

		Exec(int threads, int pending) {
			this.threads = threads;
			if (threads < 1)
				throw new IllegalArgumentException("Call threads must be positive");

			this.pending = pending;
			if (pending < 1)
				throw new IllegalArgumentException("Call pending queue size must be positive");

			final var pool = new ThreadPoolExecutor(threads, threads, 15L, TimeUnit.SECONDS,
					new ArrayBlockingQueue<>(pending, false), THREAD_FACTORY);
			pool.allowCoreThreadTimeOut(true);
			this.pool = pool;
		}
	}

	private volatile Exec exec;
	private volatile boolean closed;

	private final AtomicLong eventSeq = new AtomicLong();

	private class HintEvent {
		private final long seq = eventSeq.incrementAndGet();
		private final Instant time = Instant.now();
		private final IuRefreshableCacheHint<K, V> hint;

		private HintEvent(IuRefreshableCacheHint<K, V> hint) {
			this.hint = hint;
		}
	}

	private final Queue<HintEvent> hintQueue = new ConcurrentLinkedQueue<>();

	/**
	 * Creates a refresh-ahead cache with a dynamically supplied configuration and a
	 * policy for keys eligible for caching.
	 *
	 * <p>
	 * Both functions are consulted once per invocation, so returning current values
	 * allows the cache to be reconfigured in place. See {@link IuRefreshableCache}
	 * for how each configured value takes effect, and for a description of the
	 * defensive call cache, which is enabled when
	 * {@link IuRefreshableCacheConfiguration#getRefreshTtl()} is non-null.
	 * </p>
	 *
	 * <p>
	 * Configured values are validated when observed rather than here, so an invalid
	 * configuration fails the invocation that reads it, not construction.
	 * </p>
	 *
	 * @param config            supplies the configuration in effect;
	 *                          <em>should</em> return quickly, as it is called on
	 *                          every invocation
	 * @param refreshFunction   function that resolves cached values on a cache miss
	 *                          and on each background refresh; invoked on a pooled
	 *                          thread
	 * @param cacheHintFunction function that directs how a key interacts with the
	 *                          cache; <em>should</em> return quickly, as it is
	 *                          called on every invocation before the cache is read.
	 *                          A null function, or one that returns null for a key,
	 *                          bypasses the cache for that key
	 */
	public IuRefreshableCache(Supplier<IuRefreshableCacheConfiguration> config, UnsafeFunction<K, V> refreshFunction,
			Function<K, IuRefreshableCacheHint<K, V>> cacheHintFunction) {
		this.config = Objects.requireNonNull(config, "Missing configuration supplier");
		this.refreshFunction = refreshFunction;
		this.cacheHintFunction = Objects.requireNonNullElse(cacheHintFunction, k -> null);
	}

	/**
	 * Reads and validates the refresh TTL from a configuration snapshot.
	 *
	 * @param config configuration snapshot
	 * @return refresh interval; null if caching is disabled
	 */
	private static Duration refreshTtl(IuRefreshableCacheConfiguration config) {
		final var refreshTtl = config.getRefreshTtl();

		if (refreshTtl != null && (refreshTtl.isNegative() || refreshTtl.isZero()))
			throw new IllegalArgumentException("Refresh TTL must be positive");

		return refreshTtl;
	}

	/**
	 * Records the outcome and elapsed time of one invocation.
	 *
	 * <p>
	 * The message is composed only when {@link Level#FINE} is enabled, so a cache
	 * hit does not pay to build one that is discarded.
	 * </p>
	 *
	 * @param outcome how the invocation resolved
	 * @param key     cache key
	 * @param start   instant the invocation began
	 */
	private static void fine(String outcome, Object key, Instant start) {
		LOG.log(Level.FINE, () -> outcome + ":" + key + ":" + Duration.between(start, Instant.now()));
	}

	/**
	 * Reads and validates the call TTL from a configuration snapshot.
	 *
	 * @param config configuration snapshot
	 * @return call timeout
	 */
	private static Duration callTtl(IuRefreshableCacheConfiguration config) {
		final var callTtl = Objects.requireNonNull(config.getCallTtl(), "Missing call TTL");
		if (callTtl.isNegative() || callTtl.isZero())
			throw new IllegalArgumentException("Call TTL must be positive");
		return callTtl;
	}

	/**
	 * Reads and validates the cache TTL from a configuration snapshot.
	 *
	 * @param config     configuration snapshot
	 * @param refreshTtl validated refresh TTL, non-null
	 * @return cache time to live
	 */
	private static Duration cacheTtl(IuRefreshableCacheConfiguration config, Duration refreshTtl) {
		final var cacheTtl = Objects.requireNonNull(config.getCacheTtl(), "Missing cache TTL");
		if (cacheTtl.compareTo(refreshTtl) <= 0)
			throw new IllegalArgumentException("Cache TTL must be longer than refresh TTL");
		return cacheTtl;
	}

	/**
	 * Gets the cache, applying the cache TTL read for this invocation.
	 *
	 * @param cacheTtl validated cache TTL
	 * @return cache
	 */
	private IuCacheMap<K, CachedResult> cache(Duration cacheTtl) {

		// entries already cached keep their original expiration; a reconfigured TTL
		// takes effect as each entry is refreshed, so nothing is discarded
		cache.setCacheTimeToLive(cacheTtl);

		return cache;
	}

	/**
	 * Gets the thread pool, replacing it if its size no longer matches the
	 * configuration.
	 *
	 * <p>
	 * A replaced pool is shut down gracefully, so calls already in flight run to
	 * completion on it.
	 * </p>
	 *
	 * @param config configuration snapshot
	 * @return thread pool
	 */
	private ExecutorService exec(IuRefreshableCacheConfiguration config) {
		var threads = config.getThreads();
		var pending = config.getPending();

		final Exec current;
		final Exec replaced;
		if (exec == null || exec.threads != threads || exec.pending != pending)
			synchronized (this) {
				final var exec = this.exec;
				if (exec == null || exec.threads != threads || exec.pending != pending) {

					// constructed before the field is reassigned, so an invalid size leaves
					// the pool in use unchanged
					current = new Exec(threads, pending);
					replaced = exec;
					this.exec = current;
				} else {
					current = exec;
					replaced = null;
				}
			}
		else {
			current = exec;
			replaced = null;
		}

		if (replaced != null)
			replaced.pool.shutdown();

		return current.pool;
	}

	/**
	 * Resolves a potentially cached value.
	 *
	 * <p>
	 * See {@link IuRefreshableCache} for the full lookup lifecycle: which
	 * invocations bypass the cache, when a value is served without a call, when a
	 * refresh runs in the background, and when a caller waits.
	 * </p>
	 *
	 * @param key cache key
	 * @return value associated with the key; null if the resolved value is null
	 * @throws IllegalStateException    if this cache has been closed
	 * @throws IllegalArgumentException if the configuration snapshot read for this
	 *                                  invocation is invalid
	 * @throws TimeoutException         if a caller waiting on an uncached value is
	 *                                  not served within the call TTL
	 * @throws Throwable                as thrown by the refresh function, when no
	 *                                  last good value is available to serve in its
	 *                                  place
	 */
	@Override
	public V apply(K key) throws Throwable {
		if (closed)
			throw new IllegalStateException("Refreshable cache is closed");

		// one snapshot per invocation, so a configuration change taking effect
		// mid-invocation cannot be observed inconsistently
		final var cacheHint = cacheHintFunction.apply(key);
		final var config = this.config.get();
		final var refreshTtl = refreshTtl(config);
		final var callTtl = callTtl(config);
		final var exec = exec(config);

		final Duration cacheTtl;
		final IuCacheMap<K, CachedResult> cache;
		if (refreshTtl != null) {
			cacheTtl = cacheTtl(config, refreshTtl);
			cache = cache(cacheTtl);
		} else {
			cacheTtl = null;
			cache = null;
		}

		final var noCache = cache == null //
				|| cacheHint == null //
				|| cacheHint.shouldClear(key);

		final var start = Instant.now();
		if (noCache) {
			// not caching, exec call and join in-line
			final var result = await(call(exec, () -> IuException.checked(key, refreshFunction)), callTtl, true);

			if (cache == null)
				fine("no-cache", key, start);
			else

			// successful at this point, invalidate cache entries by hint
			if (cacheHint != null)
				if (cacheHint == IuRefreshableCacheHint.CLEAR_ALL) {
					// drained before the cache, so an event queued concurrently with this
					// clear survives to invalidate entries repopulated after it; every
					// event already queued is made moot by the clear that follows
					hintQueue.clear();
					cache.clear();
					fine("clear-cache", key, start);
				} else {
					hintQueue.offer(new HintEvent(cacheHint));
					fine("cache-hint", key, start);
				}

			else // no cache hint -> skip cache
				fine("skip-cache", key, start);

			return result;
		}

		// a lock-free read on the hit path; only a miss pays for the entry's creation
		var cached = cache.get(key);
		if (cached == null)
			cached = cache.computeIfAbsent(key, a -> new CachedResult());

		// nothing older than this can match an entry that is still cached, so these
		// are dropped from the head rather than found by walking the queue
		final var hintPurgeThreshold = Instant.now().minus(cacheTtl);
		for (var head = hintQueue.peek(); //
				head != null && !head.time.isAfter(hintPurgeThreshold); //
				head = hintQueue.peek())
			hintQueue.poll();

		final Future<V> pendingCall;
		synchronized (cached) {
			final var now = Instant.now();

			boolean clearedByHint = false;

			// every queued invalidation precedes this entry's own position unless the
			// sequence has advanced since it was read, so the common case — no write
			// since the last read of this key — costs one comparison rather than a
			// walk of the queue
			if (cached.readSeq < eventSeq.get())
				for (final var event : hintQueue)
					if (event.seq > cached.readSeq //
							&& event.hint.shouldClear(key)) {
						// short-circuit on first matching clear event
						cached.value = null;
						clearedByHint = true;
						break;
					}

			// still fresh under the refresh TTL currently in effect
			if (cached.value != null && cached.refreshedAt.plus(refreshTtl).isAfter(now)) {
				fine("cache-hit", key, start);
				return cached.value.orElse(null);
			}

			// abandon a refresh that has outlived the call TTL, so a hung call cannot
			// block all later refreshes for this key
			if (cached.refresh != null //
					&& (clearedByHint || cached.refreshStartedAt.plus(callTtl).isBefore(now))) {
				cached.refresh.cancel(true);
				cached.refresh = null;
			}

			if (cached.refresh == null) {
				final var generation = ++cached.generation;
				cached.refreshStartedAt = now;

				final var toRefresh = cached;
				try {
					final Callable<V> call = () -> {
						// read before the backing source is, so an invalidation raised
						// while this call runs is newer than the values it publishes
						final var readAtSeq = eventSeq.get();
						final var result = IuException.checked(key, refreshFunction);

						// inspect and cache embedded values when caching this result
						final var embeddedValues = cacheHint.inspect(result);
						if (embeddedValues != null //
								&& !embeddedValues.isEmpty())
							embeddedValues.forEach((k, v) -> {

								// per key, so a write to something this result says nothing
								// about does not discard every value it carried
								if (invalidatedSince(k, readAtSeq))
									return;

								var embeddedResult = cache.get(k);
								if (embeddedResult == null)
									embeddedResult = cache.computeIfAbsent(k, a -> new CachedResult());

								// stored again only when the value was actually taken, so
								// an entry left as it stands keeps its own expiration
								if (embeddedResult.accept(now, readAtSeq, v))
									cache.put(k, embeddedResult);
							});

						return result;
					};

					cached.refresh = call(exec, () -> toRefresh.refresh(cache, cacheHint, key, call, generation));

				} catch (RuntimeException e) {
					// the refresh could not be dispatched at all, e.g. the pending queue is
					// full; a stale result is still better than no result
					cached.refreshStartedAt = null;
					if (cached.value == null)
						throw e;

					LOG.log(Level.INFO, e,
							() -> "Remote call refresh could not be dispatched, serving last good result for " + key);
					return cached.value.orElse(null);
				}
			}
			pendingCall = cached.refresh;

			// a result has been cached, so refresh is background-only from here on
			if (cached.value != null) {
				fine("cache-hit-refresh", key, start);
				return cached.value.orElse(null);
			}
		}

		// nothing cached yet: all initial callers wait on this one invocation, which
		// is left running on timeout so it can still populate the cache
		final var result = await(pendingCall, callTtl, false);
		fine("cache-miss", key, start);
		return result;
	}

	/**
	 * Waits for a pending remote call, bounded by the call TTL.
	 *
	 * @param pendingCall     pending remote call
	 * @param callTtl         call TTL, from the invocation's configuration snapshot
	 * @param cancelOnTimeout whether to cancel {@code pendingCall} if the call TTL
	 *                        elapses; {@code false} when other callers may be
	 *                        waiting on the same invocation
	 * @return remote call result
	 * @throws TimeoutException if the call TTL elapses first
	 * @throws Throwable        if thrown by the remote call
	 */
	private V await(Future<V> pendingCall, Duration callTtl, boolean cancelOnTimeout) throws Throwable {
		try {
			return pendingCall.get(callTtl.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			if (cancelOnTimeout)
				pendingCall.cancel(true);
			throw e;
		} catch (ExecutionException e) {
			final var cause = e.getCause();
			cause.addSuppressed(e);
			throw cause;
		}
	}

	/**
	 * Reads this cache's current position in the invalidation sequence, for
	 * publishing a value that is about to be read from the backing source.
	 *
	 * <p>
	 * Take a mark <em>before</em> reading the value it will be published with, so
	 * that an invalidation raised while that read is in progress is newer than the
	 * value and correctly discards it. Taking it afterward would stamp the value as
	 * though it had observed a change it could not have seen.
	 * </p>
	 *
	 * <p>
	 * The one case that wants the opposite order is a caller republishing a value
	 * it has just written: take the mark <em>after</em> the invalidation describing
	 * that write, so the replacement outranks it rather than being discarded by it.
	 * </p>
	 *
	 * @return opaque sequence position, for {@link #publish(Object, Object, long)}
	 * @see #publish(Object, Object, long)
	 */
	public long mark() {
		return eventSeq.get();
	}

	/**
	 * Determines whether an invalidation raised since a mark applies to one key.
	 *
	 * <p>
	 * The sequence is shared by every key, so comparing positions alone answers
	 * "has anything been invalidated since?" — which any write to any key makes
	 * true. The queue walk is what narrows that to the key actually being
	 * published. The position comparison remains as the fast path, since the common
	 * case is that nothing has been invalidated at all.
	 * </p>
	 *
	 * @param key  key to test
	 * @param mark sequence position the value being published was read at
	 * @return true if an invalidation newer than {@code mark} matches {@code key}
	 */
	private boolean invalidatedSince(K key, long mark) {
		if (mark >= eventSeq.get())
			return false;

		for (final var event : hintQueue)
			if (event.seq > mark //
					&& event.hint.shouldClear(key))
				return true;

		return false;
	}

	/**
	 * Publishes a value obtained outside this cache, as though the cache had
	 * resolved it itself.
	 *
	 * <p>
	 * Intended for a caller that has already paid for a value the cache would
	 * otherwise have to fetch — a read performed for another purpose, or the state
	 * a write just established — so that the next reader is served without a call.
	 * </p>
	 *
	 * <p>
	 * The value is published as though read at {@code mark}, so every invalidation
	 * raised since then still applies to it. It is declined, and this method
	 * returns false, when an invalidation newer than {@code mark} matches
	 * {@code key}, or when the entry already holds a value read more recently. A
	 * declined value is simply not published; nothing else about the entry changes.
	 * </p>
	 *
	 * <p>
	 * Publishing supersedes a refresh already in flight for {@code key}. Under a
	 * refresh TTL short enough for the load the cache is carrying, a value lost
	 * that way is re-read within one refresh interval.
	 * </p>
	 *
	 * @param key   cache key
	 * @param value value to publish; may be null, which publishes a cached null
	 * @param mark  sequence position from {@link #mark()}, read before the value
	 * @return true if the value was published; false if it was declined as stale,
	 *         or if caching is disabled by the configuration in effect
	 * @throws IllegalStateException if this cache has been closed
	 * @see #mark()
	 */
	public boolean publish(K key, V value, long mark) {
		if (closed)
			throw new IllegalStateException("Refreshable cache is closed");

		final var start = Instant.now();
		final var config = this.config.get();
		final var refreshTtl = refreshTtl(config);
		if (refreshTtl == null)
			// caching is disabled, so there is no entry for this value to occupy
			return false;

		final var cache = cache(cacheTtl(config, refreshTtl));

		if (invalidatedSince(key, mark)) {
			fine("publish-stale", key, start);
			return false;
		}

		var published = cache.get(key);
		if (published == null)
			published = cache.computeIfAbsent(key, a -> new CachedResult());

		if (!published.accept(start, mark, value)) {
			fine("publish-superseded", key, start);
			return false;
		}

		// stored again so the entry carries a full cache TTL from this publication,
		// as it would have had the cache resolved the value itself
		cache.put(key, published);
		fine("publish", key, start);
		return true;
	}

	/**
	 * Releases the thread pool backing this cache, and discards all cached results
	 * and all pending invalidations.
	 *
	 * <p>
	 * In-flight calls are interrupted. Once closed, further invocations fail with
	 * {@link IllegalStateException}. This method is idempotent.
	 * </p>
	 */
	@Override
	public void close() {
		closed = true;

		// whatever pool is in use, without creating one that was never needed
		final Exec exec;
		synchronized (this) {
			exec = this.exec;
			this.exec = null;
		}
		if (exec != null)
			exec.pool.shutdownNow();

		hintQueue.clear();
		cache.clear();
	}

}
