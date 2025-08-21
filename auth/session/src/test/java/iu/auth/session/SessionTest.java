/*
 * Copyright © 2025 Indiana University
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
package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTest;
import edu.iu.test.IuTestLogger;
import iu.crypt.Jwt;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class SessionTest {

	private Session session;
	private URI resourceUri;
	private Duration expires;

	interface SessionDetailInterface {
		String getFoo();

		void setFoo(String foo);
	}

	@BeforeEach
	void setUp() {
		resourceUri = URI.create(IdGenerator.generateId());
		expires = Duration.ofHours(1);
		session = new Session(resourceUri, expires);
	}

	@Test
	public void testTokenRequiresDirect() {
		final var token = IdGenerator.generateId();
		final var header = mock(WebCryptoHeader.class);
		try (final var mockWebCryptoHeader = mockStatic(WebCryptoHeader.class)) {
			mockWebCryptoHeader.when(() -> WebCryptoHeader.getProtectedHeader(token)).thenReturn(header);
			final var error = assertThrows(IllegalArgumentException.class, () -> new Session(token, null, null, null));
			assertEquals("Invalid token key protection algorithm", error.getMessage());
		}
	}

	@Test
	public void testTokenRequiresA256GCM() {
		final var token = IdGenerator.generateId();
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.DIRECT);
		try (final var mockWebCryptoHeader = mockStatic(WebCryptoHeader.class)) {
			mockWebCryptoHeader.when(() -> WebCryptoHeader.getProtectedHeader(token)).thenReturn(header);
			final var error = assertThrows(IllegalArgumentException.class, () -> new Session(token, null, null, null));
			assertEquals("Invalid token content encryption algorithm", error.getMessage());
		}
	}

	@Test
	public void testTokenRequiresSessionType() {
		final var token = IdGenerator.generateId();
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.DIRECT);
		when(header.getExtendedParameter("enc")).thenReturn(Encryption.A256GCM);
		try (final var mockWebCryptoHeader = mockStatic(WebCryptoHeader.class)) {
			mockWebCryptoHeader.when(() -> WebCryptoHeader.getProtectedHeader(token)).thenReturn(header);
			final var error = assertThrows(IllegalArgumentException.class, () -> new Session(token, null, null, null));
			assertEquals("Invalid token type", error.getMessage());
		}
	}

	@Test
	public void testTokenConstructor() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var maxSessionTtl = Duration.ofHours(1);
		final var token = IdGenerator.generateId();
		final var secretKey = new byte[32];
		ThreadLocalRandom.current().nextBytes(secretKey);

		final var issuerKey = mock(WebKey.class);
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.DIRECT);
		when(header.getExtendedParameter("enc")).thenReturn(Encryption.A256GCM);
		when(header.getContentType()).thenReturn("session+jwt");

		final var claims = mock(JsonObject.class);

		try (final var mockWebCryptoHeader = mockStatic(WebCryptoHeader.class);
				final var mockJwt = mockStatic(Jwt.class);
				final var mockSessionJwt = mockConstruction(SessionJwt.class, (a, ctx) -> {
					assertEquals(claims, ctx.arguments().get(0));
					when(a.getIssuer()).thenReturn(resourceUri);
					when(a.getSubject()).thenReturn(resourceUri.toString());
					when(a.getExpires()).thenReturn(Instant.now().plus(maxSessionTtl));
				})) {
			mockJwt.when(() -> Jwt.decryptAndVerify(eq(token), eq(issuerKey), argThat(a -> {
				final var audienceKey = assertInstanceOf(WebKey.class, a);
				assertEquals(WebKey.Type.RAW, audienceKey.getType());
				assertArrayEquals(secretKey, audienceKey.getKey());
				return true;
			}))).thenReturn(claims);
			mockWebCryptoHeader.when(() -> WebCryptoHeader.getProtectedHeader(token)).thenReturn(header);
			final var session = assertDoesNotThrow(() -> new Session(token, secretKey, issuerKey, maxSessionTtl));
			assertTrue(Duration.between(Instant.now(), session.getExpires()).compareTo(maxSessionTtl) <= 0);

			final var sessionJwt = mockSessionJwt.constructed().get(0);
			verify(sessionJwt).validateClaims(resourceUri, maxSessionTtl);
		}
	}

	@Test
	public void testGetDetailRequiresNamedModule() throws IOException {
		final var path = Path.of(IuTest.getProperty("project.build.testOutputDirectory")).toUri().toURL();
		try (final var loader = new URLClassLoader(new URL[] { path }, ClassLoader.getPlatformClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				synchronized (getClassLoadingLock(name)) {
					Class<?> c = findLoadedClass(name);
					if (c == null)
						try {
							c = findClass(name);
						} catch (ClassNotFoundException e) {
							return super.loadClass(name, resolve);
						}
					if (resolve)
						resolveClass(c);
					return c;
				}
			}
		}) {
			final var c = assertDoesNotThrow(() -> loader.loadClass(SessionDetailInterface.class.getName()));
			final var error = assertThrows(IllegalArgumentException.class, () -> session.getDetail(c));
			assertEquals("Invalid session type, must be in a named module", error.getMessage());
			final var error2 = assertThrows(IllegalArgumentException.class, () -> session.clearDetail(c));
			assertEquals("Invalid session type, must be in a named module", error2.getMessage());
		}
	}

	@Test
	public void testGetDetail() {
		final var detail = session.getDetail(SessionDetailInterface.class);
		assertNotNull(detail);
		assertTrue(Proxy.isProxyClass(detail.getClass()));
		assertInstanceOf(SessionDetail.class, Proxy.getInvocationHandler(detail));
		final var foo = IdGenerator.generateId();
		detail.setFoo(foo);
		assertEquals(foo, session.getDetail(SessionDetailInterface.class).getFoo());
		assertEquals("Session [resourceUri=" + resourceUri + ", expires=" + session.getExpires()
				+ ", changed=true, details={" + SessionDetailInterface.class.getModule().getName() + "/"
				+ SessionDetailInterface.class.getName() + "={foo=\"" + foo + "\"}}]", session.toString());
	}

	@Test
	public void testClearDetailNotChanged() {
		session.clearDetail(SessionDetailInterface.class);
		assertFalse(session.isChanged());
	}

	@Test
	public void testClearDetailChanged() {
		final var detail = session.getDetail(SessionDetailInterface.class);
		detail.setFoo(IdGenerator.generateId());
		session.clearDetail(SessionDetailInterface.class);
		assertNull(session.getDetail(SessionDetailInterface.class).getFoo());
		assertTrue(session.isChanged());
	}

	@Test
	public void testChangeFlagManipulation() {
		assertFalse(session.isChanged());
		session.setChanged(true);
		assertTrue(session.isChanged());
	}

	@Test
	public void testGetExpires() {
		Instant expirationTime = session.getExpires();
		assertNotNull(expirationTime);
		assertTrue(expirationTime.isAfter(Instant.now()));
	}

	@Test
	public void testGetResourceUri() {
		assertEquals(resourceUri, session.getResourceUri());
	}

	@Test
	public void testTokenizeWithValidParameters() {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);

		final var secretKey = EphemeralKeys.secret("AES", 256);
		final var issuerKey = WebKey.ephemeral(Algorithm.HS256);
		final var algorithm = WebKey.Algorithm.HS256;
		final var detail = session.getDetail(SessionDetailInterface.class);
		assertInstanceOf(SessionDetail.class, Proxy.getInvocationHandler(detail));

		final var foo = IdGenerator.generateId();
		detail.setFoo(foo);

		final var token = session.tokenize(secretKey, issuerKey, algorithm);
		assertNotNull(token);

		final var fromToken = new Session(token, secretKey, issuerKey, expires);
		assertEquals(foo, fromToken.getDetail(SessionDetailInterface.class).getFoo());
	}

}
