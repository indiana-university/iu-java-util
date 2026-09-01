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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A refresh-ahead cache for values resolved by an {@link UnsafeFunction}.
 *
 * <p>
 * A first request for a key waits for its value. Subsequent requests receive
 * the last successfully resolved value while a stale entry is refreshed in the
 * background. If a refresh fails, that last good value remains available until
 * the configured cache TTL expires. A {@code null} refresh TTL disables caching
 * and every request resolves the value directly.
 * </p>
 *
 * <p>
 * The configuration supplier is read once for each {@link #apply(Object)} call,
 * permitting an application to reconfigure TTLs and call-pool limits without
 * replacing the cache. Instances own a lazily created executor and must be
 * {@link #close() closed} when no longer needed.
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
		THREAD_GROUP = new ThreadGroup("iu-java-client-remote");
		THREAD_FACTORY = new ThreadFactory() {
			private int c;

			@Override
			public Thread newThread(Runnable r) {
				// daemon: a handler that was never closed must not block JVM exit
				final var thread = new Thread(THREAD_GROUP, r, "iu-java-client-remote/" + ++c);
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
	 * was published, and the refresh that is currently in flight, if any. All
	 * mutable state is guarded by the instance monitor.
	 * </p>
	 *
	 * <p>
	 * The publication time is stored rather than a derived expiration, so that
	 * staleness is evaluated against the refresh TTL in effect when an entry is
	 * read. A reconfigured refresh TTL therefore applies to entries already cached,
	 * in both directions, without discarding them.
	 * </p>
	 */
	protected class CachedResult {
		private Optional<V> value;
		private Instant refreshedAt;
		private Future<V> refresh;
		private Instant refreshStartedAt;
		private long generation;

		private CachedResult() {
		}

		/**
		 * Performs a refresh, publishing the result as the last good value on success.
		 *
		 * @param cache      cache the entry belongs to
		 * @param key        cache key
		 * @param call       remote call
		 * @param generation refresh generation; a refresh that has been superseded by a
		 *                   later one neither publishes its result nor clears the
		 *                   refresh that replaced it
		 * @return remote call result
		 * @throws Exception if the remote call fails
		 */
		private V refresh(IuCacheMap<K, CachedResult> cache, K key, Callable<V> call, long generation)
				throws Exception {
			try {
				final var result = call.call();

				final boolean current;
				synchronized (this) {
					current = generation == this.generation;
					if (current) {
						value = Optional.ofNullable(result);
						refreshedAt = Instant.now();
					}
				}

				// restart the outage tolerance window from this successful refresh, so a
				// healthy entry is never discarded while it is still being refreshed
				if (current)
					cache.put(key, this);

				return result;
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
	private final Predicate<K> usesCache;

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

	/**
	 * Creates a refresh-ahead cache with a dynamically supplied configuration and a
	 * policy for keys eligible for caching.
	 *
	 * <p>
	 * The supplier is consulted once per invocation, so returning current values
	 * allows the cache to be reconfigured in place. See {@link IuRefreshableCache}
	 * for how each value takes effect, and for a description of the defensive call
	 * cache, which is enabled when
	 * {@link IuRefreshableCacheConfiguration#getRefreshTtl()} is non-null.
	 * </p>
	 *
	 * <p>
	 * Configured values are validated when observed rather than here, so an invalid
	 * configuration fails the invocation that reads it, not construction.
	 * </p>
	 *
	 * @param config          supplies the configuration in effect; <em>should</em>
	 *                        return quickly, as it is called on every invocation
	 * @param refreshFunction function that resolves cached values on a cache miss
	 * @param usesCache       {@link Predicate} to determine if key should use
	 *                        cache; returns true to use/refresh a cached result,
	 *                        false to clear cache before resolving
	 */
	public IuRefreshableCache(Supplier<IuRefreshableCacheConfiguration> config, UnsafeFunction<K, V> refreshFunction,
			Predicate<K> usesCache) {
		this.config = Objects.requireNonNull(config, "Missing configuration supplier");
		this.refreshFunction = refreshFunction;
		this.usesCache = usesCache;
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
	 * Gets the cache, applying the currently configured cache TTL.
	 *
	 * @param config     configuration snapshot
	 * @param refreshTtl validated refresh TTL, non-null
	 * @return cache
	 */
	private IuCacheMap<K, CachedResult> cache(IuRefreshableCacheConfiguration config, Duration refreshTtl) {
		final var cacheTtl = Objects.requireNonNull(config.getCacheTtl(), "Missing cache TTL");
		if (cacheTtl.compareTo(refreshTtl) <= 0)
			throw new IllegalArgumentException("Cache TTL must be longer than refresh TTL");

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
		final var threads = config.getThreads();
		final var pending = config.getPending();

		final Exec current;
		final Exec replaced;
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

		if (replaced != null)
			replaced.pool.shutdown();

		return current.pool;
	}

	/**
	 * Resolves a potentially cached value.
	 * 
	 * @param key cache key
	 * @return value associated with the key
	 * @throws IllegalStateException if this cache has been closed
	 * @throws Throwable             from {@link #refreshFunction}
	 */
	@Override
	public V apply(K key) throws Throwable {
		if (closed)
			throw new IllegalStateException("Remote invocation handler is closed");

		// one snapshot per invocation, so a configuration change taking effect
		// mid-invocation cannot be observed inconsistently
		final var config = this.config.get();
		final var refreshTtl = refreshTtl(config);
		final var callTtl = callTtl(config);
		final var exec = exec(config);

		final IuCacheMap<K, CachedResult> cache;
		if (refreshTtl != null)
			cache = cache(config, refreshTtl);
		else
			cache = null;

		final Callable<V> call = () -> IuException.checked(key, refreshFunction);

		final var start = Instant.now();
		if (cache == null || !usesCache.test(key)) {
			// sole waiter, so a call this caller has given up on is cancelled
			final var result = await(call(exec, call), callTtl, true);

			// only a successful write invalidates previously cached reads
			if (cache != null) {
				cache.clear();
				LOG.fine("clear-cache:" + key + ":" + Duration.between(start, Instant.now()));
			} else
				LOG.fine("no-cache:" + key + ":" + Duration.between(start, Instant.now()));

			return result;
		}

		// atomic, so concurrent first callers share one entry and one invocation
		final var cached = cache.computeIfAbsent(key, a -> new CachedResult());

		final Future<V> pendingCall;
		synchronized (cached) {
			final var now = Instant.now();

			// still fresh under the refresh TTL currently in effect
			if (cached.value != null && cached.refreshedAt.plus(refreshTtl).isAfter(now)) {
				LOG.fine("cache-hit:" + key + ":" + Duration.between(start, now));
				return cached.value.orElse(null);
			}

			// abandon a refresh that has outlived the call TTL, so a hung call cannot
			// block all later refreshes for this key
			if (cached.refresh != null && cached.refreshStartedAt.plus(callTtl).isBefore(now)) {
				cached.refresh.cancel(true);
				cached.refresh = null;
			}

			if (cached.refresh == null) {
				final var generation = ++cached.generation;
				cached.refreshStartedAt = now;
				try {
					cached.refresh = call(exec, () -> cached.refresh(cache, key, call, generation));
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
				LOG.fine("cache-hit-refresh:" + key + ":" + Duration.between(start, now));
				return cached.value.orElse(null);
			}
		}

		// nothing cached yet: all initial callers wait on this one invocation, which
		// is left running on timeout so it can still populate the cache
		final var result = await(pendingCall, callTtl, false);
		LOG.fine("cache-miss:" + key + ":" + Duration.between(start, Instant.now()));
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
	 * Releases the thread pool backing this cache and discards all cached results.
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

		cache.clear();
	}

}
