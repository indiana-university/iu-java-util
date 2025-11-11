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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuVaultKeyedValue;

@SuppressWarnings("javadoc")
public class VaultKeyedValueTest {

	@Test
	public void testProperties() {
		Queue<IuVaultKeyedValue<?>> q = new ArrayDeque<>();
		for (int i = 0; i < 2; i++) {
			final var key = IdGenerator.generateId();
			for (int j = 0; j < 2; j++) {
				final var uri = URI.create("test:" + IdGenerator.generateId());
				final var secret = new VaultSecret(null, uri, null, null, null, null);
				for (int k = 0; k < 2; k++) {
					final var value = IdGenerator.generateId();

					final var vkv = new VaultKeyedValue<>(secret, key, value, String.class);
					assertSame(secret, vkv.getSecret());
					assertEquals(key, vkv.getKey());
					assertEquals(value, vkv.getValue());
					assertEquals(String.class, vkv.getType());
					assertEquals("VaultKeyedValue [" + key + "@VaultSecret [" + uri + "]]", vkv.toString());

					q.offer(vkv);
					q.offer(new VaultKeyedValue<>(secret, key, value, Object.class));
				}
			}
		}

		for (final var a : q)
			for (final var b : q)
				if (a == b) {
					assertNotEquals(a, new Object());
					assertEquals(a, b);
					assertEquals(a.hashCode(), b.hashCode());
				} else {
					assertNotEquals(a, b);
					assertNotEquals(b, a);
					assertNotEquals(a.hashCode(), b.hashCode());
				}
	}

}
