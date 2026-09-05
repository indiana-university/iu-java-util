/*
 * Copyright © 2026 Indiana University
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
package iu.oidc.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.WebToken;
import edu.iu.jwt.WebTokenBuilder;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcProviderConfiguration;

@SuppressWarnings("javadoc")
public class OidcTokenAuthorizationTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");
	private static final String SUBJECT = "someone";

	private final String keyId = IdGenerator.generateId();
	private final WebKey issuerKey = WebKey.builder(Algorithm.ES256).keyId(keyId).ephemeral().build();

	/** Answers a configuration publishing one issuer key. */
	private IuOidcProviderConfiguration provider(Iterable<WebKey> jwks) {
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getIssuer()).thenReturn(ISSUER);

		return new IuOidcProviderConfiguration() {

			@Override
			public IuOidcProviderMetadata getMetadata() {
				return metadata;
			}

			@Override
			public Iterable<WebKey> getJwks() {
				return jwks;
			}
		};
	}

	private IuOidcProviderConfiguration provider() {
		return provider(List.of(issuerKey));
	}

	/** Issues an access token the way this provider's token endpoint would. */
	private String accessToken(Consumer<WebTokenBuilder> claims) {
		final var builder = WebToken.builder() //
				.jti() //
				.iss(ISSUER) //
				.sub(SUBJECT) //
				.aud(ISSUER) //
				.iat() //
				.exp(Instant.now().plus(Duration.ofMinutes(5L)));

		claims.accept(builder);

		return OidcJose.sign(builder.build().toString(), "at+jwt", issuerKey);
	}

	private String accessToken() {
		return accessToken(b -> b.claim("client_id", "some-client", String.class) //
				.claim("scope", "openid profile email", String.class));
	}

	@Test
	void testReportsWhatTheGrantAuthorized() {
		final var authorization = OidcTokenAuthorization.verify(accessToken(), provider(), ISSUER);

		assertEquals(SUBJECT, authorization.getSubject());
		assertEquals("some-client", authorization.getClientId());
		assertIterableEquals(List.of("openid", "profile", "email"), authorization.getScope());
	}

	@Test
	void testTheVerifiedTokenIsAvailableForClaimsItDoesntReport() {
		final var authorization = OidcTokenAuthorization.verify(accessToken(), provider(), ISSUER);
		final var token = authorization.getToken();

		assertEquals(SUBJECT, token.getSubject());
		assertSame(token, authorization.getToken());
	}

	@Test
	void testDescribesItselfByWhatItAuthorized() {
		final var authorization = OidcTokenAuthorization.verify(accessToken(), provider(), ISSUER);
		assertEquals("OidcTokenAuthorization [sub=someone, client_id=some-client, scope=[openid, profile, email]]",
				authorization.toString());
	}

	@Test
	void testATokenNamingNoClientReportsNone() {
		final var authorization = OidcTokenAuthorization.verify(accessToken(b -> {
		}), provider(), ISSUER);
		assertNull(authorization.getClientId());
	}

	@Test
	void testATokenCarryingNoScopeGrantsNone() {
		final var authorization = OidcTokenAuthorization.verify(accessToken(b -> {
		}), provider(), ISSUER);
		assertEquals(Set.of(), authorization.getScope());
	}

	@Test
	void testRepeatedSpacesInScopeDontBecomeAScope() {
		final var authorization = OidcTokenAuthorization
				.verify(accessToken(b -> b.claim("scope", "openid  email", String.class)), provider(), ISSUER);
		assertIterableEquals(List.of("openid", "email"), authorization.getScope());
	}

	@Test
	void testGrantedScopeIsNotAlterable() {
		final var authorization = OidcTokenAuthorization.verify(accessToken(), provider(), ISSUER);
		assertThrows(UnsupportedOperationException.class, () -> authorization.getScope().add("admin"));
	}

	@Test
	void testAnUnreadableTokenIsRefused() {
		final var provider = provider();
		final var e = assertThrows(SecurityException.class,
				() -> OidcTokenAuthorization.verify("not a token", provider, ISSUER));
		assertEquals("Unreadable access token", e.getMessage());
	}

	@Test
	void testATokenNamingNoKeyIsRefused() {
		// nothing selects a candidate key, so nothing could verify it
		final var unnamed = OidcJose.sign("{}", "at+jwt", WebKey.builder(Algorithm.ES256).ephemeral().build());
		final var provider = provider();
		final var e = assertThrows(SecurityException.class,
				() -> OidcTokenAuthorization.verify(unnamed, provider, ISSUER));
		assertEquals("Access token names no signing key", e.getMessage());
	}

	@Test
	void testAKeyThisIssuerDoesntPublishIsRefused() {
		final var elsewhere = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral().build();
		final var token = OidcJose.sign("{}", "at+jwt", elsewhere);
		final var provider = provider();

		final var e = assertThrows(SecurityException.class, () -> OidcTokenAuthorization.verify(token, provider, ISSUER));
		assertEquals("Access token signing key is not published by this issuer", e.getMessage());
	}

	@Test
	void testANullEntryInTheKeySetIsSkipped() {
		final var provider = provider(Arrays.asList(null, issuerKey));
		assertEquals(SUBJECT, OidcTokenAuthorization.verify(accessToken(), provider, ISSUER).getSubject());
	}

	@Test
	void testATokenAddressedElsewhereIsRefused() {
		final var provider = provider();
		final var token = accessToken();

		final var e = assertThrows(SecurityException.class,
				() -> OidcTokenAuthorization.verify(token, provider, URI.create("https://elsewhere.iu.edu")));
		assertTrue(e.getMessage().startsWith("Invalid access token"), e::getMessage);
	}

	@Test
	void testASignatureThatDoesntVerifyIsRefused() {
		// the same key ID, signed by a different key: naming a key doesn't make a
		// signature verify against it
		final var impostor = WebKey.builder(Algorithm.ES256).keyId(keyId).ephemeral().build();
		final var token = OidcJose.sign(WebToken.builder().jti().iss(ISSUER).sub(SUBJECT).aud(ISSUER).iat()
				.exp(Instant.now().plus(Duration.ofMinutes(5L))).build().toString(), "at+jwt", impostor);
		final var provider = provider();

		assertThrows(SecurityException.class, () -> OidcTokenAuthorization.verify(token, provider, ISSUER));
	}

	@Test
	void testMetadataIsRequired() {
		final var provider = mock(IuOidcProviderConfiguration.class);
		final var token = accessToken();
		assertEquals("Missing provider metadata", assertThrows(NullPointerException.class,
				() -> OidcTokenAuthorization.verify(token, provider, ISSUER)).getMessage());
	}

	@Test
	void testAnIssuerIsRequired() {
		final var provider = provider(List.of(issuerKey));
		when(provider.getMetadata().getIssuer()).thenReturn(null);

		final var token = accessToken();
		assertEquals("Missing issuer", assertThrows(NullPointerException.class,
				() -> OidcTokenAuthorization.verify(token, provider, ISSUER)).getMessage());
	}

	@Test
	void testAnAccessTokenTimeToLiveIsRequired() {
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getIssuer()).thenReturn(ISSUER);

		final var provider = new IuOidcProviderConfiguration() {

			@Override
			public IuOidcProviderMetadata getMetadata() {
				return metadata;
			}

			@Override
			public Iterable<WebKey> getJwks() {
				return List.of(issuerKey);
			}

			@Override
			public Duration getAccessTokenTimeToLive() {
				return null;
			}
		};

		final var token = accessToken();
		assertEquals("Missing access token TTL", assertThrows(NullPointerException.class,
				() -> OidcTokenAuthorization.verify(token, provider, ISSUER)).getMessage());
	}

	@Test
	void testIssuerKeysAreRequired() {
		final var provider = provider(null);
		final var token = accessToken();
		assertEquals("Missing issuer keys", assertThrows(NullPointerException.class,
				() -> OidcTokenAuthorization.verify(token, provider, ISSUER)).getMessage());
	}

}
