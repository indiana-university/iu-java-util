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
package iu.auth.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class TokenIssuerKeySetTest {

	@Test
	public void testRsa() throws Exception {
		final var id = IdGenerator.generateId();
		final var rsaKeygen = KeyPairGenerator.getInstance("RSA");
		rsaKeygen.initialize(1024);
		final var keyPair = rsaKeygen.generateKeyPair();
		final var issuerKey = WebKey.builder(Type.RSA).keyId(id).use(Use.ENCRYPT).key(keyPair).build();

		final var issuerKeySet = new TokenIssuerKeySet(Set.of(issuerKey));
		assertThrows(NoSuchElementException.class, () -> issuerKeySet.getKey("bar"));
		assertNotNull(issuerKeySet.getKey(id));
		assertEquals(id, Json.createReader(new StringReader(WebKey.asJwks(Set.of(issuerKey)))).readObject()
				.getJsonArray("keys").getJsonObject(0).getString("kid"));
	}

	@Test
	public void testEC256() throws Exception {
		for (final var type : EnumSet.of(Type.EC_P256, Type.EC_P384, Type.EC_P521)) {
			final var id = IdGenerator.generateId();
			final var ecKeygen = KeyPairGenerator.getInstance("EC");
			ecKeygen.initialize(new ECGenParameterSpec(type.algorithmParams));
			final var keyPair = ecKeygen.generateKeyPair();
			final var issuerKey = WebKey.builder(type).keyId(id).use(Use.SIGN).key(keyPair).build();

			final var issuerKeySet = new TokenIssuerKeySet(Set.of(issuerKey));
			assertThrows(NoSuchElementException.class, () -> issuerKeySet.getKey("bar"));
			assertNotNull(issuerKeySet.getKey(id));
			assertEquals(id, Json.createReader(new StringReader(WebKey.asJwks(Set.of(issuerKey)))).readObject()
					.getJsonArray("keys").getJsonObject(0).getString("kid"));
		}
	}

}
