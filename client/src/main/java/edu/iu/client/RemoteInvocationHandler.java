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
import java.security.Principal;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

import edu.iu.IuObject;
import edu.iu.IuRefreshableCache;
import edu.iu.IuRefreshableCacheConfiguration;
import edu.iu.IuRefreshableCacheHint;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.UnsafeConsumer;

/**
 * Base class for client-side invocation of a Java interface by HTTP POST.
 *
 * <p>
 * Each remote method is represented by a {@link Key} containing its method and
 * serialized arguments. The handler delegates resolution and any refresh-ahead
 * caching to an {@link IuRefreshableCache}; its configuration supplier is read
 * for each invocation. The supplied {@link IuRefreshableCacheConfiguration}
 * controls the call timeout, executor, and caching behavior. A handler owns its
 * cache and must be {@link #close() closed} when no longer needed.
 * </p>
 *
 * <p>
 * Subclasses customize the request by supplying a {@link #uri(Method)}, adding
 * authorization in {@link #authorize(HttpRequest.Builder)}, and optionally
 * overriding serialization, key creation, payload generation, cache policy, or
 * {@link #captureContext() context forwarding}. When caller-specific state can
 * affect a response, override {@link #cacheKey(Method, Object)} to include that
 * state in the {@link Key}; a cache entry is otherwise shared by equivalent
 * method calls.
 * </p>
 *
 * <p>
 * A remote error response is adapted to a {@link RemoteInvocationException}.
 * Failures before a response is received remain {@link HttpException}s. Remote
 * interface methods should declare relevant checked exceptions; otherwise the
 * proxy wraps them in an
 * {@link java.lang.reflect.UndeclaredThrowableException}.
 * </p>
 */
public abstract class RemoteInvocationHandler implements InvocationHandler, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(RemoteInvocationHandler.class.getName());

	/**
	 * Immutable description of one remote request and its cache identity.
	 *
	 * <p>
	 * The enclosing handler is part of equality, so entries cannot be shared by
	 * different handlers even when their request fields are equal.
	 * </p>
	 */
	protected class Key {
		/** Principal that distinguishes the request context; may be null. */
		public final Principal principal;

		/** Remote interface method to invoke. */
		public final Method method;

		/** Immutable serialized arguments supplied to the remote method. */
		public final Object serializedArgs;

		/**
		 * Creates a request key.
		 *
		 * @param principal      request principal; may be null
		 * @param method         remote interface method
		 * @param serializedArgs immutable serialized method arguments
		 */
		public Key(Principal principal, Method method, Object serializedArgs) {
			this.principal = principal;
			this.method = method;
			this.serializedArgs = serializedArgs;
		}

		private Object refresh() throws Exception {
			return doInvoke(method, serializedArgs);
		}

		@Override
		public int hashCode() {
			return IuObject.hashCode(outer(), principal, method, serializedArgs);
		}

		@Override
		public boolean equals(Object obj) {
			if (!IuObject.typeCheck(this, obj))
				return false;
			Key other = (Key) obj;
			return IuObject.equals(outer(), other.outer()) //
					&& Objects.equals(principal, other.principal) //
					&& Objects.equals(method, other.method) //
					&& Objects.equals(serializedArgs, other.serializedArgs);
		}

		private RemoteInvocationHandler outer() {
			return RemoteInvocationHandler.this;
		}
	}

	private final IuRefreshableCache<Key, Object> callCache;

	/**
	 * Creates a handler with caching disabled and the remaining cache settings at
	 * their defaults.
	 *
	 * <p>
	 * Caching is disabled by {@link IuRefreshableCacheConfiguration#NO_CACHE}.
	 * </p>
	 */
	protected RemoteInvocationHandler() {
		this(() -> IuRefreshableCacheConfiguration.NO_CACHE);
	}

	/**
	 * Creates a remote invocation handler.
	 *
	 * <p>
	 * The supplier is consulted once per invocation, so returning current values
	 * allows the handler to be reconfigured in place. See
	 * {@link RemoteInvocationHandler} for how each value takes effect, and for a
	 * description of the defensive call cache, which is enabled when
	 * {@link IuRefreshableCacheConfiguration#getRefreshTtl()} is non-null.
	 * </p>
	 *
	 * <p>
	 * Configured values are validated when observed rather than here, so an invalid
	 * configuration fails the invocation that reads it, not construction.
	 * </p>
	 *
	 * @param config supplies the cache configuration in effect; <em>should</em>
	 *               return quickly, as it is called on every invocation
	 */
	protected RemoteInvocationHandler(Supplier<IuRefreshableCacheConfiguration> config) {
		callCache = new IuRefreshableCache<>(config, Key::refresh, this::cacheHint) {
			@Override
			protected Supplier<Runnable> captureContext() {
				return RemoteInvocationHandler.this.captureContext();
			}
		};
	}

	/**
	 * Determines how a request uses the cache, and what a successful request
	 * invalidates.
	 *
	 * <p>
	 * The default caches every request and invalidates nothing. Whether caching is
	 * enabled at all is determined by the supplied
	 * {@link IuRefreshableCacheConfiguration}; a null refresh TTL bypasses this
	 * policy. Subclasses may override this method to exclude requests, typically
	 * write calls, based on their {@link Key}.
	 * </p>
	 *
	 * <p>
	 * The returned hint answers two separate questions, and it is the hint's
	 * {@link IuRefreshableCacheHint#shouldClear(Object) shouldClear} — evaluated
	 * against the key that produced it — that decides whether this request is
	 * itself cached.
	 * </p>
	 *
	 * <dl>
	 * <dt>{@link IuRefreshableCacheHint#useDefaults()}</dt>
	 * <dd>The request is cached and invalidates nothing. This is the default, and
	 * is what a read wants.</dd>
	 * <dt>{@link IuRefreshableCacheHint#clearAll()}</dt>
	 * <dd>The request is not cached, and on success discards every cached result.
	 * This is what a write wants unless it can describe its effect more
	 * precisely.</dd>
	 * <dt>a hint whose {@code shouldClear} matches its own key</dt>
	 * <dd>The request is not cached, and on success invalidates exactly the keys
	 * its predicate matches. Cheaper for readers than clearing everything when a
	 * write affects a known, narrow set of keys.</dd>
	 * <dt>null</dt>
	 * <dd>The request bypasses the cache and invalidates <em>nothing</em>. Use this
	 * only for a request that neither benefits from caching nor changes anything a
	 * cached result depends on.</dd>
	 * </dl>
	 *
	 * <p>
	 * <strong>Implementation Note:</strong> this method is called on every
	 * invocation, before the cache is read, so it <em>should</em> be inexpensive.
	 * The predicate of a returned hint is evaluated later, under a cache entry's
	 * own monitor, so it <em>must</em> return quickly and <em>must not</em> invoke
	 * a remote method.
	 * </p>
	 *
	 * @param key remote request key
	 * @return hint directing how this request interacts with the cache; null to
	 *         bypass the cache without invalidating anything
	 */
	protected IuRefreshableCacheHint<Key, Object> cacheHint(Key key) {
		return IuRefreshableCacheHint.useDefaults();
	}

	/**
	 * Captures the calling thread's context for application around the asynchronous
	 * remote call.
	 *
	 * <p>
	 * The returned supplier is invoked on the cache's pooled thread immediately
	 * before the call and returns a runnable that restores that thread afterwards.
	 * The default forwards only the context {@link ClassLoader}. Subclasses may
	 * override to forward additional thread-bound state; an override should
	 * delegate to {@code super.captureContext()} so the context class loader is
	 * retained.
	 * </p>
	 *
	 * @return a supplier that applies the captured context and returns its restore
	 *         action
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
	 * Converts arguments to serialized form, for use as part of the cache key and
	 * for passing to
	 * {@link #payload(java.net.http.HttpRequest.Builder, Method, Object)}.
	 *
	 * <p>
	 * Default behavior is to convert arguments to a JSON array, using
	 * {@link #adapt(Type)} for conversion. Arguments are serialized once per
	 * invocation on the calling thread.
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
	 * @param args   remote method arguments; null or empty for a no-argument method
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
	protected Key cacheKey(Method method, Object serializedArgs) {
		return new Key(null, method, serializedArgs);
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

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (method.getDeclaringClass() == Object.class)
			return invokeObjectMethod(proxy, method, args);
		else
			return callCache.apply(cacheKey(method, serialize(method, args)));
	}

	/**
	 * Performs one remote method invocation.
	 *
	 * <p>
	 * The default implementation sends the serialized arguments as an HTTP POST,
	 * adapting a successful response to the method's return type and adapting a
	 * remote failure response to {@link RemoteInvocationException}. Subclasses may
	 * override to use a different transport while retaining this handler's keying,
	 * cache policy, and asynchronous execution.
	 * </p>
	 *
	 * @param method         remote interface method
	 * @param serializedArgs arguments produced by
	 *                       {@link #serialize(Method, Object...)}
	 * @return remote method result, or null for a void method
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
		callCache.close();
	}

}
