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
package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey;

@SuppressWarnings("javadoc")
public class JwkPrincipalTest {

	@Test
	public void testDelegation() {
		final var uri = URI.create("test:" + IdGenerator.generateId());
		final var keyId = IdGenerator.generateId();
		final var jwkId = new JwkPrincipal(uri, keyId);
		assertEquals(uri + "#" + keyId, jwkId.getName());

		try (final var mockJwk = mockStatic(WebKey.class)) {
			final var key = mock(WebKey.class);
			when(key.getKeyId()).thenReturn(keyId);
			when(key.wellKnown()).thenReturn(key);
			mockJwk.when(() -> WebKey.readJwks(uri)).thenReturn(Set.of(key));
			final var subject = jwkId.getSubject();
			assertEquals(Set.of(jwkId), subject.getPrincipals());
			assertEquals(Set.of(key), subject.getPublicCredentials());
		}
	}

	@Test
	public void testRejectNonWellKnown() {
		final var keyId = IdGenerator.generateId();
		final var uri = URI.create("test:" + IdGenerator.generateId() + "#" + keyId);
		final var jwkId = new JwkPrincipal(uri, null);
		assertEquals(uri.toString(), jwkId.getName());

		try (final var mockJwk = mockStatic(WebKey.class)) {
			final var wellKnown = mock(WebKey.class);
			final var key = mock(WebKey.class);
			when(key.getKeyId()).thenReturn(keyId);
			when(key.wellKnown()).thenReturn(wellKnown);
			mockJwk.when(() -> WebKey.readJwks(uri)).thenReturn(Set.of(key));
			assertThrows(IllegalStateException.class, jwkId::getSubject);
		}
	}

}
