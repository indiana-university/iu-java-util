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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class OpenIdProviderTest extends IuOidcTestCase {

	@Test
	public void testNonAuth() throws IuAuthenticationException {
		final var client = mock(IuOpenIdClient.class);
		final var provider = new OpenIdProvider(client);
		assertThrows(NullPointerException.class, () -> provider.clientCredentials());

		final var accessToken = IdGenerator.generateId();
		try (final var mockOidcPrincipal = mockConstruction(OidcPrincipal.class, (i, ctx) -> {
			final var args = ctx.arguments();
			assertNull(args.get(0));
			assertEquals(accessToken, args.get(1));
			assertSame(provider, args.get(2));
		})) {
			assertSame(provider.hydrate(accessToken), mockOidcPrincipal.constructed().get(0));
		}

		assertSame(client, provider.client());
		assertThrows(ClassCastException.class, provider::authClient);

		final var principal = IdGenerator.generateId();
		final var userinfoUri = uri(IuJson.object() //
				.add("principal", principal) //
				.build(), b -> {
					verify(b).header("Authorization", "Bearer " + accessToken);
				});
		final var configUri = uri(IuJson.object() //
				.add("userinfo_endpoint", userinfoUri.toString()) //
				.build());
		when(client.getProviderConfigUri()).thenReturn(configUri);

		final var config = provider.config();
		assertEquals(1, config.size());
		assertTrue(config.containsKey("userinfo_endpoint"));

		final var userinfo = provider.userinfo(accessToken);
		assertEquals(1, userinfo.size());
		assertEquals(principal, userinfo.get("principal"));

		final var claims = provider.getClaims(null, accessToken);
		assertEquals(claims, userinfo);
	}

	@Test
	public void testAuth() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var atHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
				Arrays.copyOfRange(MessageDigest.getInstance("SHA-256").digest(IuText.utf8(accessToken)), 0, 16));
		final var principal = IdGenerator.generateId();
		assertAuth("RS256", issuer, clientId, accessToken, IuJson.object() //
				.add("principal", principal) //
				.add("sub", principal) //
				.build(),
				IuJson.object() //
						.add("iss", issuer) //
						.add("iat", now.getEpochSecond()) //
						.add("exp", now.getEpochSecond() + 5) //
						.add("aud", IuJson.array().add(clientId)) //
						.add("auth_time", now.getEpochSecond()) //
						.add("at_hash", atHash) //
						.build(),
				now);
	}

	@Test
	public void testClientCred() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		assertAuth("RS256", issuer, clientId, accessToken, IuJson.object() //
				.add("principal", clientId) //
				.add("sub", clientId) //
				.build(), null, now);
	}

	@Test
	public void testMissingId() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var principal = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		assertEquals("Missing ID token",
				assertThrows(IllegalArgumentException.class,
						() -> assertAuth("RS256", issuer, clientId, accessToken, IuJson.object() //
								.add("principal", principal) //
								.add("sub", clientId) //
								.build(), null, now))
						.getMessage());
		assertEquals("Missing ID token",
				assertThrows(IllegalArgumentException.class,
						() -> assertAuth("RS256", issuer, clientId, accessToken, IuJson.object() //
								.add("principal", clientId) //
								.add("sub", principal) //
								.build(), null, now))
						.getMessage());
	}

	@Test
	public void testWrongAlg() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var atHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
				Arrays.copyOfRange(MessageDigest.getInstance("SHA-256").digest(IuText.utf8(accessToken)), 0, 16));
		final var principal = IdGenerator.generateId();
		assertEquals("ES256 required",
				assertThrows(IllegalArgumentException.class,
						() -> assertAuth("ES256", issuer, clientId, accessToken, IuJson.object() //
								.add("principal", principal) //
								.add("sub", principal) //
								.build(),
								IuJson.object() //
										.add("iss", issuer) //
										.add("iat", now.getEpochSecond()) //
										.add("exp", now.getEpochSecond() + 5) //
										.add("aud", IuJson.array().add(clientId)) //
										.add("auth_time", now.getEpochSecond()) //
										.add("at_hash", atHash) //
										.build(),
								now))
						.getMessage());
	}

	@Test
	public void testWrongAtHash() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var atHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
				Arrays.copyOfRange(MessageDigest.getInstance("SHA-256").digest(IuText.utf8(accessToken)), 16, 32));
		final var principal = IdGenerator.generateId();
		assertEquals("Invalid at_hash",
				assertThrows(IllegalArgumentException.class,
						() -> assertAuth("RS256", issuer, clientId, accessToken, IuJson.object() //
								.add("principal", principal) //
								.add("sub", principal) //
								.build(),
								IuJson.object() //
										.add("iss", issuer) //
										.add("iat", now.getEpochSecond()) //
										.add("exp", now.getEpochSecond() + 5) //
										.add("aud", IuJson.array().add(clientId)) //
										.add("auth_time", now.getEpochSecond()) //
										.add("at_hash", atHash) //
										.build(),
								now))
						.getMessage());
	}

	@Test
	public void testExpiredAuth() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var atHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
				Arrays.copyOfRange(MessageDigest.getInstance("SHA-256").digest(IuText.utf8(accessToken)), 0, 16));
		final var principal = IdGenerator.generateId();
		assertEquals("OIDC authenticated session is expired",
				assertThrows(IllegalArgumentException.class,
						() -> assertAuth("RS256", issuer, clientId, accessToken, IuJson.object() //
								.add("principal", principal) //
								.add("sub", principal) //
								.build(),
								IuJson.object() //
										.add("iss", issuer) //
										.add("iat", now.getEpochSecond()) //
										.add("exp", now.getEpochSecond() + 5) //
										.add("aud", IuJson.array().add(clientId)) //
										.add("auth_time", now.getEpochSecond() - 10) //
										.add("at_hash", atHash) //
										.build(),
								now))
						.getMessage());
	}

	@SuppressWarnings("deprecation")
	@Test
	private void assertAuth(String alg, String issuer, String clientId, String accessToken, JsonObject userinfo,
			JsonObject idTokenPayload, Instant now) throws Exception {
		final var realm = IdGenerator.generateId();
		final var resourceUri = URI.create("test:" + IdGenerator.generateId());
		final var redirectUri = URI.create("test:" + IdGenerator.generateId());
		final var credentials = mock(IuApiCredentials.class);
		when(credentials.getName()).thenReturn(clientId);

		final var client = mock(IuAuthoritativeOpenIdClient.class);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getCredentials()).thenReturn(credentials);
		when(client.getIdTokenAlgorithm()).thenReturn(alg);
		when(client.getAuthenticatedSessionTimeout()).thenReturn(Duration.ofSeconds(5L));

		final var provider = new OpenIdProvider(client);
		assertInstanceOf(IuAuthorizationGrant.class, provider.clientCredentials());

		try (final var mockOidcPrincipal = mockConstruction(OidcPrincipal.class, (i, ctx) -> {
			final var args = ctx.arguments();
			assertNull(args.get(0));
			assertEquals(accessToken, args.get(1));
			assertSame(provider, args.get(2));
		})) {
			assertSame(provider.hydrate(accessToken), mockOidcPrincipal.constructed().get(0));
		}

		assertSame(client, provider.client());
		assertSame(client, provider.authClient());

		final var userinfoUri = uri(userinfo, b -> {
			verify(b).header("Authorization", "Bearer " + accessToken);
		});

		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.RS256).keyId(kid).ephemeral().build();
		final var jwksUri = uri(IuJson.parse(WebKey.asJwks(List.of(jwk.wellKnown()))).asJsonObject());
		final var configUri = uri(IuJson.object() //
				.add("issuer", issuer) //
				.add("jwks_uri", jwksUri.toString()) //
				.add("userinfo_endpoint", userinfoUri.toString()) //
				.build());
		when(client.getProviderConfigUri()).thenReturn(configUri);

		final var idToken = idTokenPayload == null ? null
				: WebSignature.builder(Algorithm.RS256) //
						.type("jwt") //
						.keyId(kid) //
						.key(jwk) //
						.compact() //
						.sign(idTokenPayload.toString()).compact();

		final var claims = provider.getClaims(idToken, accessToken);
		for (final var e : provider.userinfo(accessToken).entrySet())
			assertEquals(e.getValue(), claims.get(e.getKey()));
		if (idToken == null) {
			assertEquals(clientId, claims.get("principal"));
			assertEquals(clientId, claims.get("sub"));
		} else {
			assertEquals(now, claims.get("iat"), claims::toString);
			assertEquals(now.plusSeconds(5L), claims.get("exp"), claims::toString);
			assertEquals(now, claims.get("auth_time"), claims::toString);
		}
	}

}
