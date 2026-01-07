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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
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
			error.printStackTrace();
			assertInstanceOf(JsonParsingException.class, error.getSuppressed()[0]);
		}
	}

}
