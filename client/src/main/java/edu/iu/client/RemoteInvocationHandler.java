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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuCacheMap;
import edu.iu.IuObject;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.UnsafeConsumer;

/**
 * May be extended for synchronous client=side remote invocation of a Java
 * interface via HTTP POST.
 *
 * <p>
 * A remote error response is adapted to a {@link RemoteInvocationException}. A
 * failure that occurs before a response is received has no remote failure
 * payload to adapt, so the underlying {@link HttpException} is propagated
 * as-is. Remote interface methods <em>should</em> therefore declare
 * {@code throws java.io.IOException}; otherwise the proxy wraps the propagated
 * {@link HttpException} in a
 * {@link java.lang.reflect.UndeclaredThrowableException}, per the
 * {@link InvocationHandler#invoke(Object, Method, Object[])} contract.
 * </p>
 */
public abstract class RemoteInvocationHandler implements InvocationHandler {

	private static final Logger LOG = Logger.getLogger(RemoteInvocationHandler.class.getName());

	private static class CachedResult {
		private final Object value;
		private final long refreshAt;

		private CachedResult(Object value, Duration refreshTtl) {
			this.value = value;
			this.refreshAt = System.currentTimeMillis() + refreshTtl.toMillis();
		}

		private boolean needsRefresh() {
			return refreshAt <= System.currentTimeMillis();
		}
	}

	private final Duration refreshTtl;
	private final IuCacheMap<List<?>, CachedResult> cache;

	/**
	 * Default constructor. Caching is disabled.
	 */
	protected RemoteInvocationHandler() {
		refreshTtl = null;
		cache = null;
	}

	/**
	 * Creates a remote invocation handler with a defensive call cache.
	 *
	 * <p>
	 * Entries are refreshed after {@code refreshTtl}. They remain available until
	 * {@code cacheTtl}, allowing the last successful response to be returned when
	 * a refresh fails with a non-interruption {@link Exception}. Errors and
	 * {@link InterruptedException interrupted refreshes} propagate immediately;
	 * an interrupted refresh also restores the thread interrupt status. The interval
	 * between the two durations is the downstream service outage tolerance window.
	 * </p>
	 *
	 * <p>
	 * Methods for which {@link #usesCache(Method)} returns {@code false} are
	 * never cached; a successful invocation of one of these methods instead
	 * clears the entire cache, so that infrequent write calls (e.g. POST)
	 * invalidate previously cached results from frequent read calls (e.g. GET).
	 * </p>
	 *
	 * @param refreshTtl interval after which a cached result is refreshed
	 * @param cacheTtl   maximum time a cached result remains available; must be
	 *                   longer than {@code refreshTtl}
	 */
	protected RemoteInvocationHandler(Duration refreshTtl, Duration cacheTtl) {
		this.refreshTtl = Objects.requireNonNull(refreshTtl, "Missing refresh TTL");
		if (refreshTtl.isNegative() || refreshTtl.isZero())
			throw new IllegalArgumentException("Refresh TTL must be positive");

		cacheTtl = Objects.requireNonNull(cacheTtl, "Missing cache TTL");
		if (cacheTtl.compareTo(refreshTtl) <= 0)
			throw new IllegalArgumentException("Cache TTL must be longer than refresh TTL");

		cache = new IuCacheMap<>(cacheTtl);
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
	 * Adds request payload to a pending remote call request.
	 * 
	 * <p>
	 * Default behavior is to POST arguments as a JSON array, using
	 * {@link #adapt(Type)} for conversion.
	 * </p>
	 * 
	 * @param requestBuilder pending remote call request
	 * @param method         method
	 * @param args           arguments
	 */
	protected void payload(HttpRequest.Builder requestBuilder, Method method, Object[] args) {
		final var parameters = method.getParameters();
		final var requestBody = IuJson.array();
		for (var i = 0; i < parameters.length; i++)
			requestBody.add(adapt(parameters[i].getParameterizedType()).toJson(args[i]));

		final var request = requestBody.build().toString();
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
	 * constructed with a refresh TTL. Subclasses may override this method to
	 * choose a subset of methods. A handler created without a cache bypasses this
	 * policy and always invokes the remote method directly.
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

		if (cache == null || !usesCache(method)) {
			final var value = doInvoke(method, args);
			if (cache != null)
				cache.clear();
			return value;
		}

		final var key = List.of(method.getDeclaringClass(), method,
				Arrays.asList(args == null ? new Object[0] : args.clone()));
		final var cached = cache.get(key);
		if (cached == null) {
			final var value = doInvoke(method, args);
			cache.put(key, new CachedResult(value, refreshTtl));
			return value;
		}

		if (!cached.needsRefresh())
			return cached.value;

		try {
			final var value = doInvoke(method, args);
			cache.put(key, new CachedResult(value, refreshTtl));
			return value;
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				throw e;
			}
			LOG.log(Level.INFO, e, () -> "Remote call refresh failed for " + method);
			return cached.value;
		}
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
	 * @param method remote method
	 * @param args   remote method arguments
	 * @return remote method result
	 * @throws Throwable if the remote invocation fails
	 */
	protected Object doInvoke(Method method, Object[] args) throws Throwable {
		final UnsafeConsumer<HttpRequest.Builder> request = builder -> {
			authorize(builder);
			payload(builder, method, args);
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

			Throwable remoteError;
			String body = null;
			try {
				body = IuText.utf8(IuStream.read(errorResponse.body()));
				remoteError = new RemoteInvocationException(
						(RemoteInvocationFailure) adapt(RemoteInvocationFailure.class).fromJson(IuJson.parse(body)));
				remoteError.addSuppressed(e);
			} catch (Throwable errorHandlingFailure) {
				remoteError = new IllegalStateException(body, e);
				remoteError.addSuppressed(errorHandlingFailure);
			}
			throw remoteError;
		}
	}

}
