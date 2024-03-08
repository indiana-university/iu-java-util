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
package edu.iu.auth.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import javax.security.auth.Subject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuAuthorizationScope;
import edu.iu.auth.session.IuSessionHeader;
import edu.iu.auth.session.IuSessionProviderKey;
import edu.iu.auth.session.IuSessionToken;
import iu.auth.util.PrincipalVerifierRegistry;

@SuppressWarnings("javadoc")
public class IuSessionTokenTest {

	private String realm;
	private IuPrincipalIdentity id;
	private String issuer;
	private Subject subject;

	@BeforeEach
	public void setup() throws Throwable {
		realm = IdGenerator.generateId();
		final var principal = IdGenerator.generateId();
		id = new IuPrincipalIdentity() {
			private static final long serialVersionUID = 1L;

			@Override
			public String getName() {
				return principal;
			}
		};
		PrincipalVerifierRegistry.registerVerifier(realm, a -> assertSame(a, id), true);

		issuer = IdGenerator.generateId();
		final var ecKeygen = KeyPairGenerator.getInstance("EC");
		ecKeygen.initialize(new ECGenParameterSpec("secp256r1"));
		final var keyPair = ecKeygen.generateKeyPair();
		subject = new Subject();
		subject.getPrincipals().add(new IuPrincipalIdentity() {
			private static final long serialVersionUID = 1L;

			@Override
			public String getName() {
				return issuer;
			}
		});
		subject.getPrincipals().add(IuAuthorizationScope.of("session", issuer));
		subject.getPrivateCredentials().add(new IuSessionProviderKey() {
			@Override
			public String getId() {
				return "default";
			}

			@Override
			public Usage getUsage() {
				return Usage.SIGN;
			}

			@Override
			public Type getType() {
				return Type.EC_P256;
			}

			@Override
			public PublicKey getPublic() {
				return keyPair.getPublic();
			}

			@Override
			public PrivateKey getPrivate() {
				return keyPair.getPrivate();
			}
		});
		IuSessionToken.register(Set.of(realm), subject);
	}

	@Test
	public void testSerializesAsAccessToken() throws Exception {
		final var audience = IdGenerator.generateId();

		final var header = mock(IuSessionHeader.class, CALLS_REAL_METHODS);
		when(header.getKeyId()).thenReturn("default");
		when(header.getSignatureAlgorithm()).thenReturn("ES256");
		when(header.getRealm()).thenReturn(realm);
		when(header.getIssuer()).thenReturn(issuer);
		when(header.getAudience()).thenReturn(audience);
		when(header.getSubject()).thenReturn(
				new Subject(true, Set.of(id, IuAuthorizationScope.of("session", issuer)), Set.of(), Set.of()));

		final var token = IuSessionToken.create(header);
		assertFalse(token.getTokenExpires()
				.isAfter(Instant.now().plus(header.getTokenExpires()).plus(1L, ChronoUnit.SECONDS)));
		assertNull(token.getSessionExpires());
		assertNull(token.getRefreshToken());
		assertEquals("{\"token_type\":\"Bearer\",\"access_token\":\"" + token.getAccessToken() + "\",\"expires_in\":"
				+ Duration.between(Instant.now(), token.getTokenExpires()).toSeconds() + ",\"scope\":\"session\"}",
				token.asTokenResponse());

		final var authToken = IuSessionToken.authorize(audience, token.getAccessToken());
		assertEquals(token.hashCode(), authToken.hashCode());
		assertEquals(token, authToken);
		assertEquals(authToken, token);
	}

	@Test
	public void testRefresh() throws Exception {
		final var audience = IdGenerator.generateId();

		final var header = mock(IuSessionHeader.class, CALLS_REAL_METHODS);
		when(header.getKeyId()).thenReturn("default");
		when(header.getSignatureAlgorithm()).thenReturn("ES256");
		when(header.getRealm()).thenReturn(realm);
		when(header.getIssuer()).thenReturn(issuer);
		when(header.getAudience()).thenReturn(audience);
		when(header.getSubject()).thenReturn(
				new Subject(true, Set.of(id, IuAuthorizationScope.of("session", issuer)), Set.of(), Set.of()));
		when(header.isRefresh()).thenReturn(true);

		final var token = IuSessionToken.create(header);
		assertFalse(token.getTokenExpires().isAfter(Instant.now().plus(header.getTokenExpires())));
		assertFalse(token.getSessionExpires().isAfter(Instant.now().plus(header.getSessionExpires())));
		assertNotNull(token.getRefreshToken());
		assertEquals(
				"{\"token_type\":\"Bearer\",\"access_token\":\"" + token.getAccessToken() + "\",\"expires_in\":"
						+ Duration.between(Instant.now(), token.getTokenExpires()).toSeconds()
						+ ",\"scope\":\"session\",\"refresh_token\":\"" + token.getRefreshToken() + "\"}",
				token.asTokenResponse());

		Thread.sleep(5L);
		final var refreshed = IuSessionToken.refresh(
				new Subject(true, Set.of(id, IuAuthorizationScope.of("session", issuer)), Set.of(), Set.of()),
				token.getRefreshToken());
		assertFalse(refreshed.getTokenExpires().isAfter(Instant.now().plus(header.getTokenExpires())));
		assertEquals(token.getSessionExpires(), refreshed.getSessionExpires());
		assertNotNull(refreshed.getRefreshToken());
		assertNotEquals(token.getAccessToken(), refreshed.getAccessToken());
		assertNotEquals(token.getRefreshToken(), refreshed.getRefreshToken());

		assertEquals(
				"{\"token_type\":\"Bearer\",\"access_token\":\"" + refreshed.getAccessToken() + "\",\"expires_in\":"
						+ Duration.between(Instant.now(), refreshed.getTokenExpires()).toSeconds()
						+ ",\"scope\":\"session\",\"refresh_token\":\"" + refreshed.getRefreshToken() + "\"}",
				refreshed.asTokenResponse());
		assertEquals(token.hashCode(), refreshed.hashCode());
		assertEquals(token, refreshed);
		assertEquals(refreshed, token);

		final var authToken = IuSessionToken.authorize(audience, refreshed.getAccessToken());
		assertEquals(token.hashCode(), authToken.hashCode());
		assertEquals(token, authToken);
		assertEquals(authToken, token);
	}

}
