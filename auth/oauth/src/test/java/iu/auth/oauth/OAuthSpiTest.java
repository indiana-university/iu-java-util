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
package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.IuAuthorizationClient;

@SuppressWarnings("javadoc")
public class OAuthSpiTest {

	@Test
	public void testInitClient() {
		final var spi = new OAuthSpi();
		final var realm = IdGenerator.generateId();
		assertThrows(IllegalStateException.class, () -> OAuthSpi.getClient(realm));

		final var client = mock(IuAuthorizationClient.class);
		when(client.getRealm()).thenReturn(realm);
		final var uri = mock(URI.class);
		when(client.getResourceUri()).thenReturn(uri);
		assertThrows(IllegalArgumentException.class, () -> spi.initialize(client));

		when(uri.isOpaque()).thenReturn(true, false);
		assertThrows(IllegalArgumentException.class, () -> spi.initialize(client));

		when(uri.isAbsolute()).thenReturn(true);
		spi.initialize(client);
		assertSame(client, OAuthSpi.getClient(realm));

		assertThrows(IllegalStateException.class, () -> spi.initialize(client));
	}

	@Test
	public void testCreateAuthorizationSession() {
		final var spi = new OAuthSpi();
		final var realm = IdGenerator.generateId();
		final var entryPoint = mock(URI.class);
		try (final var mockAuthSession = mockConstruction(AuthorizationSession.class)) {
			final var authSession = spi.createAuthorizationSession(realm, entryPoint);
			assertSame(authSession, mockAuthSession.constructed().get(0));
		}
	}

	@Test
	public void testCreateAuthorizedScope() {
		final var spi = new OAuthSpi();
		final var name = IdGenerator.generateId();
		final var realm = IdGenerator.generateId();
		try (final var mockAuthScope = mockConstruction(AuthorizedScope.class)) {
			final var authSession = spi.createAuthorizationScope(name, realm);
			assertSame(authSession, mockAuthScope.constructed().get(0));
		}
	}
}
