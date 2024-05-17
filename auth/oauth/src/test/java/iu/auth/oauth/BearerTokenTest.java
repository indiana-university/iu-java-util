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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;

@SuppressWarnings("javadoc")
public class BearerTokenTest {

	@Test
	public void testAccessTokenOnly() throws IuAuthenticationException, InterruptedException {
		final var realm = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var clientCredentials = new MockClientCredentials();

		final var client = mock(IuAuthorizationClient.class);
		when(client.getCredentials()).thenReturn(clientCredentials);

		try (final var mockSpi = mockStatic(OAuthSpi.class)) {
			mockSpi.when(() -> OAuthSpi.getClient(realm)).thenReturn(client);
			final var auth = new BearerToken(realm, null, Set.of(), accessToken, Instant.now().plusSeconds(1L));
			assertNotNull(auth.toString());
			assertEquals(accessToken, auth.getAccessToken());
			assertSame(auth, auth.getSubject().getPrincipals().iterator().next());
			assertEquals(clientCredentials.getName(), auth.getName());
			assertEquals(Set.of(), auth.getScope());

			final var req = mock(HttpRequest.Builder.class);
			auth.applyTo(req);
			verify(req).header("Authorization", "Bearer " + accessToken);

			Thread.sleep(1000L);
			assertThrows(IllegalStateException.class, () -> auth.applyTo(req));
		}
	}

	@Test
	public void testAccessToken() throws IuAuthenticationException, InterruptedException {
		final var realm = IdGenerator.generateId();
		MockPrincipal.registerVerifier(realm);
		final var accessToken = IdGenerator.generateId();
		final var principal = new MockPrincipal(realm);

		final var auth = new BearerToken(realm, principal, Set.of(), accessToken, Instant.now().plusSeconds(1L));
		assertNotNull(auth.toString());
		assertEquals(accessToken, auth.getAccessToken());
		assertSame(principal, auth.getSubject().getPrincipals().iterator().next());
		assertEquals(principal.getName(), auth.getName());
		assertEquals(Set.of(), auth.getScope());

		final var req = mock(HttpRequest.Builder.class);
		auth.applyTo(req);
		verify(req).header("Authorization", "Bearer " + accessToken);

		Thread.sleep(1000L);
		assertThrows(IllegalStateException.class, () -> auth.applyTo(req));
	}

	@Test
	public void testEquals() throws IuAuthenticationException {
		final var t = IdGenerator.generateId();
		final List<BearerToken> creds = new ArrayList<>();
		for (final var realm : List.of(IdGenerator.generateId(), IdGenerator.generateId()))
			for (final var p : List.of(new MockPrincipal(realm), new MockPrincipal(realm)))
				for (final var s : List.of(Set.of("foo"), Set.of("bar")))
					creds.add(new BearerToken(realm, p, s, t, Instant.now().plusSeconds(1L)));
		for (int i = 0; i < creds.size(); i++)
			for (int j = 0; j < creds.size(); j++) {
				final var ai = creds.get(i);
				final var aj = creds.get(j);
				if (i == j) {
					assertEquals(ai, aj);
					assertNotEquals(ai, new Object());
				} else {
					assertNotEquals(ai, aj);
					assertNotEquals(aj, ai);
					assertNotEquals(ai.hashCode(), aj.hashCode());
				}
			}
	}

}
