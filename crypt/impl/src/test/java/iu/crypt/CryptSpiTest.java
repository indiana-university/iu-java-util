/*
 * Copyright © 2024 Indiana University
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
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.client.HttpResponseHandler;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.PemEncoded.KeyType;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTest;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class CryptSpiTest extends CryptImplTestCase {

	private CryptSpi spi;

	@BeforeEach
	public void setUp() {
		spi = new CryptSpi();
	}

	@AfterEach
	public void tearDown() {
		spi = null;
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetCertificateChain() {
		final var uri = mock(URI.class);
		final var cert = mock(X509Certificate.class);
		final var httpHandler = mock(HttpResponseHandler.class);
		final var pem = mock(PemEncoded.class);
		when(pem.getKeyType()).thenReturn(KeyType.CERTIFICATE);
		when(pem.asCertificate()).thenReturn(cert);
		final var pemIter = IuIterable.iter(pem).iterator();
		final var in = mock(InputStream.class);
		try (final var mockPemEncoded = mockStatic(PemEncoded.class); //
				final var mockIuHttp = mockStatic(IuHttp.class)) {
			mockIuHttp.when(() -> IuHttp.validate(argThat(a -> {
				a.apply(in);
				mockPemEncoded.verify(() -> PemEncoded.parse(in));
				return true;
			}), eq(IuHttp.OK))).thenReturn(httpHandler);
			mockIuHttp.when(() -> IuHttp.get(uri, httpHandler)).thenReturn(pemIter);
			mockPemEncoded.when(() -> PemEncoded.getCertificateChain(pemIter))
					.thenReturn(new X509Certificate[] { cert });
			assertArrayEquals(new X509Certificate[] { cert }, spi.getCertificateChain(uri));
			mockIuHttp.verify(() -> IuHttp.get(uri, httpHandler));
			mockIuHttp.clearInvocations();
			assertArrayEquals(new X509Certificate[] { cert }, spi.getCertificateChain(uri));
			mockIuHttp.verify(() -> IuHttp.get(uri, httpHandler), never());
		}
	}

	@Test
	public void testGetJwkBuidler() {
		try (final var mockJwkBuilder = mockStatic(JwkBuilder.class)) {
			final var type = IuTest.rand(WebKey.Type.class);
			final var builder = mock(JwkBuilder.class);
			mockJwkBuilder.when(() -> JwkBuilder.of(type)).thenReturn(builder);
			assertSame(builder, spi.getJwkBuilder(type));
		}
	}

	@Test
	public void testParseJwk() {
		final var jwk = IdGenerator.generateId();
		final var json = mock(JsonObject.class);
		when(json.asJsonObject()).thenReturn(json);
		try (final var mockIuJson = mockStatic(IuJson.class); final var mockJwk = mockConstruction(Jwk.class)) {
			mockIuJson.when(() -> IuJson.parse(jwk)).thenReturn(json);
			final var parsedJwk = spi.parseJwk(jwk);
			assertSame(mockJwk.constructed().get(0), parsedJwk);
		}
	}

	@Test
	public void testParseJwks() {
		final var jwk = IdGenerator.generateId();
		final var json = mock(JsonObject.class);
		when(json.asJsonObject()).thenReturn(json);
		try (final var mockIuJson = mockStatic(IuJson.class); final var mockJwk = mockStatic(Jwk.class)) {
			mockIuJson.when(() -> IuJson.parse(jwk)).thenReturn(json);
			final var parsedJwk = mock(Jwk.class);
			final var parsedJwks = IuIterable.iter(parsedJwk);
			mockJwk.when(() -> Jwk.parseJwks(json)).thenReturn(parsedJwks);
			assertSame(parsedJwks, spi.parseJwks(jwk));
		}
	}

	@Test
	public void testReadJwksFromUri() {
		final var uri = mock(URI.class);
		final var webKey = mock(WebKey.class);
		final var jwks = IuIterable.iter(webKey);
		try (final var mockJwk = mockStatic(Jwk.class)) {
			mockJwk.when(() -> Jwk.readJwks(uri)).thenReturn(jwks);
			assertSame(jwks, spi.readJwks(uri));
		}
	}

	@Test
	public void testReadJwksFromInputStream() {
		final var in = mock(InputStream.class);
		final var webKey = mock(WebKey.class);
		final var jwks = IuIterable.iter(webKey);
		try (final var mockJwk = mockStatic(Jwk.class)) {
			mockJwk.when(() -> Jwk.readJwks(in)).thenReturn(jwks);
			assertSame(jwks, spi.readJwks(in));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAsJwks() {
		final var jwks = mock(Iterable.class);
		final var json = mock(JsonObject.class);
		final var serializedJwks = IdGenerator.generateId();
		when(json.toString()).thenReturn(serializedJwks);
		try (final var mockJwk = mockStatic(Jwk.class)) {
			mockJwk.when(() -> Jwk.asJwks(jwks)).thenReturn(json);
			assertSame(serializedJwks, spi.asJwks(jwks));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testWriteJwks() {
		final var jwks = mock(Iterable.class);
		final var out = mock(OutputStream.class);
		try (final var mockJwk = mockStatic(Jwk.class)) {
			spi.writeJwks(jwks, out);
			mockJwk.verify(() -> Jwk.writeJwks(jwks, out));
		}
	}

	@Test
	public void testGetJwsBuilder() {
		final var algorithm = IuTest.rand(Algorithm.class);
		try (final var mockJwsBuilder = mockConstruction(JwsBuilder.class, (a, ctx) -> {
			assertSame(algorithm, ctx.arguments().get(0));
		})) {
			final var builder = spi.getJwsBuilder(algorithm);
			assertSame(mockJwsBuilder.constructed().get(0), builder);
		}
	}

	@Test
	public void testGetJweBuilder() {
		final var encryption = IuTest.rand(Encryption.class);
		try (final var mockJweBuilder = mockConstruction(JweBuilder.class, (a, ctx) -> {
			assertSame(encryption, ctx.arguments().get(0));
			assertTrue((boolean) ctx.arguments().get(1));
		})) {
			final var builder = spi.getJweBuilder(encryption, true);
			assertSame(mockJweBuilder.constructed().get(0), builder);
		}
	}

	@Test
	public void testParseJwe() {
		final var jwe = IdGenerator.generateId();
		try (final var mockJwe = mockConstruction(Jwe.class, (a, ctx) -> {
			assertSame(jwe, ctx.arguments().get(0));
		})) {
			final var parsedJwe = spi.parseJwe(jwe);
			assertSame(mockJwe.constructed().get(0), parsedJwe);
		}
	}

	@Test
	public void testParseJws() {
		final var jws = IdGenerator.generateId();
		final var webSignedPayload = mock(JwsSignedPayload.class);
		try (final var mockJwsBuilder = mockStatic(JwsBuilder.class)) {
			mockJwsBuilder.when(() -> JwsBuilder.parse(jws)).thenReturn(webSignedPayload);
			assertSame(webSignedPayload, spi.parseJws(jws));
		}
	}

	@Test
	public void testJwtBuilder() {
		try (final var mockJwtBuilder = mockConstruction(JwtBuilder.class)) {
			final var jwtBuilder = spi.getJwtBuilder();
			assertEquals(mockJwtBuilder.constructed().get(0), jwtBuilder);
		}
	}

	@Test
	public void testVerifyJwt() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		try (final var mockJwtBuilder = mockConstruction(Jwt.class, (a, ctx) -> {
			assertSame(jwt, ctx.arguments().get(0));
			assertSame(issuerKey, ctx.arguments().get(1));
		})) {
			final var jwtBuilder = spi.verifyJwt(jwt, issuerKey);
			assertEquals(mockJwtBuilder.constructed().get(0), jwtBuilder);
		}
	}

	@Test
	public void testDecryptAndVerifyJwt() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		final var audienceKey = mock(WebKey.class);
		try (final var mockJwtBuilder = mockConstruction(Jwt.class, (a, ctx) -> {
			assertSame(jwt, ctx.arguments().get(0));
			assertSame(issuerKey, ctx.arguments().get(1));
			assertSame(audienceKey, ctx.arguments().get(2));
		})) {
			final var jwtBuilder = spi.decryptAndVerifyJwt(jwt, issuerKey, audienceKey);
			assertEquals(mockJwtBuilder.constructed().get(0), jwtBuilder);
		}
	}
}
