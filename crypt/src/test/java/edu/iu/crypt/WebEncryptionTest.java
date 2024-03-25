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
package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class WebEncryptionTest {

	@Test
	@SuppressWarnings("deprecation")
	public void testRsaLegacy() throws NoSuchAlgorithmException {
		final var key = WebKey.builder().algorithm(Algorithm.RSA1_5).ephemeral().build();
		final var message = IdGenerator.generateId();

		final var jwe = WebEncryption.builder().enc(Encryption.AES_128_CBC_HMAC_SHA_256).addRecipient()
				.algorithm(Algorithm.RSA1_5).jwk(key, false).then().encrypt(message);
		assertNull(jwe.getAdditionalData());

		final var fromCompact = WebEncryption.parse(jwe.compact());

		final var compactHeader = fromCompact.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.RSA1_5, compactHeader.getAlgorithm());
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, fromCompact.getEncryption());
		assertNull(compactHeader.getKey());

		final var fromSerial = WebEncryption.parse(jwe.toString());
		final var serialHeader = fromSerial.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.RSA1_5, serialHeader.getAlgorithm());
		assertEquals(Encryption.AES_128_CBC_HMAC_SHA_256, fromSerial.getEncryption());
		assertNotNull(serialHeader.getKey());
		assertNull(serialHeader.getKey().getPrivateKey());

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(jwe.decrypt(key)));

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(fromCompact.decrypt(key)));

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(fromSerial.decrypt(key)));
	}

	@Test
	public void testEcdhGcm() throws NoSuchAlgorithmException {
		final var key = WebKey.builder().algorithm(Algorithm.ECDH_ES).id("a").ephemeral().build();
		final var message = IdGenerator.generateId();

		final var jwe = WebEncryption.builder().enc(Encryption.A128GCM).addRecipient().algorithm(Algorithm.ECDH_ES)
				.jwk(key, false).then().encrypt(message);
		assertNull(jwe.getAdditionalData());

		final var fromCompact = WebEncryption.parse(jwe.compact());

		final var compactHeader = fromCompact.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.ECDH_ES, compactHeader.getAlgorithm());
		assertEquals(Encryption.A128GCM, fromCompact.getEncryption());
		assertNull(compactHeader.getKey());

		final var fromSerial = WebEncryption.parse(jwe.toString());
		final var serialHeader = fromSerial.getRecipients().iterator().next().getHeader();
		assertEquals(Algorithm.ECDH_ES, serialHeader.getAlgorithm());
		assertEquals(Encryption.A128GCM, fromSerial.getEncryption());
		assertNotNull(serialHeader.getKey());
		assertNull(serialHeader.getKey().getPrivateKey());

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(jwe.decrypt(key)));

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(jwe.decrypt(key)));

		IuTestLogger.expect("iu.crypt.Jwe", Level.FINE, "CEK decryption successful for " + key.wellKnown());
		assertEquals(message, new String(jwe.decrypt(key)));
	}
}
