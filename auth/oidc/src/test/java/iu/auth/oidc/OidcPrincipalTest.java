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
package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class OidcPrincipalTest extends IuOidcTestCase {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testSubjectAndClaims() throws InterruptedException {
		final var provider = mock(OpenIdProvider.class);

		final var client = mock(IuOpenIdClient.class);
		final var realm = IdGenerator.generateId();
		when(client.getRealm()).thenReturn(realm);
		when(client.getVerificationInterval()).thenReturn(Duration.ofMillis(250L));
		when(provider.client()).thenReturn(client);

		final var idToken = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final Map claims = Map.of("principal", name);
		when(provider.getClaims(idToken, accessToken)).thenReturn(claims);

		final var principal = new OidcPrincipal(idToken, accessToken, provider);
		verify(provider).getClaims(idToken, accessToken);
		assertEquals(name, principal.getName());
		assertEquals(realm, principal.realm());

		assertEquals("OIDC Principal ID [" + name + "; " + realm + "] {principal=" + name + "}", principal.toString());

		final var subject = principal.getSubject();
		assertSame(principal, subject.getPrincipals().iterator().next());
		assertEquals(1, subject.getPrincipals().size());
		assertTrue(subject.getPrivateCredentials().isEmpty());
		assertTrue(subject.getPublicCredentials().isEmpty());

		try (final var mockSpi = mockStatic(OpenIdConnectSpi.class)) {
			mockSpi.when(() -> OpenIdConnectSpi.getProvider(realm)).thenReturn(provider);
			assertEquals(claims, principal.getClaims());
			verify(provider).getClaims(idToken, accessToken);
			Thread.sleep(250L);
			assertEquals(claims, principal.getClaims());
			verify(provider, times(2)).getClaims(idToken, accessToken);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEquals() throws InterruptedException {
		final List<OidcPrincipal> principals = new ArrayList<>();
		final var names = List.of(IdGenerator.generateId(), IdGenerator.generateId());
		final var realms = List.of(IdGenerator.generateId(), IdGenerator.generateId());
		for (final var realm : realms) {
			final var provider = mock(OpenIdProvider.class);
			final var client = mock(IuOpenIdClient.class);
			when(client.getRealm()).thenReturn(realm);
			when(provider.client()).thenReturn(client);

			for (final var name : names) {
				final var idToken = IdGenerator.generateId();
				final var accessToken = IdGenerator.generateId();
				final Map claims = Map.of("principal", name);
				when(provider.getClaims(idToken, accessToken)).thenReturn(claims);
				principals.add(new OidcPrincipal(idToken, accessToken, provider));
			}
		}

		for (var i = 0; i < principals.size(); i++)
			for (var j = 0; j < principals.size(); j++) {
				final var pi = principals.get(i);
				final var pj = principals.get(j);
				if (i == j) {
					assertNotEquals(pi, new Object());
					assertEquals(pi, pj);
					assertEquals(pi.hashCode(), pj.hashCode());
				} else {
					assertNotEquals(pi, pj);
					assertNotEquals(pj, pi);
					assertNotEquals(pi.hashCode(), pj.hashCode());
				}
			}
	}

//	@SuppressWarnings({ "rawtypes", "unchecked" })
//	@Test
//	public void testRevoke() throws InterruptedException {
//		final var provider = mock(OpenIdProvider.class);
//
//		final var client = mock(IuOpenIdClient.class);
//		final var realm = IdGenerator.generateId();
//		when(client.getRealm()).thenReturn(realm);
//		when(client.getVerificationInterval()).thenReturn(Duration.ofMillis(250L));
//		when(provider.client()).thenReturn(client);
//
//		final var idToken = IdGenerator.generateId();
//		final var accessToken = IdGenerator.generateId();
//		final var name = IdGenerator.generateId();
//		final Map claims = Map.of("principal", name);
//		when(provider.getClaims(idToken, accessToken)).thenReturn(claims);
//
//		final var principal = new OidcPrincipal(idToken, accessToken, provider);
//
//	}
//
}
