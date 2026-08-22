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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import jakarta.json.stream.JsonParsingException;

@SuppressWarnings("javadoc")
public class RemoteInvocationHandlerTest extends IuHttpTestCase {

	interface A {
		void b();

		String echo(String message);
	}

	interface IoAware {
		void b() throws IOException;
	}

	/**
	 * Resolves a remote method.
	 *
	 * <p>
	 * The HTTP-level tests below drive {@link RemoteInvocationHandler#doInvoke} on
	 * the test thread rather than through the proxy, because a proxied call runs on
	 * the handler's thread pool where Mockito's thread-confined static mocks do not
	 * apply.
	 * </p>
	 */
	private static Method method(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
		return assertDoesNotThrow(() -> declaringClass.getMethod(name, parameterTypes));
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
	public void testSimpleCall() {
		final var uri = URI.create(TEST_URI + "/" + IdGenerator.generateId());
		final var check = mock(Consumer.class);
		final var handler = new RemoteInvocationHandler() {
			@Override
			protected void authorize(Builder requestBuilder) {
				check.accept(requestBuilder);
			}

			@Override
			protected URI uri(Method method) {
				return URI.create(uri + "/" + method.getName());
			}
		};
		final var a = (A) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { A.class },
				handler);
		assertEquals(System.identityHashCode(a), a.hashCode());
		assertEquals(a, a);
		assertNotEquals(a, this);
		assertEquals(handler.toString(), a.toString());
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final var p = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString("[]")).thenReturn(p);

			assertDoesNotThrow(() -> handler.doInvoke(method(A.class, "b"), null));
			mockIuHttp.verify(() -> IuHttp.send(eq(URI.create(uri + "/b")), argThat(c -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> c.accept(rb));
				verify(check).accept(rb);
				verify(rb).POST(p);
				return true;
			}), eq(IuHttp.NO_CONTENT)));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCallWithArgs() {
		final var uri = URI.create(TEST_URI + "/" + IdGenerator.generateId());
		final var check = mock(Consumer.class);
		final var handler = new RemoteInvocationHandler() {
			@Override
			protected void authorize(Builder requestBuilder) {
				check.accept(requestBuilder);
			}

			@Override
			protected URI uri(Method method) {
				return URI.create(uri + "/" + method.getName());
			}
		};
		final var a = (A) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { A.class },
				handler);
		assertEquals(System.identityHashCode(a), a.hashCode());
		assertEquals(a, a);
		assertNotEquals(a, this);
		assertEquals(handler.toString(), a.toString());

		final var message = IdGenerator.generateId();
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final var p = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(IuJson.array().add(message).build().toString()))
					.thenReturn(p);

			mockIuHttp.when(() -> IuHttp.send(eq(URI.create(uri + "/echo")), argThat(c -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> c.accept(rb));
				verify(check).accept(rb);
				verify(rb).POST(p);
				return true;
			}), eq(IuHttp.READ_JSON))).thenReturn(IuJson.string(message));

			assertEquals(message,
					assertDoesNotThrow(() -> handler.doInvoke(method(A.class, "echo", String.class),
							new Object[] { message })));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testErrorWithDetails() {
		final var uri = URI.create(TEST_URI + "/" + IdGenerator.generateId());
		final var check = mock(Consumer.class);
		final var handler = new RemoteInvocationHandler() {
			@Override
			protected void authorize(Builder requestBuilder) {
				check.accept(requestBuilder);
			}

			@Override
			protected URI uri(Method method) {
				return URI.create(uri + "/" + method.getName());
			}
		};
		final var a = (A) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { A.class },
				handler);
		assertEquals(System.identityHashCode(a), a.hashCode());
		assertEquals(a, a);
		assertNotEquals(a, this);
		assertEquals(handler.toString(), a.toString());
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockRemoteInvocationFailure = mockStatic(RemoteInvocationFailure.class)) {
			final var p = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString("[]")).thenReturn(p);

			final var errorMessage = IdGenerator.generateId();
			final var resp = mock(HttpResponse.class);

			final var remoteName = IdGenerator.generateId();
			final var remoteMethod = IdGenerator.generateId();
			final var remoteError = new Exception(errorMessage);
			when(resp.body()).thenReturn(new ByteArrayInputStream(handler.adapt(RemoteInvocationFailure.class)
					.toJson(new ThrowableRemoteInvocationFailure(remoteName, remoteMethod, remoteError)).toString()
					.getBytes()));

			final var ex = new HttpException(resp, IdGenerator.generateId());
			mockIuHttp.when(() -> IuHttp.send(eq(URI.create(uri + "/b")), argThat(c -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> c.accept(rb));
				verify(check).accept(rb);
				verify(rb).POST(p);
				return true;
			}), eq(IuHttp.NO_CONTENT))).thenThrow(ex);

			final var error = assertThrows(RemoteInvocationException.class,
					() -> handler.doInvoke(method(A.class, "b"), null));
			assertEquals(errorMessage, error.getMessage());
			assertEquals(Exception.class.getName(), error.getExceptionType());
			assertEquals(Exception.class.getName(), error.getStackTrace()[0].getClassName());
			assertEquals("<init>", error.getStackTrace()[0].getMethodName());
			assertSame(ex, error.getSuppressed()[0]);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testErrorWithCorruptBody() {
		final var uri = URI.create(TEST_URI + "/" + IdGenerator.generateId());
		final var check = mock(Consumer.class);
		final var handler = new RemoteInvocationHandler() {
			@Override
			protected void authorize(Builder requestBuilder) {
				check.accept(requestBuilder);
			}

			@Override
			protected URI uri(Method method) {
				return URI.create(uri + "/" + method.getName());
			}
		};
		final var a = (A) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { A.class },
				handler);
		assertEquals(System.identityHashCode(a), a.hashCode());
		assertEquals(a, a);
		assertNotEquals(a, this);
		assertEquals(handler.toString(), a.toString());
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockRemoteInvocationFailure = mockStatic(RemoteInvocationFailure.class)) {
			final var p = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString("[]")).thenReturn(p);

			final var errorMessage = IdGenerator.generateId();
			final var resp = mock(HttpResponse.class);

			when(resp.body()).thenReturn(new ByteArrayInputStream(("<!doctype html>\n" + errorMessage).getBytes()));

			final var ex = new HttpException(resp, IdGenerator.generateId());
			mockIuHttp.when(() -> IuHttp.send(eq(URI.create(uri + "/b")), argThat(c -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> c.accept(rb));
				verify(check).accept(rb);
				verify(rb).POST(p);
				return true;
			}), eq(IuHttp.NO_CONTENT))).thenThrow(ex);

			final var error = assertThrows(IllegalStateException.class,
					() -> handler.doInvoke(method(A.class, "b"), null));
			assertEquals("<!doctype html>\n" + errorMessage, error.getMessage());
			assertSame(ex, error.getCause());
			assertInstanceOf(JsonParsingException.class, error.getSuppressed()[0]);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testConnectionFailurePropagates() {
		final var uri = URI.create(TEST_URI + "/" + IdGenerator.generateId());
		final var check = mock(Consumer.class);
		final var handler = new RemoteInvocationHandler() {
			@Override
			protected void authorize(Builder requestBuilder) {
				check.accept(requestBuilder);
			}

			@Override
			protected URI uri(Method method) {
				return URI.create(uri + "/" + method.getName());
			}
		};
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final var p = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString("[]")).thenReturn(p);

			// a pre-response HttpException carries no response body to adapt
			final var ex = new HttpException(IdGenerator.generateId(), new IOException());
			mockIuHttp.when(() -> IuHttp.send(eq(URI.create(uri + "/b")), argThat(c -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> c.accept(rb));
				verify(check).accept(rb);
				verify(rb).POST(p);
				return true;
			}), eq(IuHttp.NO_CONTENT))).thenThrow(ex);

			assertSame(ex, assertThrows(HttpException.class,
					() -> handler.doInvoke(method(IoAware.class, "b"), null)));
		}
	}

	@Test
	public void testConnectionFailureUndeclared() {
		final var ex = new HttpException(IdGenerator.generateId(), new IOException());
		try (final var handler = new TestHandler() {
			@Override
			protected Object doInvoke(Method method, Object[] args) throws Exception {
				throw ex;
			}
		}) {
			// A#b() doesn't declare IOException, so the proxy wraps it
			assertSame(ex, assertThrows(UndeclaredThrowableException.class, proxy(handler)::b).getCause());
		}
	}

	@Test
	public void testInvokeObjectMethodUnhandled() throws Exception {
		final var handler = new RemoteInvocationHandler() {
			@Override
			protected void authorize(Builder requestBuilder) {
			}

			@Override
			protected URI uri(Method method) {
				return TEST_URI;
			}
		};
		final var a = (A) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { A.class },
				handler);
		assertEquals(null, handler.invokeObjectMethod(a, Object.class.getDeclaredMethod("finalize"), null));
	}

	/**
	 * Base handler for cache, timeout, and lifecycle tests.
	 */
	private abstract static class TestHandler extends RemoteInvocationHandler {
		TestHandler() {
			super();
		}

		TestHandler(RemoteInvocationConfiguration config) {
			super(config);
		}

		@Override
		protected void authorize(Builder requestBuilder) {
		}

		@Override
		protected URI uri(Method method) {
			return TEST_URI;
		}
	}

	/**
	 * Single-threaded configuration, so tests can assert against exactly one
	 * pooled thread.
	 */
	private static RemoteInvocationConfiguration singleThreadConfig(Duration refreshTtl, Duration cacheTtl) {
		return new RemoteInvocationConfiguration() {
			@Override
			public Duration getRefreshTtl() {
				return refreshTtl;
			}

			@Override
			public Duration getCacheTtl() {
				return cacheTtl;
			}

			@Override
			public int getThreads() {
				return 1;
			}
		};
	}

	private static RemoteInvocationConfiguration cacheConfig(Duration refreshTtl, Duration cacheTtl) {
		return new RemoteInvocationConfiguration() {
			@Override
			public Duration getRefreshTtl() {
				return refreshTtl;
			}

			@Override
			public Duration getCacheTtl() {
				return cacheTtl;
			}
		};
	}

	private static RemoteInvocationConfiguration cacheConfig(Duration refreshTtl, Duration cacheTtl,
			Duration callTtl) {
		return new RemoteInvocationConfiguration() {
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
		};
	}

	private static A proxy(RemoteInvocationHandler handler) {
		return (A) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[] { A.class }, handler);
	}

	/**
	 * Polls until the expected number of remote calls have started, since a
	 * background refresh is triggered asynchronously.
	 */
	private static void awaitCalls(AtomicInteger calls, int expected) throws Exception {
		final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
		while (calls.get() < expected && System.nanoTime() < expires)
			Thread.sleep(5L);
		assertEquals(expected, calls.get());
	}

	/**
	 * Asserts no further remote call is triggered, allowing time for an unwanted
	 * call to show up.
	 */
	private static void assertNoFurtherCalls(AtomicInteger calls, int expected) throws Exception {
		Thread.sleep(250L);
		assertEquals(expected, calls.get());
	}

	/**
	 * Polls until a cached value converges on the expected result, to allow for a
	 * background refresh landing asynchronously.
	 */
	private static void awaitEcho(A a, String key, String expected) throws Exception {
		final var expires = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
		String last;
		do {
			last = a.echo(key);
			if (expected.equals(last))
				return;
			Thread.sleep(10L);
		} while (System.nanoTime() < expires);
		assertEquals(expected, last, "timed out waiting for refresh");
	}

	@Test
	public void testConstructorValidatesConfiguration() {
		assertEquals("Refresh TTL must be positive", assertThrows(IllegalArgumentException.class,
				() -> new TestHandler(cacheConfig(Duration.ZERO, Duration.ofMinutes(1L))) {
				}).getMessage());
		assertEquals("Refresh TTL must be positive", assertThrows(IllegalArgumentException.class,
				() -> new TestHandler(cacheConfig(Duration.ofMinutes(-1L), Duration.ofMinutes(1L))) {
				}).getMessage());
		assertEquals("Missing cache TTL", assertThrows(NullPointerException.class,
				() -> new TestHandler(cacheConfig(Duration.ofMinutes(5L), null)) {
				}).getMessage());
		assertEquals("Cache TTL must be longer than refresh TTL", assertThrows(IllegalArgumentException.class,
				() -> new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(5L))) {
				}).getMessage());
		assertEquals("Missing call TTL", assertThrows(NullPointerException.class,
				() -> new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L), null)) {
				}).getMessage());
		assertEquals("Call TTL must be positive", assertThrows(IllegalArgumentException.class,
				() -> new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ZERO)) {
				}).getMessage());
		assertEquals("Call TTL must be positive",
				assertThrows(IllegalArgumentException.class, () -> new TestHandler(
						cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ofMinutes(-1L))) {
				}).getMessage());

		// defaults apply to everything the configuration does not override
		assertEquals("Call threads must be positive", assertThrows(IllegalArgumentException.class,
				() -> new TestHandler(new RemoteInvocationConfiguration() {
					@Override
					public int getThreads() {
						return 0;
					}
				}) {
				}).getMessage());
		assertEquals("Call pending queue size must be positive", assertThrows(IllegalArgumentException.class,
				() -> new TestHandler(new RemoteInvocationConfiguration() {
					@Override
					public int getPending() {
						return 0;
					}
				}) {
				}).getMessage());
	}

	@Test
	public void testDefensiveCallCache() throws Exception {
		final var calls = new AtomicInteger();
		try (final var handler = new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L))) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return args[0] + "/" + calls.incrementAndGet();
			}
		}) {
			assertTrue(handler.usesCache(A.class.getMethod("echo", String.class)));

			final var a = proxy(handler);
			assertEquals("first/1", a.echo("first"));
			assertEquals("first/1", a.echo("first"));
			assertEquals("second/2", a.echo("second"));
			assertEquals("second/2", a.echo("second"));
			assertEquals(2, calls.get());
		}
	}

	@Test
	public void testInitialCallersShareOneInvocation() throws Exception {
		final var calls = new AtomicInteger();
		final var arrived = new CountDownLatch(1);
		final var proceed = new CountDownLatch(1);
		try (final var handler = new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L))) {
			@Override
			protected Object doInvoke(Method method, Object[] args) throws Exception {
				final var call = calls.incrementAndGet();
				arrived.countDown();
				assertTrue(proceed.await(5L, TimeUnit.SECONDS));
				return args[0] + "/" + call;
			}
		}) {
			final var a = proxy(handler);
			final var results = Collections.synchronizedList(new ArrayList<String>());
			final var errors = Collections.synchronizedList(new ArrayList<Throwable>());
			final List<Thread> callers = new ArrayList<>();
			for (var i = 0; i < 8; i++) {
				final var caller = new Thread(() -> {
					try {
						results.add(a.echo("shared"));
					} catch (Throwable e) {
						errors.add(e);
					}
				});
				callers.add(caller);
				caller.start();
			}

			// hold the single invocation open until every caller is waiting on it
			assertTrue(arrived.await(5L, TimeUnit.SECONDS));
			Thread.sleep(100L);
			proceed.countDown();

			for (final var caller : callers)
				caller.join(5000L);

			assertEquals(List.of(), errors);
			assertEquals(1, calls.get(), "each initial caller must share one invocation");
			assertEquals(8, results.size());
			results.forEach(result -> assertEquals("shared/1", result));
		}
	}

	@Test
	public void testRefreshIsBackgroundOnlyAfterFirstCall() throws Exception {
		final var calls = new AtomicInteger();
		final var hold = new AtomicBoolean();
		final var proceed = new CountDownLatch(1);
		final var refreshTtl = Duration.ofMillis(100L);
		try (final var handler = new TestHandler(cacheConfig(refreshTtl, Duration.ofSeconds(30L))) {
			@Override
			protected Object doInvoke(Method method, Object[] args) throws Exception {
				final var call = calls.incrementAndGet();
				if (hold.get())
					assertTrue(proceed.await(5L, TimeUnit.SECONDS));
				return args[0] + "/" + call;
			}
		}) {
			final var a = proxy(handler);

			// first call blocks, since there is nothing cached to fall back on
			assertEquals("stale/1", a.echo("stale"));

			Thread.sleep(refreshTtl.toMillis() + 25L);
			hold.set(true);

			// stale entry: triggers a background refresh, returns last good result
			final var start = System.nanoTime();
			assertEquals("stale/1", a.echo("stale"));
			final var blockedFor = Duration.ofNanos(System.nanoTime() - start);
			assertTrue(blockedFor.toMillis() < 2000L, () -> "caller blocked for " + blockedFor);
			awaitCalls(calls, 2);

			// a refresh is already in flight, so no further call is triggered
			assertEquals("stale/1", a.echo("stale"));
			assertEquals("stale/1", a.echo("stale"));
			assertNoFurtherCalls(calls, 2);

			proceed.countDown();
			awaitEcho(a, "stale", "stale/2");
		}
	}

	@Test
	public void testFailedBackgroundRefreshServesLastGoodResult() throws Exception {
		final var calls = new AtomicInteger();
		final var refreshTtl = Duration.ofMillis(100L);
		try (final var handler = new TestHandler(cacheConfig(refreshTtl, Duration.ofSeconds(30L))) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				if (calls.incrementAndGet() > 1)
					throw new IllegalStateException("remote service unavailable");
				return "cached";
			}
		}) {
			final var a = proxy(handler);
			assertEquals("cached", a.echo("key"));

			Thread.sleep(refreshTtl.toMillis() + 25L);

			// the failed refresh is logged, not propagated: the caller still gets a result
			assertEquals("cached", a.echo("key"));
			awaitCalls(calls, 2);
			assertEquals("cached", a.echo("key"));
		}
	}

	@Test
	public void testFirstCallFailurePropagatesToAllInitialCallers() throws Exception {
		final var calls = new AtomicInteger();
		final var arrived = new CountDownLatch(1);
		final var proceed = new CountDownLatch(1);
		try (final var handler = new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L))) {
			@Override
			protected Object doInvoke(Method method, Object[] args) throws Exception {
				calls.incrementAndGet();
				arrived.countDown();
				assertTrue(proceed.await(5L, TimeUnit.SECONDS));
				throw new IllegalStateException("remote service unavailable");
			}
		}) {
			final var a = proxy(handler);
			final var errors = Collections.synchronizedList(new ArrayList<Throwable>());
			final List<Thread> callers = new ArrayList<>();
			for (var i = 0; i < 4; i++) {
				final var caller = new Thread(() -> {
					try {
						a.echo("key");
					} catch (Throwable e) {
						errors.add(e);
					}
				});
				callers.add(caller);
				caller.start();
			}

			assertTrue(arrived.await(5L, TimeUnit.SECONDS));
			Thread.sleep(100L);
			proceed.countDown();

			for (final var caller : callers)
				caller.join(5000L);

			assertEquals(1, calls.get());
			assertEquals(4, errors.size());
			errors.forEach(e -> assertEquals("remote service unavailable",
					assertInstanceOf(IllegalStateException.class, e).getMessage()));
		}
	}

	@Test
	public void testSupersededRefreshDoesNotBlockOrOverwriteLaterRefresh() throws Exception {
		final var calls = new AtomicInteger();
		final var released = new AtomicBoolean();
		final var refreshTtl = Duration.ofMillis(50L);
		final var callTtl = Duration.ofMillis(200L);
		try (final var handler = new TestHandler(cacheConfig(refreshTtl, Duration.ofSeconds(30L), callTtl)) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				final var call = calls.incrementAndGet();
				if (call == 2)
					// hangs past the call TTL, ignoring the abandonment interrupt
					while (!released.get())
						try {
							Thread.sleep(10L);
						} catch (InterruptedException e) {
							continue;
						}

				return "v" + call;
			}
		}) {
			final var a = proxy(handler);
			assertEquals("v1", a.echo("key"));

			Thread.sleep(refreshTtl.toMillis() + 25L);
			assertEquals("v1", a.echo("key"));
			awaitCalls(calls, 2);

			// while that refresh is in flight no other refresh starts
			assertEquals("v1", a.echo("key"));
			assertNoFurtherCalls(calls, 2);

			// once it outlives the call TTL it is abandoned and replaced
			Thread.sleep(callTtl.toMillis() + 50L);
			assertEquals("v1", a.echo("key"));
			awaitCalls(calls, 3);
			awaitEcho(a, "key", "v3");

			// the superseded refresh must not overwrite the newer result
			released.set(true);
			Thread.sleep(200L);
			assertEquals("v3", a.echo("key"));
		}
	}

	@Test
	public void testCallTimeoutWithoutCache() throws Throwable {
		final var interrupted = new CountDownLatch(1);
		try (final var handler = new TestHandler(new RemoteInvocationConfiguration() {
			@Override
			public Duration getRefreshTtl() {
				return null;
			}

			@Override
			public Duration getCallTtl() {
				return Duration.ofMillis(100L);
			}
		}) {
			@Override
			protected Object doInvoke(Method method, Object[] args) throws Exception {
				try {
					Thread.sleep(5000L);
				} catch (InterruptedException e) {
					interrupted.countDown();
					throw e;
				}
				return "never";
			}
		}) {
			final var echo = A.class.getMethod("echo", String.class);
			assertThrows(TimeoutException.class, () -> handler.invoke(null, echo, new Object[] { "key" }));

			// an uncached call has no other waiters, so it is cancelled
			assertTrue(interrupted.await(5L, TimeUnit.SECONDS));
		}
	}

	@Test
	public void testCallTimeoutOnFirstCachedCallLeavesCallRunning() throws Throwable {
		final var calls = new AtomicInteger();
		try (final var handler = new TestHandler(
				cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ofMillis(100L))) {
			@Override
			protected Object doInvoke(Method method, Object[] args) throws Exception {
				calls.incrementAndGet();
				Thread.sleep(300L);
				return "slow";
			}
		}) {
			final var echo = A.class.getMethod("echo", String.class);
			assertThrows(TimeoutException.class, () -> handler.invoke(null, echo, new Object[] { "key" }));
			awaitCalls(calls, 1);

			// let the call the caller gave up on run to completion undisturbed
			Thread.sleep(600L);

			// it still populated the cache, so the next caller needs no new call
			assertEquals("slow", handler.invoke(null, echo, new Object[] { "key" }));
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void testCloseReleasesResourcesAndRejectsFurtherCalls() throws Throwable {
		final var calls = new AtomicInteger();
		final var handler = new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L))) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return "call/" + calls.incrementAndGet();
			}
		};
		final var a = proxy(handler);
		assertEquals("call/1", a.echo("key"));
		assertEquals("call/1", a.echo("key"));

		handler.close();
		handler.close(); // idempotent

		final var echo = A.class.getMethod("echo", String.class);
		assertEquals("Remote invocation handler is closed", assertThrows(IllegalStateException.class,
				() -> handler.invoke(null, echo, new Object[] { "key" })).getMessage());

		// object methods still work on a closed handler
		assertEquals(System.identityHashCode(a), a.hashCode());
		assertEquals(1, calls.get());
	}

	@Test
	public void testCloseWithoutCache() throws Exception {
		final var handler = new TestHandler() {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return "uncached";
			}
		};
		assertEquals("uncached", proxy(handler).echo("key"));
		assertDoesNotThrow(handler::close);
	}

	@Test
	public void testDefaultHandlerDoesNotCache() throws Exception {
		final var calls = new AtomicInteger();
		try (final var handler = new TestHandler() {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return Integer.toString(calls.incrementAndGet());
			}
		}) {
			assertFalse(handler.usesCache(A.class.getMethod("echo", String.class)));

			final var a = proxy(handler);
			assertEquals("1", a.echo("first"));
			assertEquals("2", a.echo("first"));
		}
	}

	@Test
	public void testDefaultHandlerBypassesOverriddenCachePolicy() throws Exception {
		final var calls = new AtomicInteger();
		try (final var handler = new TestHandler() {
			@Override
			protected boolean usesCache(Method method) {
				return true;
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return Integer.toString(calls.incrementAndGet());
			}
		}) {
			final var a = proxy(handler);
			assertEquals("1", a.echo("first"));
			assertEquals("2", a.echo("first"));
		}
	}

	@Test
	public void testCachedNoArgMethod() throws Exception {
		final var calls = new AtomicInteger();
		try (final var handler = new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L))) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				calls.incrementAndGet();
				return null;
			}
		}) {
			final var a = proxy(handler);
			a.b();
			a.b();
			assertEquals(1, calls.get());
		}
	}

	@Test
	public void testSuccessfulNonCachedInvocationInvalidatesCache() throws Exception {
		final var calls = new AtomicInteger();
		final var echo = A.class.getMethod("echo", String.class);
		try (final var handler = new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L))) {
			@Override
			protected boolean usesCache(Method method) {
				return method.equals(echo);
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				calls.incrementAndGet();
				return args == null ? null : args[0] + "/" + calls.get();
			}
		}) {
			final var a = proxy(handler);
			assertEquals("first/1", a.echo("first"));
			assertEquals("first/1", a.echo("first"));
			assertEquals(1, calls.get());

			a.b();
			assertEquals(2, calls.get());

			assertEquals("first/3", a.echo("first"));
			assertEquals(3, calls.get());
		}
	}

	@Test
	public void testForwardsAndRestoresContextClassLoader() throws Throwable {
		final var poolBase = new ClassLoader(null) {
		};
		final var caller = new ClassLoader(null) {
		};
		final var callThread = new AtomicReference<Thread>();
		final var callContext = new AtomicReference<ClassLoader>();

		final var restore = Thread.currentThread().getContextClassLoader();
		try (final var handler = new TestHandler(singleThreadConfig(null, null)) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				callThread.set(Thread.currentThread());
				callContext.set(Thread.currentThread().getContextClassLoader());
				return "result";
			}
		}) {
			final var a = proxy(handler);

			// create the single pooled thread while a known context is in effect
			Thread.currentThread().setContextClassLoader(poolBase);
			assertEquals("result", a.echo("warmup"));
			final var pooled = callThread.get();
			assertEquals(poolBase, callContext.get());

			Thread.currentThread().setContextClassLoader(caller);
			assertEquals("result", a.echo("key"));

			assertSame(pooled, callThread.get(), "expected the same pooled thread");
			assertSame(caller, callContext.get(), "caller context must be forwarded to the call");
			assertSame(poolBase, pooled.getContextClassLoader(), "pooled thread context must be restored");
		} finally {
			Thread.currentThread().setContextClassLoader(restore);
		}
	}

	@Test
	public void testRestoresContextClassLoaderAfterFailure() throws Throwable {
		final var poolBase = new ClassLoader(null) {
		};
		final var caller = new ClassLoader(null) {
		};
		final var callThread = new AtomicReference<Thread>();

		final var restore = Thread.currentThread().getContextClassLoader();
		try (final var handler = new TestHandler(singleThreadConfig(null, null)) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				callThread.set(Thread.currentThread());
				if (args != null)
					throw new IllegalStateException("call failed");
				return "warmup";
			}
		}) {
			final var a = proxy(handler);

			Thread.currentThread().setContextClassLoader(poolBase);
			a.b();
			final var pooled = callThread.get();

			Thread.currentThread().setContextClassLoader(caller);
			assertEquals("call failed", assertThrows(IllegalStateException.class, () -> a.echo("key")).getMessage());

			assertSame(poolBase, pooled.getContextClassLoader(), "context must be restored after a failed call");
		} finally {
			Thread.currentThread().setContextClassLoader(restore);
		}
	}

	@Test
	public void testCapturedContextIsAppliedAndRestoredAroundCall() throws Exception {
		final var events = Collections.synchronizedList(new ArrayList<String>());
		final var callerThread = Thread.currentThread();
		try (final var handler = new TestHandler(singleThreadConfig(null, null)) {
			@Override
			protected Supplier<Runnable> captureContext() {
				final var delegate = super.captureContext();
				events.add("capture:" + (Thread.currentThread() == callerThread));
				return () -> {
					final var restore = delegate.get();
					events.add("apply:" + (Thread.currentThread() == callerThread));
					return () -> {
						events.add("restore:" + (Thread.currentThread() == callerThread));
						restore.run();
					};
				};
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				events.add("call");
				return "result";
			}
		}) {
			assertEquals("result", proxy(handler).echo("key"));

			// capture on the caller, apply and restore around the call on the pool
			assertEquals(List.of("capture:true", "apply:false", "call", "restore:false"), events);
		}
	}

	@Test
	public void testFailedContextRestoreDoesNotMaskCallOutcome() throws Throwable {
		try (final var handler = new TestHandler(singleThreadConfig(null, null)) {
			@Override
			protected Supplier<Runnable> captureContext() {
				final var delegate = super.captureContext();
				return () -> {
					final var restore = delegate.get();
					return () -> {
						restore.run();
						throw new IllegalStateException("restore failed");
					};
				};
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				if (args == null)
					throw new UnsupportedOperationException("call failed");
				return "result";
			}
		}) {
			final var a = proxy(handler);

			// the result stands, and the restore failure is logged instead
			assertEquals("result", a.echo("key"));
			assertEquals("call failed", assertThrows(UnsupportedOperationException.class, a::b).getMessage());
		}
	}

	@Test
	public void testRefreshDispatchFailureServesLastGoodResult() throws Exception {
		final var calls = new AtomicInteger();
		final var failCapture = new AtomicBoolean();
		final var refreshTtl = Duration.ofMillis(100L);
		try (final var handler = new TestHandler(cacheConfig(refreshTtl, Duration.ofSeconds(30L))) {
			@Override
			protected Supplier<Runnable> captureContext() {
				if (failCapture.get())
					// stands in for any dispatch failure, e.g. a full pending queue
					throw new RejectedExecutionException("cannot dispatch");
				return super.captureContext();
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return "call/" + calls.incrementAndGet();
			}
		}) {
			final var a = proxy(handler);
			assertEquals("call/1", a.echo("key"));

			Thread.sleep(refreshTtl.toMillis() + 25L);
			failCapture.set(true);

			// the refresh never starts, so the last good result is served
			assertEquals("call/1", a.echo("key"));
			assertNoFurtherCalls(calls, 1);

			// a later refresh is still able to run once dispatch recovers
			failCapture.set(false);
			assertEquals("call/1", a.echo("key"));
			awaitCalls(calls, 2);
			awaitEcho(a, "key", "call/2");
		}
	}

	@Test
	public void testDispatchFailureWithNothingCachedPropagates() throws Exception {
		try (final var handler = new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L))) {
			@Override
			protected Supplier<Runnable> captureContext() {
				throw new RejectedExecutionException("cannot dispatch");
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return "never";
			}
		}) {
			assertEquals("cannot dispatch", assertThrows(RejectedExecutionException.class,
					() -> proxy(handler).echo("key")).getMessage());
		}
	}

	@Test
	public void testFailedNonCachedInvocationPreservesCache() throws Exception {
		final var calls = new AtomicInteger();
		final var echo = A.class.getMethod("echo", String.class);
		try (final var handler = new TestHandler(cacheConfig(Duration.ofMinutes(5L), Duration.ofMinutes(30L))) {
			@Override
			protected boolean usesCache(Method method) {
				return method.equals(echo);
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				calls.incrementAndGet();
				if (args == null)
					throw new IllegalStateException("write failed");
				return args[0] + "/" + calls.get();
			}
		}) {
			final var a = proxy(handler);
			assertEquals("first/1", a.echo("first"));

			assertEquals("write failed", assertThrows(IllegalStateException.class, a::b).getMessage());
			assertEquals(2, calls.get());

			// a failed write must not discard results that are still valid
			assertEquals("first/1", a.echo("first"));
			assertEquals(2, calls.get());
		}
	}

}
