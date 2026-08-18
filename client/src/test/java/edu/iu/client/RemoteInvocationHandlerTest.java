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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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

			assertDoesNotThrow(a::b);
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

			assertEquals(message, a.echo(message));
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

			final var error = assertThrows(RemoteInvocationException.class, a::b);
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

			final var error = assertThrows(IllegalStateException.class, a::b);
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
		final var a = (IoAware) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
				new Class<?>[] { IoAware.class }, handler);
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

			assertSame(ex, assertThrows(HttpException.class, a::b));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testConnectionFailureUndeclared() {
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
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final var p = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString("[]")).thenReturn(p);

			final var ex = new HttpException(IdGenerator.generateId(), new IOException());
			mockIuHttp.when(() -> IuHttp.send(eq(URI.create(uri + "/b")), argThat(c -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> c.accept(rb));
				verify(check).accept(rb);
				verify(rb).POST(p);
				return true;
			}), eq(IuHttp.NO_CONTENT))).thenThrow(ex);

			// A#b() doesn't declare IOException, so the proxy wraps it
			assertSame(ex, assertThrows(UndeclaredThrowableException.class, a::b).getCause());
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

	@Test
	public void testConstructorValidatesTtls() {
		assertThrows(IllegalArgumentException.class,
				() -> new RemoteInvocationHandler(Duration.ZERO, Duration.ofMinutes(1L)) {
					@Override
					protected void authorize(Builder requestBuilder) {
					}

					@Override
					protected URI uri(Method method) {
						return TEST_URI;
					}
				});
		assertThrows(IllegalArgumentException.class,
				() -> new RemoteInvocationHandler(Duration.ofMinutes(-1L), Duration.ofMinutes(1L)) {
					@Override
					protected void authorize(Builder requestBuilder) {
					}

					@Override
					protected URI uri(Method method) {
						return TEST_URI;
					}
				});
		assertThrows(IllegalArgumentException.class,
				() -> new RemoteInvocationHandler(Duration.ofMinutes(5L), Duration.ofMinutes(5L)) {
					@Override
					protected void authorize(Builder requestBuilder) {
					}

					@Override
					protected URI uri(Method method) {
						return TEST_URI;
					}
				});
	}

	@Test
	public void testDefensiveCallCache() throws Exception {
		final var calls = new AtomicInteger();
		final var remoteAvailable = new AtomicBoolean(true);
		final var refreshTtl = Duration.ofSeconds(1L);
		final var handler = new RemoteInvocationHandler(refreshTtl, Duration.ofSeconds(5L)) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				calls.incrementAndGet();
				if (!remoteAvailable.get())
					throw new IllegalStateException("remote service unavailable");
				return args[0] + "/" + calls.get();
			}

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
		final var echo = A.class.getMethod("echo", String.class);

		assertTrue(handler.usesCache(echo));
		assertEquals("first/1", a.echo("first"));
		assertEquals("first/1", a.echo("first"));
		assertEquals("second/2", a.echo("second"));
		assertEquals(2, calls.get());

		Thread.sleep(refreshTtl.toMillis() + 25L);
		assertEquals("first/3", a.echo("first"));
		assertEquals("first/3", a.echo("first"));
		assertEquals(3, calls.get());

		Thread.sleep(refreshTtl.toMillis() + 25L);
		remoteAvailable.set(false);
		assertEquals("first/3", a.echo("first"));
		assertEquals(4, calls.get());
	}

	@Test
	public void testDefaultHandlerDoesNotCache() throws Exception {
		final var calls = new AtomicInteger();
		final var handler = new RemoteInvocationHandler() {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return Integer.toString(calls.incrementAndGet());
			}

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

		assertFalse(handler.usesCache(A.class.getMethod("echo", String.class)));
		assertEquals("1", a.echo("first"));
		assertEquals("2", a.echo("first"));
	}

	@Test
	public void testDefaultHandlerBypassesOverriddenCachePolicy() throws Exception {
		final var calls = new AtomicInteger();
		final var handler = new RemoteInvocationHandler() {
			@Override
			protected boolean usesCache(Method method) {
				return true;
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				return Integer.toString(calls.incrementAndGet());
			}

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

		assertEquals("1", a.echo("first"));
		assertEquals("2", a.echo("first"));
	}

	@Test
	public void testInterruptedCacheRefreshPropagatesAndRestoresInterruptStatus() throws Throwable {
		final var calls = new AtomicInteger();
		final var handler = new RemoteInvocationHandler(Duration.ofMillis(1L), Duration.ofSeconds(1L)) {
			@Override
			protected Object doInvoke(Method method, Object[] args) throws Throwable {
				if (calls.incrementAndGet() > 1)
					throw new InterruptedException("refresh interrupted");
				return "cached";
			}

			@Override
			protected void authorize(Builder requestBuilder) {
			}

			@Override
			protected URI uri(Method method) {
				return TEST_URI;
			}
		};
		final var echo = A.class.getMethod("echo", String.class);

		assertEquals("cached", handler.invoke(null, echo, new Object[] { "first" }));
		Thread.sleep(25L);
		try {
			assertThrows(InterruptedException.class, () -> handler.invoke(null, echo, new Object[] { "first" }));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	public void testErrorDuringCacheRefreshPropagates() throws Throwable {
		final var calls = new AtomicInteger();
		final var handler = new RemoteInvocationHandler(Duration.ofMillis(1L), Duration.ofSeconds(1L)) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				if (calls.incrementAndGet() > 1)
					throw new AssertionError("refresh failed");
				return "cached";
			}

			@Override
			protected void authorize(Builder requestBuilder) {
			}

			@Override
			protected URI uri(Method method) {
				return TEST_URI;
			}
		};
		final var echo = A.class.getMethod("echo", String.class);

		assertEquals("cached", handler.invoke(null, echo, new Object[] { "first" }));
		Thread.sleep(25L);
		assertThrows(AssertionError.class, () -> handler.invoke(null, echo, new Object[] { "first" }));
	}

	@Test
	public void testCachedNoArgMethod() throws Exception {
		final var calls = new AtomicInteger();
		final var handler = new RemoteInvocationHandler(Duration.ofMinutes(5L), Duration.ofMinutes(30L)) {
			@Override
			protected Object doInvoke(Method method, Object[] args) {
				calls.incrementAndGet();
				return null;
			}

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

		a.b();
		a.b();
		assertEquals(1, calls.get());
	}

	@Test
	public void testNonCachedInvocationInvalidatesCache() throws Exception {
		final var calls = new AtomicInteger();
		final var echo = A.class.getMethod("echo", String.class);
		final var b = A.class.getMethod("b");
		final var handler = new RemoteInvocationHandler(Duration.ofMinutes(5L), Duration.ofMinutes(30L)) {
			@Override
			protected boolean usesCache(Method method) {
				return method.equals(echo);
			}

			@Override
			protected Object doInvoke(Method method, Object[] args) {
				calls.incrementAndGet();
				return args == null ? null : args[0] + "/" + calls.get();
			}

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

		assertEquals("first/1", a.echo("first"));
		assertEquals("first/1", a.echo("first"));
		assertEquals(1, calls.get());

		a.b();
		assertEquals(2, calls.get());

		assertEquals("first/3", a.echo("first"));
		assertEquals(3, calls.get());
	}

}
