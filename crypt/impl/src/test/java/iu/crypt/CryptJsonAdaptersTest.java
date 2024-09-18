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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.net.URI;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class CryptJsonAdaptersTest {

	@Test
	public void testCert() {
		final var adapter = CryptJsonAdapters.CERT;
		final var cert = mock(X509Certificate.class);
		final var encoded = new byte[16];
		ThreadLocalRandom.current().nextBytes(encoded);
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));
		assertEquals(IuText.base64(encoded), ((JsonString) adapter.toJson(cert)).getString());
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCertificate(encoded)).thenReturn(cert);
			assertEquals(cert, adapter.fromJson(adapter.toJson(cert)));
		}
	}

	@Test
	public void testBigInt() {
		final var adapter = CryptJsonAdapters.BIGINT;
		final var binary = new byte[128];
		ThreadLocalRandom.current().nextBytes(binary);
		final var bigInt = new BigInteger(1, binary);
		assertEquals(bigInt, adapter.fromJson(adapter.toJson(bigInt)));
	}

	@Test
	public void testCrl() {
		final var adapter = CryptJsonAdapters.CRL;
		final var crl = mock(X509CRL.class);
		final var encoded = new byte[16];
		ThreadLocalRandom.current().nextBytes(encoded);
		assertDoesNotThrow(() -> when(crl.getEncoded()).thenReturn(encoded));
		assertEquals(IuText.base64(encoded), ((JsonString) adapter.toJson(crl)).getString());
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCRL(encoded)).thenReturn(crl);
			assertEquals(crl, adapter.fromJson(adapter.toJson(crl)));
		}
	}

	@Test
	public void testUse() {
		final var adapter = CryptJsonAdapters.USE;
		for (final var use : Use.values())
			assertEquals(use, adapter.fromJson(adapter.toJson(use)));
	}

	@Test
	public void testOp() {
		final var adapter = CryptJsonAdapters.OP;
		for (final var op : Operation.values())
			assertEquals(op, adapter.fromJson(adapter.toJson(op)));
	}

	@Test
	public void testAlg() {
		final var adapter = CryptJsonAdapters.ALG;
		for (final var alg : Algorithm.values())
			assertEquals(alg, adapter.fromJson(adapter.toJson(alg)));
	}

	@Test
	public void testEnc() {
		final var adapter = CryptJsonAdapters.ENC;
		for (final var enc : Encryption.values())
			assertEquals(enc, adapter.fromJson(adapter.toJson(enc)));
	}

	@Test
	public void testWebKeyFromJson() {
		final var adapter = CryptJsonAdapters.WEBKEY;
		final var jwk = mock(JsonObject.class);
		when(jwk.asJsonObject()).thenReturn(jwk);
		try (final var mockJwk = mockConstruction(Jwk.class, (a, ctx) -> {
			assertEquals(jwk, ctx.arguments().get(0));
		})) {
			final var key = adapter.fromJson(jwk);
			assertSame(key, mockJwk.constructed().get(0));
		}
	}

	@Test
	public void testWebKeyToJson() {
		final var adapter = CryptJsonAdapters.WEBKEY;
		final var jwk = mock(Jwk.class);
		final var jsonBuilder = mock(JsonObjectBuilder.class);
		final var json = mock(JsonObject.class);
		when(jsonBuilder.build()).thenReturn(json);
		when(json.asJsonObject()).thenReturn(json);
		try (final var mockIuJson = mockStatic(IuJson.class)) {
			mockIuJson.when(() -> IuJson.object()).thenReturn(jsonBuilder);
			assertSame(json, adapter.toJson(jwk));
			verify(jwk).serializeTo(jsonBuilder);
		}
	}

	@Test
	public void testJoseFromJson() {
		final var adapter = CryptJsonAdapters.JOSE;
		final var json = mock(JsonValue.class);
		try (final var mockJose = mockConstruction(Jose.class, (a, ctx) -> {
			assertEquals(json, ctx.arguments().get(0));
		})) {
			final var jose = adapter.fromJson(json);
			assertSame(jose, mockJose.constructed().get(0));
		}
	}

	@Test
	public void testJoseToJson() {
		final var adapter = CryptJsonAdapters.JOSE;
		final var jose = mock(Jose.class);
		final var json = mock(JsonObject.class);
		when(jose.toJson(argThat(a -> {
			assertTrue(a.test(IdGenerator.generateId()));
			return true;
		}))).thenReturn(json);
		assertSame(json, adapter.toJson(jose));
	}

	@Test
	public void testOfParams() {
		assertSame(CryptJsonAdapters.ALG, CryptJsonAdapters.of(Param.ALGORITHM));
		assertSame(CryptJsonAdapters.B64URL, CryptJsonAdapters.of(Param.CERTIFICATE_THUMBPRINT));
		assertSame(CryptJsonAdapters.B64URL, CryptJsonAdapters.of(Param.CERTIFICATE_SHA256_THUMBPRINT));
		assertSame(CryptJsonAdapters.B64URL, CryptJsonAdapters.of(Param.INITIALIZATION_VECTOR));
		assertSame(CryptJsonAdapters.B64URL, CryptJsonAdapters.of(Param.PARTY_UINFO));
		assertSame(CryptJsonAdapters.B64URL, CryptJsonAdapters.of(Param.PARTY_VINFO));
		assertSame(CryptJsonAdapters.B64URL, CryptJsonAdapters.of(Param.PASSWORD_SALT));
		assertSame(CryptJsonAdapters.B64URL, CryptJsonAdapters.of(Param.TAG));
		assertSame(CryptJsonAdapters.ENC, CryptJsonAdapters.of(Param.ENCRYPTION));
		assertSame(CryptJsonAdapters.WEBKEY, CryptJsonAdapters.of(Param.KEY));
		assertSame(IuJsonAdapter.of(URI.class), CryptJsonAdapters.of(Param.CERTIFICATE_URI));
		assertSame(IuJsonAdapter.of(URI.class), CryptJsonAdapters.of(Param.KEY_SET_URI));
		assertSame(IuJsonAdapter.of(Integer.class), CryptJsonAdapters.of(Param.PASSWORD_COUNT));
		assertSame(IuJsonAdapter.of(String.class), CryptJsonAdapters.of(Param.CONTENT_TYPE));
		assertSame(IuJsonAdapter.of(String.class), CryptJsonAdapters.of(Param.KEY_ID));
		assertSame(IuJsonAdapter.of(String.class), CryptJsonAdapters.of(Param.TYPE));
		assertSame(IuJsonAdapter.of(String.class), CryptJsonAdapters.of(Param.ZIP));
	}

	@Test
	public void testOfCertificateChain() {
		final var adapter = CryptJsonAdapters.of(Param.CERTIFICATE_CHAIN);
		final var cert = mock(X509Certificate.class);
		final var encoded = IuText.utf8(IdGenerator.generateId());
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCertificate(encoded)).thenReturn(cert);
			final var chain = new X509Certificate[] { cert };
			assertArrayEquals(chain, (X509Certificate[]) adapter.fromJson(adapter.toJson(chain)));
		}
	}

	@Test
	public void testOfCriticalParams() {
		final var adapter = CryptJsonAdapters.of(Param.CRITICAL_PARAMS);
		final var crit = Set.of(IdGenerator.generateId());
		assertEquals(crit, adapter.fromJson(adapter.toJson(crit)));
	}

}