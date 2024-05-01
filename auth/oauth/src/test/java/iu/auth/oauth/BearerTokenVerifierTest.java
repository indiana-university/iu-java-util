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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import iu.auth.principal.PrincipalVerifierRegistry;

@SuppressWarnings("javadoc")
public class BearerTokenVerifierTest extends IuOAuthTestCase {

	@Test
	public void testNoScope() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var verifier = new BearerTokenVerifier(realm, null);
		PrincipalVerifierRegistry.registerVerifier(verifier);
		final var client = mock(IuAuthorizationClient.class);
		try (final var mockSpi = mockStatic(OAuthSpi.class)) {
			mockSpi.when(() -> OAuthSpi.getClient(realm)).thenReturn(client);
			final var id = new BearerToken(realm, null, Set.of(), null, Instant.now().plusSeconds(1L));
			assertEquals(
					"Bearer realm=\"" + realm
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for principal realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id, realm)).getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testVerificationFailures() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var scope = IdGenerator.generateId();
		final var verifier = new BearerTokenVerifier(realm, scope);
		PrincipalVerifierRegistry.registerVerifier(verifier);

		final var client = mock(IuAuthorizationClient.class);
		try (final var mockSpi = mockStatic(OAuthSpi.class)) {
			mockSpi.when(() -> OAuthSpi.getClient(realm)).thenReturn(client);
			final var id = new BearerToken(realm, null, Set.of(scope), null, Instant.now().plusSeconds(1L));
			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for principal realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id, realm)).getMessage());

			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id, IdGenerator.generateId()))
							.getMessage());

			final var idrealm = IdGenerator.generateId();
			when(client.getPrincipalRealms()).thenReturn(Set.of(idrealm), Set.of(idrealm), Set.of(realm));
			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for principal realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id, realm)).getMessage());

			final var id3 = new BearerToken(idrealm, null, Set.of(scope), null, Instant.now().plusSeconds(1L));
			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for principal realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id3, realm)).getMessage());

			final var id4 = new BearerToken(realm, id, Set.of(scope), null, Instant.now().plusSeconds(120L));
			final var id5 = new BearerToken(realm, id4, Set.of(scope), null, Instant.now().plusSeconds(120L));
			assertEquals("illegal principal reference",
					assertThrows(IllegalStateException.class, () -> verifier.verify(id5, realm)).getMessage());

			final var id2 = new BearerToken(realm, null, Set.of(scope), null,
					Instant.now().truncatedTo(ChronoUnit.SECONDS));
			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Token is expired\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id2, realm)).getMessage());
		}
	}

}
