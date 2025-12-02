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
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class JweRecipientTest {

	@Test
	public void testPasswordSaltTooShort() {
		final var jose = new Jose(IuJson.object() //
				.add("alg", Algorithm.PBES2_HS256_A128KW.alg) //
				.add("enc", Encryption.A128GCM.enc) //
				.add("p2c", 1000) //
				.add("p2s", CryptJsonAdapters.B64URL.toJson("foo".getBytes())) //
				.build());
		final var jweRecipient = new JweRecipient(jose, null);
		final var password = IdGenerator.generateId();
		assertThrows(IllegalArgumentException.class, () -> jweRecipient.passphraseDerivedKey(password));
	}

	@Test
	public void testPasswordCountTooLow() {
		final var jose = new Jose(IuJson.object() //
				.add("alg", Algorithm.PBES2_HS256_A128KW.alg) //
				.add("enc", Encryption.A128GCM.enc) //
				.add("p2c", 4) //
				.add("p2s", CryptJsonAdapters.B64URL.toJson(IdGenerator.generateId().getBytes())) //
				.build());
		final var jweRecipient = new JweRecipient(jose, null);
		final var password = IdGenerator.generateId();
		assertThrows(IllegalArgumentException.class, () -> jweRecipient.passphraseDerivedKey(password));
	}

}
