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
package edu.iu.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuCacheMap;
import edu.iu.IuObject;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.UnsafeConsumer;

/**
 * May be extended for client-side remote invocation of a Java interface via
 * HTTP POST.
 *
 * <p>
 * Every remote call is performed asynchronously on a bounded thread pool and is
 * limited by the {@link RemoteInvocationConfiguration#getCallTtl() call TTL}.
 * The pool is owned by the handler, so a handler <em>must</em> be
 * {@link #close() closed} when no longer needed.
 * </p>
 *
 * <p>
 * Because a call does not run on the calling thread, thread-bound context
 * reaches it only by being forwarded: see {@link #captureContext()}, which
 * forwards the context {@link ClassLoader} by default and may be overridden to
 * forward more.
 * </p>
 *
 * <p>
 * When configured with a {@link RemoteInvocationConfiguration#getRefreshTtl()
 * refresh TTL}, a defensive call cache reduces call volume as follows.
 * </p>
 *
 * <ul>
 * <li>The first callers for a cache key all wait on a single invocation and
 * receive the same result, rather than each issuing a remote call.</li>
 * <li>Once a result has been cached, callers never block again: a caller that
 * observes a stale entry triggers a background refresh if one is not already in
 * flight, then immediately returns the last good result.</li>
 * <li>A successful refresh restarts the
 * {@link RemoteInvocationConfiguration#getCacheTtl() cache TTL}, so the
 * interval between the two TTLs is the downstream outage tolerance window. Once
 * that window elapses without a successful refresh the entry is discarded and
 * the next caller blocks on a fresh invocation.</li>
 * <li>A successful call to a method excluded by {@link #usesCache(Method)}
 * clears the entire cache, so infrequent write calls (e.g. POST) invalidate
 * results cached by frequent read calls (e.g. GET).</li>
 * </ul>
 *
 * <p>
 * A remote error response is adapted to a {@link RemoteInvocationException}. A
 * failure that occurs before a response is received has no remote failure
 * payload to adapt, so the underlying {@link HttpException} is propagated
 * as-is, as is a {@link TimeoutException} for a call that exceeds the call TTL.
 * Remote interface methods <em>should</em> therefore declare
 * {@code throws java.io.IOException} and, when a cache is not in use,
 * {@code throws java.util.concurrent.TimeoutException}; otherwise the proxy
 * wraps the propagated exception in a
 * {@link java.lang.reflect.UndeclaredThrowableException}, per the
 * {@link InvocationHandler#invoke(Object, Method, Object[])} contract.
 * </p>
 */
public abstract class RemoteInvocationHandler implements InvocationHandler, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(RemoteInvocationHandler.class.getName());

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

	private static final RemoteInvocationConfiguration NO_CACHE = new RemoteInvocationConfiguration() {
		@Override
		public Duration getRefreshTtl() {
			return null;
		}
	};

	/**
	 * Cached result class, value type for {@link #cache()}.
	 *
	 * <p>
	 * Holds the last good result for one cache key, the {@link Instant} it goes
	 * stale, and the refresh that is currently in flight, if any. All mutable state
	 * is guarded by the instance monitor.
	 * </p>
	 */
	protected class CachedResult {
		private Optional<Object> value;
		private Instant refreshAt;
		private Future<?> refresh;
		private Instant refreshStartedAt;
		private long generation;

		private CachedResult() {
		}

		/**
		 * Performs a refresh, publishing the result as the last good value on success.
		 *
		 * @param cache      cache the entry belongs to, captured from the triggering
		 *                   caller rather than read from {@link #cache()}, which is
		 *                   context-sensitive
		 * @param key        cache key
		 * @param call       remote call
		 * @param generation refresh generation; a refresh that has been superseded by a
		 *                   later one neither publishes its result nor clears the
		 *                   refresh that replaced it
		 * @return remote call result
		 * @throws Exception if the remote call fails
		 */
		private Object refresh(IuCacheMap<Object, CachedResult> cache, Object key, Callable<?> call, long generation)
				throws Exception {
			try {
				final var result = call.call();

				final boolean current;
				synchronized (this) {
					current = generation == this.generation;
					if (current) {
						value = Optional.ofNullable(result);
						refreshAt = Instant.now().plus(refreshTtl);
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
	 * result is then served to every caller. When forwarded context can change the
	 * result, override {@link #cache()} to partition the cache by context so
	 * results are not shared across contexts.
	 * </p>
	 *
	 * <p>
	 * <strong>Implementation Note:</strong> capture runs on the calling thread
	 * while a lock on the cache entry is held, so it <em>should</em> return quickly
	 * and <em>must not</em> invoke a remote method. An apply phase that fails
	 * <em>should</em> leave the thread unmodified, since its restore
	 * {@link Runnable} is never invoked. A failed restore is logged and does not
	 * mask the outcome of the call.
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

	private <T> Future<T> call(Callable<T> call) {
		final var context = captureContext();
		return exec.submit(() -> {
			final var restore = context.get();
			try {
				return call.call();
			} finally {
				// a failed restore must not mask the outcome of the call
				try {
					restore.run();
				} catch (Throwable e) {
					LOG.log(Level.WARNING, e, () -> "Failed to restore thread context after remote call");
				}
			}
		});
	}

	private final ExecutorService exec;
	private final Duration refreshTtl;
	private final Duration callTtl;
	private final IuCacheMap<Object, CachedResult> cache;
	private volatile boolean closed;

	/**
	 * Default constructor.
	 *
	 * <p>
	 * Caching is disabled; all other parameters take the
	 * {@link RemoteInvocationConfiguration} defaults.
	 * </p>
	 */
	protected RemoteInvocationHandler() {
		this(NO_CACHE);
	}

	/**
	 * Creates a remote invocation handler.
	 *
	 * <p>
	 * See {@link RemoteInvocationHandler} for a description of the defensive call
	 * cache, which is enabled when
	 * {@link RemoteInvocationConfiguration#getRefreshTtl()} is non-null.
	 * </p>
	 *
	 * @param config configuration
	 * @throws IllegalArgumentException if any configured value is invalid
	 */
	protected RemoteInvocationHandler(RemoteInvocationConfiguration config) {
		refreshTtl = config.getRefreshTtl();
		if (refreshTtl != null) {
			if (refreshTtl.isNegative() || refreshTtl.isZero())
				throw new IllegalArgumentException("Refresh TTL must be positive");

			final var cacheTtl = Objects.requireNonNull(config.getCacheTtl(), "Missing cache TTL");
			if (cacheTtl.compareTo(refreshTtl) <= 0)
				throw new IllegalArgumentException("Cache TTL must be longer than refresh TTL");

			cache = new IuCacheMap<>(cacheTtl);
		} else
			cache = null;

		callTtl = Objects.requireNonNull(config.getCallTtl(), "Missing call TTL");
		if (callTtl.isNegative() || callTtl.isZero())
			throw new IllegalArgumentException("Call TTL must be positive");

		final var threads = config.getThreads();
		if (threads < 1)
			throw new IllegalArgumentException("Call threads must be positive");
		final var pending = config.getPending();
		if (pending < 1)
			throw new IllegalArgumentException("Call pending queue size must be positive");

		final var exec = new ThreadPoolExecutor(threads, threads, 15L, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(pending, false), THREAD_FACTORY);
		exec.allowCoreThreadTimeOut(true);
		this.exec = exec;
	}

	/**
	 * Supplies the remote invocation URI.
	 * 
	 * @param method remote method
	 * @return {@link URI}
	 */
	protected abstract URI uri(Method method);

	/**
	 * Adds authorization headers to a pending remote call request
	 * 
	 * @param requestBuilder pending remote call request
	 */
	protected abstract void authorize(HttpRequest.Builder requestBuilder);

	/**
	 * Gets the cache to use for the current call context.
	 * 
	 * @return context-sensitive cache; null to skip cache behavior
	 */
	protected IuCacheMap<Object, CachedResult> cache() {
		return cache;
	}

	/**
	 * Converts arguments to serialized form, for use as part of the cache key and
	 * for passing to
	 * {@link #payload(java.net.http.HttpRequest.Builder, Method, Object)}.
	 *
	 * <p>
	 * Default behavior is to convert arguments to a JSON array, using
	 * {@link #adapt(Type)} for conversion. Arguments are serialized once per
	 * invocation, on the calling thread, so the conversion sees the caller's
	 * thread context rather than the {@link #captureContext() forwarded} context.
	 * </p>
	 *
	 * <p>
	 * Since the result is used as part of the cache key, it <em>must</em> be
	 * immutable and implement {@link Object#equals(Object)} and
	 * {@link Object#hashCode()} by value. Serializing rather than retaining the
	 * arguments means a mutable argument cannot alter a key after the fact, and
	 * that arguments which are distinct objects but serialize identically share a
	 * cache entry, matching the fact that the remote result depends only on the
	 * serialized request.
	 * </p>
	 *
	 * @param method remote method
	 * @param args   remote method arguments; null or empty for a no-argument
	 *               method
	 * @return serialized arguments
	 */
	protected Object serialize(Method method, Object... args) {
		final var parameters = method.getParameters();
		final var requestBody = IuJson.array();
		for (var i = 0; i < parameters.length; i++)
			requestBody.add(adapt(parameters[i].getParameterizedType()).toJson(args[i]));
		return requestBody.build();
	}

	/**
	 * Gets the cache key for an invocation.
	 *
	 * <p>
	 * Default behavior is to key on the remote method paired with its
	 * {@link #serialize(Method, Object...) serialized arguments}. The method is
	 * part of the key because serialized arguments alone do not identify a call:
	 * every no-argument method serializes to the same empty array, as do distinct
	 * methods that accept equal arguments.
	 * </p>
	 *
	 * @param method         remote method
	 * @param serializedArgs serialized arguments, from
	 *                       {@link #serialize(Method, Object...)}
	 * @return cache key; <em>must</em> be immutable and implement
	 *         {@link Object#equals(Object)} and {@link Object#hashCode()} by value
	 */
	protected Object cacheKey(Method method, Object serializedArgs) {
		return List.of(method, serializedArgs);
	}

	/**
	 * Adds request payload to a pending remote call request.
	 * 
	 * <p>
	 * Default behavior is to POST the serialized arguments as the request body.
	 * Conversion has already happened, on the calling thread, in
	 * {@link #serialize(Method, Object...)}.
	 * </p>
	 *
	 * @param requestBuilder pending remote call request
	 * @param method         method
	 * @param serializedArgs arguments, serialized via
	 *                       {@link #serialize(Method, Object...)}
	 */
	protected void payload(HttpRequest.Builder requestBuilder, Method method, Object serializedArgs) {
		final var request = serializedArgs.toString();
		LOG.finer(() -> method + " " + request);

		requestBuilder.header("Content-Type", "application/json");
		requestBuilder.POST(BodyPublishers.ofString(request));
	}

	/**
	 * Get a {@link IuJsonAdapter} for converting to a generic type.
	 * 
	 * @param <T>  Java type
	 * @param type Java type
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	protected <T> IuJsonAdapter<T> adapt(Type type) {
		if (type instanceof Class) {
			final var c = (Class<?>) type;
			if (!IuObject.isPlatformName(c.getName()) && c.isInterface())
				return (IuJsonAdapter<T>) IuJsonAdapter.from((Class<?>) type,
						IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, a -> adapt(a));
		}

		return IuJsonAdapter.of(type, a -> adapt(a));
	}

	/**
	 * Handles object method invocation without remote call overhead.
	 * 
	 * @param proxy  proxy
	 * @param method method
	 * @param args   args
	 * @return non-null if the method invoked was handled; null if not handled.
	 */
	protected final Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
		switch (method.getName()) {
		case "hashCode":
			return System.identityHashCode(proxy);
		case "equals":
			return proxy == args[0];
		case "toString":
			return toString();
		}

		return null;
	}

	/**
	 * Determines whether calls to a method use the cache.
	 *
	 * <p>
	 * The default enables caching for all remote methods when this handler was
	 * configured with a refresh TTL. Subclasses may override this method to choose
	 * a subset of methods, typically to exclude write calls. A handler configured
	 * without a cache bypasses this policy and always invokes the remote method
	 * directly.
	 * </p>
	 *
	 * <p>
	 * A successful call to an excluded method clears the entire cache.
	 * </p>
	 *
	 * @param method remote method
	 * @return {@code true} if calls to {@code method} use the cache
	 */
	protected boolean usesCache(Method method) {
		return refreshTtl != null;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (method.getDeclaringClass() == Object.class)
			return invokeObjectMethod(proxy, method, args);

		if (closed)
			throw new IllegalStateException("Remote invocation handler is closed");

		// serialized once on the calling thread, then reused as the request payload
		final var serializedArgs = serialize(method, args);

		final Callable<?> call = () -> doInvoke(method, serializedArgs);
		final var cache = cache();

		if (cache == null || !usesCache(method)) {
			// sole waiter, so a call this caller has given up on is cancelled
			final var result = await(call(call), true);

			// only a successful write invalidates previously cached reads
			if (cache != null)
				cache.clear();

			return result;
		}

		final var key = cacheKey(method, serializedArgs);

		// atomic, so concurrent first callers share one entry and one invocation
		final var cached = cache.computeIfAbsent(key, a -> new CachedResult());

		final Future<?> pendingCall;
		synchronized (cached) {
			final var now = Instant.now();

			// still fresh: no remote call at all
			if (cached.value != null && cached.refreshAt.isAfter(now))
				return cached.value.orElse(null);

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
					cached.refresh = call(() -> cached.refresh(cache, key, call, generation));
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
			if (cached.value != null)
				return cached.value.orElse(null);
		}

		// nothing cached yet: all initial callers wait on this one invocation, which
		// is left running on timeout so it can still populate the cache
		return await(pendingCall, false);
	}

	/**
	 * Waits for a pending remote call, bounded by the configured call TTL.
	 *
	 * @param pendingCall     pending remote call
	 * @param cancelOnTimeout whether to cancel {@code pendingCall} if the call TTL
	 *                        elapses; {@code false} when other callers may be
	 *                        waiting on the same invocation
	 * @return remote call result
	 * @throws TimeoutException if the call TTL elapses first
	 * @throws Throwable        if thrown by the remote call
	 */
	private Object await(Future<?> pendingCall, boolean cancelOnTimeout) throws Throwable {
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
	 * Releases the thread pool backing this handler and discards all cached
	 * results.
	 *
	 * <p>
	 * In-flight calls are interrupted. Once closed, further invocations fail with
	 * {@link IllegalStateException}. This method is idempotent.
	 * </p>
	 */
	@Override
	public void close() {
		closed = true;
		exec.shutdownNow();

		final var cache = this.cache;
		if (cache != null)
			cache.clear();
	}

	/**
	 * Performs a remote method invocation.
	 *
	 * <p>
	 * Subclasses may override this method to customize how remote calls are
	 * performed. {@link #invoke(Object, Method, Object[])} handles object methods
	 * and cache behavior before delegating here.
	 * </p>
	 *
	 * @param method         remote method
	 * @param serializedArgs method arguments, already serialized by
	 *                       {@link #serialize(Method, Object...)}
	 * @return remote method result
	 * @throws Exception if the remote invocation fails
	 */
	protected Object doInvoke(Method method, Object serializedArgs) throws Exception {
		final UnsafeConsumer<HttpRequest.Builder> request = builder -> {
			authorize(builder);
			payload(builder, method, serializedArgs);
		};

		try {
			final var type = method.getGenericReturnType();
			if (type == void.class)
				return IuHttp.send(uri(method), request, IuHttp.NO_CONTENT);
			else {
				final var responseJson = IuHttp.send(uri(method), request, IuHttp.READ_JSON);
				LOG.finer(() -> method + " " + responseJson);

				return adapt(type).fromJson(responseJson);
			}
		} catch (HttpException e) {
			final var errorResponse = e.getResponse();
			if (errorResponse == null)
				// the request failed before a response was received; nothing to adapt
				throw e;

			Exception remoteError;
			String body = null;
			try {
				body = IuText.utf8(IuStream.read(errorResponse.body()));
				remoteError = new RemoteInvocationException(
						(RemoteInvocationFailure) adapt(RemoteInvocationFailure.class).fromJson(IuJson.parse(body)));
				remoteError.addSuppressed(e);
			} catch (Exception errorHandlingFailure) {
				remoteError = new IllegalStateException(body, e);
				remoteError.addSuppressed(errorHandlingFailure);
			}
			throw remoteError;
		}
	}

}
