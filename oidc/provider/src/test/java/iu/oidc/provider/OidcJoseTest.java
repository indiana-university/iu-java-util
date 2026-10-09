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
package iu.oidc.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class OidcJoseTest {

	static {
		edu.iu.crypt.Init.init();
	}

	/** Stands in for whatever rendered the claims. */
	private static final String CLAIMS = "{\"sub\":\"someone\"}";

	private static WebKey issuerKey(String keyId) {
		final var builder = WebKey.builder(Algorithm.ES256);
		if (keyId != null)
			builder.keyId(keyId);
		return builder.ephemeral().build();
	}

	private static WebKey audienceKey(String keyId) {
		final var builder = WebKey.builder(Algorithm.ECDH_ES);
		if (keyId != null)
			builder.keyId(keyId);
		return builder.ephemeral().build();
	}

	/** Reads the one signature header off a JWS compact serialization. */
	private static WebCryptoHeader header(String jws) {
		return WebSignedPayload.parse(jws).getSignatures().iterator().next().getHeader();
	}

	/** Answers a key that declares no algorithm at all. */
	private static WebKey keyWithNoAlgorithm() {
		return mock(WebKey.class);
	}

	@Test
	void testSignsWithTheIssuerKeysOwnAlgorithm() {
		final var issuerKey = issuerKey(null);
		final var signed = OidcJose.sign(CLAIMS, "JWT", issuerKey);

		final var jws = WebSignedPayload.parse(signed);
		assertEquals(CLAIMS, IuText.utf8(jws.getPayload()));
		assertDoesNotThrow(() -> jws.verify(issuerKey));

		final var header = header(signed);
		assertEquals(Algorithm.ES256, header.getAlgorithm());
		assertEquals("JWT", header.getType());
	}

	@Test
	void testTheSignatureNamesTheIssuerKey() {
		final var keyId = IdGenerator.generateId();
		assertEquals(keyId, header(OidcJose.sign(CLAIMS, "JWT", issuerKey(keyId))).getKeyId());
	}

	@Test
	void testAnIssuerKeyWithNoIdNamesNone() {
		assertNull(header(OidcJose.sign(CLAIMS, "JWT", issuerKey(null))).getKeyId());
	}

	@Test
	void testTheSignatureCarriesNoCertificate() {
		// a relying party resolves the key through the published JWK Set by kid, so a
		// chain in the header names a trust path nothing asks anyone to follow
		final var header = header(OidcJose.sign(CLAIMS, "JWT", issuerKey(IdGenerator.generateId())));
		assertNull(header.getCertificateThumbprint());
		assertNull(header.getCertificateChain());
	}

	@Test
	void testAnIssuerKeyMustDeclareAnAlgorithm() {
		// the provider selects an issuer key by algorithm, so one without an algorithm
		// is a configuration fault rather than something to guess at
		final var issuerKey = keyWithNoAlgorithm();
		assertEquals("Missing issuer key algorithm", assertThrows(NullPointerException.class, //
				() -> OidcJose.sign(CLAIMS, "JWT", issuerKey)).getMessage());
	}

	@Test
	void testSignsThenEncrypts() {
		final var issuerKey = issuerKey(IdGenerator.generateId());
		final var audienceKey = audienceKey(null);

		final var encrypted = OidcJose.signAndEncrypt(CLAIMS, "JWT", issuerKey, audienceKey,
				Encryption.AES_128_CBC_HMAC_SHA_256);

		final var jwe = WebEncryption.parse(encrypted);
		assertEquals("JWT", jwe.getRecipients().iterator().next().getHeader().getContentType());

		// the signature is inside the encryption, so the audience authenticates the
		// claims only after decrypting
		IuTestLogger.allow("iu.crypt", Level.FINE);
		final var jws = WebSignedPayload.parse(jwe.decryptText(audienceKey));
		assertEquals(CLAIMS, IuText.utf8(jws.getPayload()));
		assertDoesNotThrow(() -> jws.verify(issuerKey));
	}

	@Test
	void testTheEncryptionNamesTheAudienceKey() {
		final var keyId = IdGenerator.generateId();
		final var encrypted = OidcJose.signAndEncrypt(CLAIMS, "JWT", issuerKey(null), audienceKey(keyId),
				Encryption.AES_128_CBC_HMAC_SHA_256);

		assertEquals(keyId, WebEncryption.parse(encrypted).getRecipients().iterator().next().getHeader().getKeyId());
	}

	@Test
	void testAnAudienceKeyWithNoIdNamesNone() {
		final var encrypted = OidcJose.signAndEncrypt(CLAIMS, "JWT", issuerKey(null), audienceKey(null),
				Encryption.AES_128_CBC_HMAC_SHA_256);

		assertNull(WebEncryption.parse(encrypted).getRecipients().iterator().next().getHeader().getKeyId());
	}

	@Test
	void testAnAudienceKeyMustDeclareAnAlgorithm() {
		final var issuerKey = issuerKey(null);
		final var audienceKey = keyWithNoAlgorithm();
		assertEquals("Missing audience key algorithm", assertThrows(NullPointerException.class, //
				() -> OidcJose.signAndEncrypt(CLAIMS, "JWT", issuerKey, audienceKey,
						Encryption.AES_128_CBC_HMAC_SHA_256)).getMessage());
	}

}
