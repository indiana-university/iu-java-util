/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 */
package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuRefreshableCacheConfiguration;
import edu.iu.IuRefreshableCacheHint;
import jakarta.json.stream.JsonParsingException;

/** Tests behavior that remains in {@link RemoteInvocationHandler}. */
@SuppressWarnings("javadoc")
public class RemoteInvocationHandlerTest extends IuHttpTestCase {

	interface A {
		void b();

		String echo(String message);
	}

	interface IoAware {
		void b() throws IOException;
	}

	private static Method method(Class<?> type, String name, Class<?>... parameters) {
		return assertDoesNotThrow(() -> type.getMethod(name, parameters));
	}

	private static class TestHandler extends RemoteInvocationHandler {
		private final URI base;
		private final Consumer<HttpRequest.Builder> authorization;

		TestHandler() {
			this(TEST_URI, builder -> {
			});
		}

		TestHandler(URI base, Consumer<HttpRequest.Builder> authorization) {
			super(() -> IuRefreshableCacheConfiguration.NO_CACHE);
			this.base = base;
			this.authorization = authorization;
		}

		@Override
		protected void authorize(HttpRequest.Builder requestBuilder) {
			authorization.accept(requestBuilder);
		}

		@Override
		protected URI uri(Method method) {
			return URI.create(base + "/" + method.getName());
		}
	}

	/**
	 * Invokes the private request-resolution seam directly so Mockito's
	 * thread-confined static mock is used on the test thread. Refresh-ahead
	 * execution is covered by {@code IuRefreshableCacheTest}.
	 */
	private static Object refresh(RemoteInvocationHandler handler, Method method, Object... args) throws Throwable {
		final var key = handler.cacheKey(method, handler.serialize(method, args));
		final var refresh = RemoteInvocationHandler.Key.class.getDeclaredMethod("refresh");
		refresh.setAccessible(true);
		try {
			return refresh.invoke(key);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private MockedStatic<IuHttp> mockIuHttp;

	@BeforeEach
	void setup() throws Exception {
		LogManager.getLogManager().getLogger(Class.forName(RemoteInvocationHandler.class.getName()).getName())
				.setLevel(Level.FINER);
		mockIuHttp = mockStatic(IuHttp.class);
	}

	@AfterEach
	void tearDown() {
		mockIuHttp.close();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testVoidRequestBuildsAndAuthorizesPost() throws Throwable {
		final var base = URI.create(TEST_URI + "/" + IdGenerator.generateId());
		final var authorization = mock(Consumer.class);
		final var handler = new TestHandler(base, authorization);
		try (final var bodyPublishers = mockStatic(BodyPublishers.class)) {
			final var publisher = mock(BodyPublisher.class);
			bodyPublishers.when(() -> BodyPublishers.ofString("[]")).thenReturn(publisher);

			assertDoesNotThrow(() -> refresh(handler, method(A.class, "b")));
			mockIuHttp.verify(() -> IuHttp.send(eq(URI.create(base + "/b")), argThat(request -> {
				final var builder = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> request.accept(builder));
				verify(authorization).accept(builder);
				verify(builder).header("Content-Type", "application/json");
				verify(builder).POST(publisher);
				return true;
			}), eq(IuHttp.NO_CONTENT)));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testValueRequestSerializesArgumentsAndAdaptsResponse() throws Throwable {
		final var base = URI.create(TEST_URI + "/" + IdGenerator.generateId());
		final var authorization = mock(Consumer.class);
		final var handler = new TestHandler(base, authorization);
		final var value = IdGenerator.generateId();
		final var expectedRequest = IuJson.array().add(value).build().toString();
		try (final var bodyPublishers = mockStatic(BodyPublishers.class)) {
			final var publisher = mock(BodyPublisher.class);
			bodyPublishers.when(() -> BodyPublishers.ofString(expectedRequest)).thenReturn(publisher);
			mockIuHttp.when(() -> IuHttp.send(eq(URI.create(base + "/echo")), argThat(request -> {
				final var builder = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> request.accept(builder));
				verify(authorization).accept(builder);
				verify(builder).POST(publisher);
				return true;
			}), eq(IuHttp.READ_JSON))).thenReturn(IuJson.string(value));

			assertEquals(value, refresh(handler, method(A.class, "echo", String.class), value));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testRemoteFailureIsAdapted() throws Throwable {
		final var handler = new TestHandler();
		final var response = mock(HttpResponse.class);
		final var message = IdGenerator.generateId();
		final var remote = new ThrowableRemoteInvocationFailure(IdGenerator.generateId(), IdGenerator.generateId(),
				new Exception(message));
		when(response.body()).thenReturn(new ByteArrayInputStream(
				handler.adapt(RemoteInvocationFailure.class).toJson(remote).toString().getBytes()));
		final var failure = new HttpException(response, IdGenerator.generateId());
		mockIuHttp.when(() -> IuHttp.send(eq(URI.create(TEST_URI + "/b")), argThat(request -> true),
				eq(IuHttp.NO_CONTENT))).thenThrow(failure);

		final var error = assertThrows(RemoteInvocationException.class, () -> refresh(handler, method(A.class, "b")));
		assertEquals(message, error.getMessage());
		assertEquals(Exception.class.getName(), error.getExceptionType());
		assertSame(failure, error.getSuppressed()[0]);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCorruptRemoteFailureBodyIncludesOriginalFailure() throws Throwable {
		final var handler = new TestHandler();
		final var response = mock(HttpResponse.class);
		final var body = "<!doctype html>\n" + IdGenerator.generateId();
		when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes()));
		final var failure = new HttpException(response, IdGenerator.generateId());
		mockIuHttp.when(() -> IuHttp.send(eq(URI.create(TEST_URI + "/b")), argThat(request -> true),
				eq(IuHttp.NO_CONTENT))).thenThrow(failure);

		final var error = assertThrows(IllegalStateException.class, () -> refresh(handler, method(A.class, "b")));
		assertEquals(body, error.getMessage());
		assertSame(failure, error.getCause());
		assertInstanceOf(JsonParsingException.class, error.getSuppressed()[0]);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPreResponseFailurePropagates() throws Throwable {
		final var handler = new TestHandler();
		final var failure = new HttpException(IdGenerator.generateId(), new IOException());
		mockIuHttp.when(() -> IuHttp.send(eq(URI.create(TEST_URI + "/b")), argThat(request -> true),
				eq(IuHttp.NO_CONTENT))).thenThrow(failure);
		assertSame(failure, assertThrows(HttpException.class, () -> refresh(handler, method(IoAware.class, "b"))));
	}

	@Test
	public void testKeyIncludesRequestIdentityAndEnclosingHandler() throws Exception {
		final var first = new TestHandler();
		final var second = new TestHandler();
		final var echo = method(A.class, "echo", String.class);
		final var serialized = first.serialize(echo, "value");
		assertEquals(first.cacheKey(echo, serialized), first.cacheKey(echo, serialized));
		assertEquals(first.cacheKey(echo, serialized).hashCode(), first.cacheKey(echo, serialized).hashCode());
		assertNotEquals(first.cacheKey(echo, serialized), second.cacheKey(echo, serialized));
		assertNotEquals(first.cacheKey(echo, serialized),
				first.new Key(null, echo, first.serialize(echo, "different")));

		final Principal firstPrincipal = () -> "first";
		final Principal secondPrincipal = () -> "second";
		assertNotEquals(first.new Key(firstPrincipal, echo, serialized), first.new Key(secondPrincipal, echo, serialized));
		assertNotEquals(first.cacheKey(echo, serialized), first.new Key(null, method(A.class, "b"), serialized));
		assertNotEquals(first.cacheKey(echo, serialized), "not a key");
	}

	@Test
	public void testCachePolicyReceivesKeyAndCanBeOverridden() throws Exception {
		final var handler = new TestHandler();
		final var key = handler.cacheKey(method(A.class, "b"), handler.serialize(method(A.class, "b")));

		// the default hint caches the request: it does not match its own key
		final var defaultHint = handler.cacheHint(key);
		assertNotNull(defaultHint);
		assertFalse(defaultHint.shouldClear(key));

		final var observed = new AtomicReference<RemoteInvocationHandler.Key>();
		final var rejecting = new TestHandler() {
			@Override
			protected IuRefreshableCacheHint<Key, Object> cacheHint(Key request) {
				observed.set(request);
				return null;
			}
		};
		assertNull(rejecting.cacheHint(key));
		assertSame(key, observed.get());
	}

	/**
	 * Handler with caching enabled and a counted, in-process invocation, so the
	 * tests below observe cache behavior rather than transport behavior.
	 */
	private static class CachingHandler extends RemoteInvocationHandler {
		private final AtomicInteger invocations = new AtomicInteger();
		private final Function<Key, IuRefreshableCacheHint<Key, Object>> hint;

		CachingHandler(Function<Key, IuRefreshableCacheHint<Key, Object>> hint) {
			super(() -> IuRefreshableCacheConfiguration.DEFAULT);
			this.hint = hint;
		}

		@Override
		protected void authorize(HttpRequest.Builder requestBuilder) {
		}

		@Override
		protected URI uri(Method method) {
			return TEST_URI;
		}

		@Override
		protected IuRefreshableCacheHint<Key, Object> cacheHint(Key key) {
			return hint.apply(key);
		}

		@Override
		protected Object doInvoke(Method method, Object serializedArgs) {
			return method.getName() + "/" + invocations.incrementAndGet();
		}
	}

	@Test
	public void testDefaultHintCachesRepeatedRequests() throws Throwable {
		final var echo = method(A.class, "echo", String.class);
		try (final var handler = new CachingHandler(key -> IuRefreshableCacheHint.useDefaults())) {
			assertEquals("echo/1", handler.invoke(null, echo, new Object[] { "key" }));
			assertEquals("echo/1", handler.invoke(null, echo, new Object[] { "key" }));
			assertEquals(1, handler.invocations.get());
		}
	}

	@Test
	public void testClearAllHintExcludesTheRequestAndDiscardsCachedResults() throws Throwable {
		final var echo = method(A.class, "echo", String.class);
		final var b = method(A.class, "b");

		// the direct replacement for excluding a request under the previous policy,
		// which cleared the whole cache on success
		try (final var handler = new CachingHandler(key -> key.method.equals(b) //
				? IuRefreshableCacheHint.clearAll()
				: IuRefreshableCacheHint.useDefaults())) {
			assertEquals("echo/1", handler.invoke(null, echo, new Object[] { "key" }));
			assertEquals("echo/1", handler.invoke(null, echo, new Object[] { "key" }));

			// never cached itself, and discards what was cached before it
			assertEquals("b/2", handler.invoke(null, b, null));
			assertEquals("b/3", handler.invoke(null, b, null));
			assertEquals("echo/4", handler.invoke(null, echo, new Object[] { "key" }));
		}
	}

	@Test
	public void testNullHintBypassesTheCacheWithoutDiscardingCachedResults() throws Throwable {
		final var echo = method(A.class, "echo", String.class);
		final var b = method(A.class, "b");

		// null is NOT the equivalent of excluding a request under the previous
		// policy: it bypasses the cache but publishes no invalidation, so a result
		// cached before it survives
		try (final var handler = new CachingHandler(key -> key.method.equals(b) //
				? null
				: IuRefreshableCacheHint.useDefaults())) {
			assertEquals("echo/1", handler.invoke(null, echo, new Object[] { "key" }));

			assertEquals("b/2", handler.invoke(null, b, null));
			assertEquals("b/3", handler.invoke(null, b, null));

			assertEquals("echo/1", handler.invoke(null, echo, new Object[] { "key" }));
		}
	}

	@Test
	public void testSelectiveHintInvalidatesOnlyTheKeysItMatches() throws Throwable {
		final var echo = method(A.class, "echo", String.class);
		final var b = method(A.class, "b");

		try (final var handler = new CachingHandler(key -> {
			if (!key.method.equals(b))
				return IuRefreshableCacheHint.useDefaults();

			return new IuRefreshableCacheHint<RemoteInvocationHandler.Key, Object>() {
				@Override
				public boolean shouldClear(RemoteInvocationHandler.Key candidate) {
					// its own key, so the write is not cached, plus the one read it
					// is declared to affect. Arguments reach the key serialized, as a
					// JSON array, rather than as the values passed to the method.
					return candidate.method.equals(b) //
							|| String.valueOf(candidate.serializedArgs).contains("stale");
				}
			};
		})) {
			assertEquals("echo/1", handler.invoke(null, echo, new Object[] { "stale" }));
			assertEquals("echo/2", handler.invoke(null, echo, new Object[] { "fresh" }));

			assertEquals("b/3", handler.invoke(null, b, null));

			// only the matched key is resolved again
			assertEquals("echo/4", handler.invoke(null, echo, new Object[] { "stale" }));
			assertEquals("echo/2", handler.invoke(null, echo, new Object[] { "fresh" }));
		}
	}

	@Test
	public void testCacheDelegatesContextCaptureToHandlerOverride() throws Exception {
		final var captured = new AtomicReference<Thread>();
		final var handler = new TestHandler() {
			@Override
			protected Supplier<Runnable> captureContext() {
				captured.set(Thread.currentThread());
				throw new IllegalStateException("captured");
			}
		};
		final var echo = method(A.class, "echo", String.class);
		assertEquals("captured", assertThrows(IllegalStateException.class,
				() -> handler.invoke(null, echo, new Object[] { "key" })).getMessage());
		assertSame(Thread.currentThread(), captured.get());
	}

	@Test
	public void testDefaultHandlerForwardsAndRestoresContextAroundInvocation() throws Throwable {
		final var callerContext = new ClassLoader(null) {
		};
		final var originalContext = Thread.currentThread().getContextClassLoader();
		final var callContext = new AtomicReference<ClassLoader>();
		try (final var handler = new RemoteInvocationHandler() {
			@Override
			protected void authorize(HttpRequest.Builder requestBuilder) {
			}

			@Override
			protected URI uri(Method method) {
				return TEST_URI;
			}

			@Override
			protected Object doInvoke(Method method, Object serializedArgs) {
				callContext.set(Thread.currentThread().getContextClassLoader());
				return "result";
			}
		}) {
			Thread.currentThread().setContextClassLoader(callerContext);
			assertEquals("result", handler.invoke(null, method(A.class, "echo", String.class), new Object[] { "key" }));
			assertSame(callerContext, callContext.get());
		} finally {
			Thread.currentThread().setContextClassLoader(originalContext);
		}
	}

	@Test
	public void testProxyObjectMethodsAndUnrecognizedObjectMethod() throws Throwable {
		final var handler = new TestHandler();
		final var proxy = (A) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { A.class }, handler);
		assertEquals(System.identityHashCode(proxy), proxy.hashCode());
		assertEquals(proxy, proxy);
		assertNotEquals(proxy, this);
		assertEquals(handler.toString(), proxy.toString());
		assertEquals(null, handler.invokeObjectMethod(proxy, Object.class.getDeclaredMethod("finalize"), null));
		assertDoesNotThrow(handler::close);
	}
}
