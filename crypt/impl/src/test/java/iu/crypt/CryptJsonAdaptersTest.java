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
package iu.crypt;

import static iu.crypt.CryptJsonAdapters.JSONB;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.X509CertificateAuthority;
import jakarta.json.JsonString;

@SuppressWarnings("javadoc")
public class CryptJsonAdaptersTest {

	@Test
	public void testCert() {
		final var cert = mock(X509Certificate.class);
		final var encoded = new byte[16];
		ThreadLocalRandom.current().nextBytes(encoded);
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));
		assertEquals(IuText.base64(encoded), ((JsonString) IuJson.parse(JSONB.toJson(cert))).getString());
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCertificate(encoded)).thenReturn(cert);
			assertEquals(cert, JSONB.fromJson(JSONB.toJson(cert), X509Certificate.class));
		}
	}

	@Test
	public void testBigInt() {
		final var binary = new byte[128];
		ThreadLocalRandom.current().nextBytes(binary);
		final var bigInt = new BigInteger(1, binary);
		assertEquals(bigInt, JSONB.fromJson(JSONB.toJson(bigInt), BigInteger.class));
	}

	@Test
	public void testCrl() {
		final var crl = mock(X509CRL.class);
		final var encoded = new byte[16];
		ThreadLocalRandom.current().nextBytes(encoded);
		assertDoesNotThrow(() -> when(crl.getEncoded()).thenReturn(encoded));
		assertEquals(IuText.base64(encoded), ((JsonString) IuJson.parse(JSONB.toJson(crl))).getString());
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCRL(encoded)).thenReturn(crl);
			assertEquals(crl, JSONB.fromJson(JSONB.toJson(crl), X509CRL.class));
		}
	}

	@Test
	public void testUse() {
		for (final var use : Use.values()) {
			final var json = JSONB.toJson(use);
			assertEquals(use.use, ((JsonString) IuJson.parse(json)).getString());
			assertEquals(use, JSONB.fromJson(json, Use.class));
		}
	}

	@Test
	public void testOp() {
		for (final var op : Operation.values()) {
			final var json = JSONB.toJson(op);
			assertEquals(op.keyOp, ((JsonString) IuJson.parse(json)).getString());
			assertEquals(op, JSONB.fromJson(json, Operation.class));
		}
	}

	@Test
	public void testAlg() {
		for (final var alg : Algorithm.values()) {
			final var json = JSONB.toJson(alg);
			assertEquals(alg.alg, ((JsonString) IuJson.parse(json)).getString());
			assertEquals(alg, JSONB.fromJson(json, Algorithm.class));
		}
	}

	@Test
	public void testEnc() {
		for (final var enc : Encryption.values()) {
			final var json = JSONB.toJson(enc);
			assertEquals(enc.enc, ((JsonString) IuJson.parse(json)).getString());
			assertEquals(enc, JSONB.fromJson(json, Encryption.class));
		}
	}

	@Test
	public void testWebKey() {
		final var jwk = "{\"kty\":\"oct\"}";
		final var key = JSONB.fromJson(jwk, WebKey.class);
		assertEquals(jwk, JSONB.toJson(key));
	}

	@Test
	public void testWebCryptoHeader() {
		final var jose = "{\"alg\":\"ES256\"}";
		final var key = JSONB.fromJson(jose, WebCryptoHeader.class);
		assertEquals(jose, JSONB.toJson(key));
	}

	@Test
	public void testOfCertificateChain() {
		final var cert = mock(X509Certificate.class);
		final var encoded = IuText.utf8(IdGenerator.generateId());
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encoded));
		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCertificate(encoded)).thenReturn(cert);
			final var chain = new X509Certificate[] { cert };
			assertArrayEquals(chain, (X509Certificate[]) JSONB.fromJson(JSONB.toJson(chain), X509Certificate[].class));
		}
	}

	@Test
	public void testCa() {
		final var jwk = WebKey.ephemeral(Algorithm.ES256);
		final var database = new byte[128];
		ThreadLocalRandom.current().nextBytes(database);
		final var cert = mock(X509Certificate.class);
		final var crl = mock(X509CRL.class);

		final var encodedCert = IuText.utf8(IdGenerator.generateId());
		final var encodedCrl = IuText.utf8(IdGenerator.generateId());
		assertDoesNotThrow(() -> when(cert.getEncoded()).thenReturn(encodedCert));
		assertDoesNotThrow(() -> when(crl.getEncoded()).thenReturn(encodedCrl));

		final var builder = CryptJsonAdapters.builder();
		builder.put("jwk", jwk);
		builder.put("database", database);
		builder.put("certificates", new X509Certificate[] { cert });
		builder.put("crl", new X509CRL[] { crl });
		final var json = JSONB.toJson(builder.build());

		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
			mockPemEncoded.when(() -> PemEncoded.asCertificate(encodedCert)).thenReturn(cert);
			mockPemEncoded.when(() -> PemEncoded.asCRL(encodedCrl)).thenReturn(crl);

			final var ca = JSONB.fromJson(json, X509CertificateAuthority.class);
			assertEquals(jwk, ca.getJwk());
			assertArrayEquals(database, ca.getDatabase());
			assertEquals(cert, ca.getCertificates().iterator().next());
			assertEquals(crl, ca.getCrl().iterator().next());

			assertEquals(json, JSONB.toJson(ca));
		}
	}

//	@Test
//	public void testCaFromJsonPropertyGetters() {
//		final var keyData = new byte[32];
//		final var database = new byte[128];
//		final var certData = new byte[64];
//		final var crlData = new byte[64];
//		ThreadLocalRandom.current().nextBytes(keyData);
//		ThreadLocalRandom.current().nextBytes(database);
//		ThreadLocalRandom.current().nextBytes(certData);
//		ThreadLocalRandom.current().nextBytes(crlData);
//		final var cert = mock(X509Certificate.class);
//		final var crl = mock(X509CRL.class);
//		final var json = IuJson.object() //
//				.add("jwk", IuJson.object().add("kty", "oct").add("k", IuText.base64Url(keyData))) //
//				.add("database", IuText.base64Url(database)) //
//				.add("certificates", IuJson.array().add(IuText.base64(certData))) //
//				.add("crl", IuJson.array().add(IuText.base64(crlData))) //
//				.build();
//
//		try (final var mockPemEncoded = mockStatic(PemEncoded.class)) {
//			mockPemEncoded.when(() -> PemEncoded.asCertificate(certData)).thenReturn(cert);
//			mockPemEncoded.when(() -> PemEncoded.asCRL(crlData)).thenReturn(crl);
//
//			final var ca = CryptJsonAdapters.CA.fromJson(json);
//			assertEquals(WebKey.Type.RAW, ca.getJwk().getType());
//			assertArrayEquals(keyData, ca.getJwk().getKey());
//			assertArrayEquals(database, ca.getDatabase());
//			assertSame(cert, ca.getCertificates().iterator().next());
//			assertSame(crl, ca.getCrl().iterator().next());
//		}
//	}

}
