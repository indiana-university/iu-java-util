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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class JweBuilderTest {
	
	@Test
	public void testCompactLimits() {
		final var b = new JweBuilder(Encryption.AES_192_CBC_HMAC_SHA_384, false);
		b.compact();
		assertThrows(NullPointerException.class, () -> b.encrypt("foo"));

		b.addRecipient(Algorithm.ECDH_ES).key(WebKey.ephemeral(Algorithm.ECDH_ES));
		assertDoesNotThrow(() -> b.encrypt("foo"));

		b.aad(new byte[] { 1, 2, 3 });
		assertThrows(IllegalArgumentException.class, () -> b.encrypt("foo"));

		b.addRecipient(Algorithm.RSA_OAEP).key(WebKey.ephemeral(Algorithm.RSA_OAEP));
		assertThrows(IllegalArgumentException.class, () -> b.encrypt("foo"));
	}

	@Test
	public void testHangingRecipients() {
		final var k1 = WebKey.builder(Type.X448).algorithm(Algorithm.ECDH_ES_A192KW).ephemeral().build();
		final var k2 = WebKey.builder(Type.X448).algorithm(Algorithm.ECDH_ES_A192KW).ephemeral().build();

		final var id = IdGenerator.generateId();
		final var jweBuilder = WebEncryption.builder(Encryption.A128GCM);
		jweBuilder.addRecipient(Algorithm.ECDH_ES_A192KW).key(k1);
		jweBuilder.addRecipient(Algorithm.ECDH_ES_A192KW).key(k2);
		final var jwe = jweBuilder.encrypt(id);

		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE, "CEK decryption successful.*");
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE, "CEK decryption failed");
		final var ewj = WebEncryption.parse(jwe.toString());
		assertEquals(id, ewj.decryptText(k1));
		assertEquals(id, ewj.decryptText(k2));
	}
}
