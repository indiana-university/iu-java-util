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
package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class WebCryptoHeaderTest extends IuCryptApiTestCase {

	private void assertParam(Param param, boolean encrypt, boolean sign, boolean required, String name, Object value,
			Consumer<WebCryptoHeader> setup) {
		assertEquals(encrypt, param.isUsedFor(Use.ENCRYPT));
		assertEquals(sign, param.isUsedFor(Use.SIGN));
		assertEquals(required, param.required);
		assertEquals(param, Param.from(name));

		final var header = mock(WebCryptoHeader.class, a -> null);
		assertFalse(param.isPresent(header));
		assertNull(param.get(header));

		final var headerWithValue = mock(WebCryptoHeader.class, a -> null);
		setup.accept(headerWithValue);
		assertTrue(param.isPresent(headerWithValue));

		assertEquals(value, param.get(headerWithValue));
	}

	@Test
	public void testAlgorithmParam() {
		final var alg = IuTest.rand(Algorithm.class);
		assertParam(Param.ALGORITHM, true, true, true, "alg", alg, a -> when(a.getAlgorithm()).thenReturn(alg));
	}

	@Test
	public void testKeyIdParam() {
		final var kid = IdGenerator.generateId();
		assertParam(Param.KEY_ID, true, true, false, "kid", kid, a -> when(a.getKeyId()).thenReturn(kid));
	}

	@Test
	public void testKeySetUriParam() {
		final var jku = mock(URI.class);
		assertParam(Param.KEY_SET_URI, true, true, false, "jku", jku, a -> when(a.getKeySetUri()).thenReturn(jku));
	}

	@Test
	public void testKeyParam() {
		final var jwk = mock(WebKey.class);
		assertParam(Param.KEY, true, true, false, "jwk", jwk, a -> when(a.getKey()).thenReturn(jwk));
	}

	@Test
	public void testCertificateUriParam() {
		final var x5u = mock(URI.class);
		assertParam(Param.CERTIFICATE_URI, true, true, false, "x5u", x5u,
				a -> when(a.getCertificateUri()).thenReturn(x5u));
	}

	@Test
	public void testCertificateChainParam() {
		final var cert = mock(X509Certificate.class);
		final var x5c = new X509Certificate[] { cert };
		assertParam(Param.CERTIFICATE_CHAIN, true, true, false, "x5c", x5c,
				a -> when(a.getCertificateChain()).thenReturn(x5c));
	}

	@Test
	public void testCertificateThumbprintParam() {
		final var x5t = new byte[16];
		ThreadLocalRandom.current().nextBytes(x5t);
		assertParam(Param.CERTIFICATE_THUMBPRINT, true, true, false, "x5t", x5t,
				a -> when(a.getCertificateThumbprint()).thenReturn(x5t));
	}

	@Test
	public void testCertificateSha256ThumbprintParam() {
		final var x5t256 = new byte[16];
		ThreadLocalRandom.current().nextBytes(x5t256);
		assertParam(Param.CERTIFICATE_SHA256_THUMBPRINT, true, true, false, "x5t#S256", x5t256,
				a -> when(a.getCertificateSha256Thumbprint()).thenReturn(x5t256));
	}

	@Test
	public void testTypeParam() {
		final var typ = IdGenerator.generateId();
		assertParam(Param.TYPE, true, true, false, "typ", typ, a -> when(a.getType()).thenReturn(typ));
	}

	@Test
	public void testContentTypeParam() {
		final var cty = IdGenerator.generateId();
		assertParam(Param.CONTENT_TYPE, true, true, false, "cty", cty, a -> when(a.getContentType()).thenReturn(cty));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCritParam() {
		final var crit = mock(Set.class);
		assertParam(Param.CRITICAL_PARAMS, true, true, false, "crit", crit,
				a -> when(a.getCriticalParameters()).thenReturn(crit));
	}

	@Test
	public void testEncryptionParam() {
		final var enc = IuTest.rand(Encryption.class);
		assertParam(Param.ENCRYPTION, true, false, true, "enc", enc,
				a -> when(a.getExtendedParameter("enc")).thenReturn(enc));
	}

	@Test
	public void testZipParam() {
		final var zip = "DEF";
		assertParam(Param.ZIP, true, false, false, "zip", zip,
				a -> when(a.getExtendedParameter("zip")).thenReturn(zip));
	}

	@Test
	public void testEpkParam() {
		final var epk = mock(WebKey.class);
		assertParam(Param.EPHEMERAL_PUBLIC_KEY, true, false, true, "epk", epk,
				a -> when(a.getExtendedParameter("epk")).thenReturn(epk));
	}

	@Test
	public void testApuParam() {
		final var apu = new byte[16];
		ThreadLocalRandom.current().nextBytes(apu);
		assertParam(Param.PARTY_UINFO, true, false, false, "apu", apu,
				a -> when(a.getExtendedParameter("apu")).thenReturn(apu));
	}

	@Test
	public void testApvParam() {
		final var apv = new byte[16];
		ThreadLocalRandom.current().nextBytes(apv);
		assertParam(Param.PARTY_VINFO, true, false, false, "apv", apv,
				a -> when(a.getExtendedParameter("apv")).thenReturn(apv));
	}

	@Test
	public void testIvParam() {
		final var iv = new byte[16];
		ThreadLocalRandom.current().nextBytes(iv);
		assertParam(Param.INITIALIZATION_VECTOR, true, false, true, "iv", iv,
				a -> when(a.getExtendedParameter("iv")).thenReturn(iv));
	}

	@Test
	public void testTagParam() {
		final var tag = new byte[16];
		ThreadLocalRandom.current().nextBytes(tag);
		assertParam(Param.TAG, true, false, true, "tag", tag, a -> when(a.getExtendedParameter("tag")).thenReturn(tag));
	}

	@Test
	public void testP2sParam() {
		final var p2s = new byte[16];
		ThreadLocalRandom.current().nextBytes(p2s);
		assertParam(Param.PASSWORD_SALT, true, false, true, "p2s", p2s,
				a -> when(a.getExtendedParameter("p2s")).thenReturn(p2s));
	}

	@Test
	public void testP2cParam() {
		final var p2c = ThreadLocalRandom.current().nextInt();
		assertParam(Param.PASSWORD_COUNT, true, false, true, "p2c", p2c,
				a -> when(a.getExtendedParameter("p2c")).thenReturn(p2c));
	}

	@Test
	public void testProtectedHeader() {
		final var serialized = IdGenerator.generateId();
		WebCryptoHeader.getProtectedHeader(serialized);
		verify(Init.SPI).getProtectedHeader(serialized);
	}
	
	@Test
	public void testVerifyRequiresAlgorithm() {
		final var header = mock(WebCryptoHeader.class);
		final var error = assertThrows(NullPointerException.class, () -> WebCryptoHeader.verify(header));
		assertEquals("Signature or key protection algorithm is required", error.getMessage());
	}

	@Test
	public void testVerifyRequiresEncryption() {
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.ECDH_ES);

		final var error = assertThrows(NullPointerException.class, () -> WebCryptoHeader.verify(header));
		assertEquals("Content encryption algorithm is required", error.getMessage());
	}

	@Test
	public void testVerifyECDHRequiresEpk() {
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.ECDH_ES);
		when(header.getExtendedParameter("enc")).thenReturn(Encryption.AES_128_CBC_HMAC_SHA_256);

		final var error = assertThrows(NullPointerException.class, () -> WebCryptoHeader.verify(header));
		assertEquals("Missing required encryption parameter epk", error.getMessage());
	}

	@Test
	public void testVerifyValidECDH() {
		final var epk = mock(WebKey.class);
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.ECDH_ES);
		when(header.getExtendedParameter("enc")).thenReturn(Encryption.AES_128_CBC_HMAC_SHA_256);
		when(header.getExtendedParameter("epk")).thenReturn(epk);

		assertDoesNotThrow(() -> WebCryptoHeader.verify(header));
	}

	@Test
	public void testVerifyRequiresCritRegistered() {
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.RSA_OAEP);
		when(header.getExtendedParameter("enc")).thenReturn(Encryption.A128GCM);
		when(header.getCriticalParameters()).thenReturn(Set.of("iv"));

		final var error = assertThrows(NullPointerException.class, () -> WebCryptoHeader.verify(header));
		assertEquals("Missing critical registered parameter iv", error.getMessage());
	}

	@Test
	public void testVerifyRequiresCritExtended() {
		final var ext = IdGenerator.generateId();
		final var iv = new byte[16];
		ThreadLocalRandom.current().nextBytes(iv);
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.RSA_OAEP);
		when(header.getExtendedParameter("enc")).thenReturn(Encryption.A128GCM);
		when(header.getExtendedParameter("iv")).thenReturn(iv);
		when(header.getCriticalParameters()).thenReturn(Set.of("iv", ext));

		final var error = assertThrows(NullPointerException.class, () -> WebCryptoHeader.verify(header));
		assertEquals("Missing critical extended parameter " + ext, error.getMessage());
	}

	@Test
	public void testVerifyValidWithExtended() {
		final var extName = IdGenerator.generateId();
		final var extValue = IdGenerator.generateId();
		final var iv = new byte[16];
		ThreadLocalRandom.current().nextBytes(iv);
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.RSA_OAEP);
		when(header.getExtendedParameter("enc")).thenReturn(Encryption.A128GCM);
		when(header.getExtendedParameter("iv")).thenReturn(iv);
		when(header.getExtendedParameter(extName)).thenReturn(extValue);
		when(header.getCriticalParameters()).thenReturn(Set.of("iv", extName));

		assertDoesNotThrow(() -> WebCryptoHeader.verify(header));
	}

	@Test
	public void testVerifyWellKnownNoIdOrCert() {
		final var key = mock(WebKey.class);
		when(key.wellKnown()).thenReturn(key);
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.ES256);
		when(header.getCriticalParameters()).thenReturn(null);
		when(header.getKey()).thenReturn(key);

		assertDoesNotThrow(() -> WebCryptoHeader.verify(header));
	}

	@Test
	public void testVerifyWellKnownIdRefNoCert() {
		final var keyId = IdGenerator.generateId();
		final var keySetUri = mock(URI.class);
		final var key = mock(WebKey.class);
		when(key.getKeyId()).thenReturn(keyId);
		when(key.wellKnown()).thenReturn(key);
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.ES256);
		when(header.getCriticalParameters()).thenReturn(null);
		when(header.getKeyId()).thenReturn(keyId);
		when(header.getKeySetUri()).thenReturn(keySetUri);

		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.readJwks(keySetUri)).thenReturn(IuIterable.iter(key));
			assertDoesNotThrow(() -> WebCryptoHeader.verify(header));
		}
	}

	@Test
	public void testVerifyWellKnownIdCert() {
		final var pubkey = mock(PublicKey.class);
		final var cert = mock(X509Certificate.class);
		when(cert.getPublicKey()).thenReturn(pubkey);
		final var key = mock(WebKey.class);
		when(key.getPublicKey()).thenReturn(pubkey);
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.ES256);
		when(header.getCriticalParameters()).thenReturn(null);
		when(header.getKey()).thenReturn(key);

		try (final var mockWebKey = mockStatic(WebKey.class);
				final var mockWebCertificateReference = mockStatic(WebCertificateReference.class)) {
			final var keyBuilder = mock(WebKey.Builder.class);
			when(keyBuilder.cert(any(X509Certificate[].class))).thenReturn(keyBuilder);
			when(keyBuilder.build()).thenReturn(key);
			mockWebKey.when(() -> WebKey.builder(WebKey.Type.EC_P256)).thenReturn(keyBuilder);
			mockWebCertificateReference.when(() -> WebCertificateReference.verify(header))
					.thenReturn(new X509Certificate[] { cert });
			assertDoesNotThrow(() -> WebCryptoHeader.verify(header));
			verify(keyBuilder).cert(cert);
		}
	}

}
