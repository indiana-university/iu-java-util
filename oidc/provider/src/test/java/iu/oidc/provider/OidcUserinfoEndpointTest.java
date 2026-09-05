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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcClaims;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcClaimsSource;
import edu.iu.oidc.config.IuOidcClientConfiguration;
import edu.iu.oidc.config.IuOidcClientSource;
import edu.iu.oidc.config.IuOidcProviderConfiguration;
import edu.iu.oidc.config.IuOidcProviderReference;
import edu.iu.test.IuTestLogger;
import iu.oidc.provider.OidcUserinfoResult.Json;
import iu.oidc.provider.OidcUserinfoResult.Jwt;

@SuppressWarnings("javadoc")
public class OidcUserinfoEndpointTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");
	private static final String SUB = "someone";
	private static final String CLIENT_ID = "some-client";

	/** What a source renders; opaque to the endpoint, which publishes it as-is. */
	private static final String DOCUMENT = "{\"sub\":\"someone\",\"email\":\"someone@iu.edu\"}";

	private final String keyId = IdGenerator.generateId();
	private final WebKey issuerKey = WebKey.builder(Algorithm.ES256).keyId(keyId).ephemeral().build();
	private final IuOidcProviderReference reference = mock(IuOidcProviderReference.class);
	private final IuOidcClientSource clients = mock(IuOidcClientSource.class);
	private final IuOidcClaimsSource claimsSource = mock(IuOidcClaimsSource.class);

	private OidcUserinfoEndpoint endpoint;

	@BeforeEach
	void setup() {
		IuTestLogger.allow(OidcUserinfoEndpoint.class.getName(), Level.INFO);

		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getIssuer()).thenReturn(ISSUER);

		final var configuration = new IuOidcProviderConfiguration() {

			@Override
			public IuOidcProviderMetadata getMetadata() {
				return metadata;
			}

			@Override
			public Iterable<WebKey> getJwks() {
				return List.of(issuerKey);
			}
		};

		when(reference.getConfiguration()).thenReturn(configuration);
		when(reference.getClientSource()).thenReturn(clients);
		when(reference.getClaimsSource()).thenReturn(claimsSource);

		endpoint = new OidcUserinfoEndpoint(reference);
	}

	/** Issues an access token the way this provider's token endpoint would. */
	private String accessToken(String scope) {
		final var builder = WebToken.builder() //
				.jti() //
				.iss(ISSUER) //
				.sub(SUB) //
				.aud(ISSUER) //
				.iat() //
				.exp(Instant.now().plus(Duration.ofMinutes(5L))) //
				.claim("client_id", CLIENT_ID, String.class);

		if (scope != null)
			builder.claim("scope", scope, String.class);

		return OidcJose.sign(builder.build().toString(), "at+jwt", issuerKey);
	}

	/**
	 * Answers claims that render themselves, which is what the contract requires
	 * and the only thing the endpoint reads besides {@code sub}.
	 */
	private static IuOidcClaims claims(String sub, String document) {
		return new IuOidcClaims() {

			@Override
			public String getSub() {
				return sub;
			}

			@Override
			public String toString() {
				return document;
			}
		};
	}

	/** Answers claims for the subject the token names. */
	private void sourceHolds() {
		when(claimsSource.claims(eq(SUB), any())).thenReturn(claims(SUB, DOCUMENT));
	}

	/** Registers a client with the given UserInfo response settings. */
	private void register(Algorithm algorithm, Encryption encryption, WebKey jwk) {
		final var client = mock(IuOidcClientConfiguration.class);
		when(client.getClientId()).thenReturn(CLIENT_ID);
		when(client.getUserinfoAlg()).thenReturn(algorithm);
		when(client.getUserinfoEnc()).thenReturn(encryption);
		when(client.getUserinfoJwk()).thenReturn(jwk);
		when(clients.client(CLIENT_ID)).thenReturn(client);
	}

	/** Answers the claim names the endpoint asked its source for. */
	@SuppressWarnings("unchecked")
	private Set<String> admitted() {
		final var captor = ArgumentCaptor.forClass(Set.class);
		verify(claimsSource).claims(eq(SUB), captor.capture());
		return captor.getValue();
	}

	@Test
	void testTheReferenceIsRequired() {
		assertEquals("Missing provider reference",
				assertThrows(NullPointerException.class, () -> new OidcUserinfoEndpoint(null)).getMessage());
	}

	@Test
	void testAnUnverifiableTokenIsRefusedBeforeAnythingIsRead() {
		assertThrows(SecurityException.class, () -> endpoint.userinfo("not a token"));

		// nothing was asked of the claims source
		verify(claimsSource, never()).claims(any(), any());
	}

	@Test
	void testTheGrantsScopeIsWhatTheSourceIsAskedFor() {
		sourceHolds();
		register(null, null, null);

		endpoint.userinfo(accessToken("openid email"));

		// email admits the address and whether it was verified; nothing granted profile
		assertIterableEquals(List.of("sub", "email", "email_verified"), admitted());
	}

	@Test
	void testATokenCarryingNoScopeAdmitsOnlyTheSubject() {
		sourceHolds();
		register(null, null, null);

		endpoint.userinfo(accessToken(null));
		assertIterableEquals(List.of("sub"), admitted());
	}

	@Test
	void testAnUnrecognizedScopeAdmitsNothingExtra() {
		sourceHolds();
		register(null, null, null);

		endpoint.userinfo(accessToken("openid offline_access read"));
		assertIterableEquals(List.of("sub"), admitted());
	}

	@Test
	void testProfileAdmitsTheProfileClaims() {
		sourceHolds();
		register(null, null, null);

		endpoint.userinfo(accessToken("profile address phone"));

		final var admitted = admitted();
		// sub, plus fourteen from profile, one from address, two from phone
		assertEquals(18, admitted.size(), admitted::toString);
		assertEquals(true, admitted.containsAll(List.of("sub", "name", "given_name", "updated_at", "address",
				"phone_number", "phone_number_verified")), admitted::toString);
	}

	@Test
	void testWhatTheSourceRendersIsWhatIsPublished() {
		sourceHolds();
		register(null, null, null);

		final var result = assertInstanceOf(Json.class, endpoint.userinfo(accessToken("openid email")));
		assertEquals(DOCUMENT, result.content());
		assertEquals("application/json", result.contentType());
	}

	@Test
	void testASourceAnsweringAboutSomebodyElseIsRefused() {
		// a relying party matches sub against the ID token it holds, so publishing
		// claims about anyone else would be worse than answering nothing
		when(claimsSource.claims(eq(SUB), any())).thenReturn(claims("somebody-else", DOCUMENT));
		register(null, null, null);

		final var token = accessToken("openid");
		assertEquals("Claims source answered for somebody-else rather than someone",
				assertThrows(IllegalStateException.class, () -> endpoint.userinfo(token)).getMessage());
	}

	@Test
	void testASourceAnsweringNothingIsAServerFault() {
		when(claimsSource.claims(eq(SUB), any())).thenReturn(null);
		register(null, null, null);

		final var token = accessToken("openid");
		assertEquals("Missing claims for someone",
				assertThrows(NullPointerException.class, () -> endpoint.userinfo(token)).getMessage());
	}

	@Test
	void testATokenNamingNoClientGetsAPlainDocument() {
		sourceHolds();

		final var token = OidcJose.sign(WebToken.builder().jti().iss(ISSUER).sub(SUB).aud(ISSUER).iat()
				.exp(Instant.now().plus(Duration.ofMinutes(5L))).build().toString(), "at+jwt", issuerKey);

		assertInstanceOf(Json.class, endpoint.userinfo(token));
	}

	@Test
	void testARegistrationSinceGoneMissingDoesntTakeATokenOutOfService() {
		sourceHolds();
		when(clients.client(CLIENT_ID)).thenReturn(null);

		assertInstanceOf(Json.class, endpoint.userinfo(accessToken("openid")));
	}

	@Test
	void testAClientSourceThatRefusesDoesntTakeATokenOutOfService() {
		sourceHolds();
		when(clients.client(CLIENT_ID)).thenThrow(new IllegalStateException("no such registration"));

		assertInstanceOf(Json.class, endpoint.userinfo(accessToken("openid")));
	}

	@Test
	void testRegisteringAnAlgorithmSignsTheResponse() {
		sourceHolds();
		register(Algorithm.ES256, null, null);

		final var result = assertInstanceOf(Jwt.class, endpoint.userinfo(accessToken("openid email")));
		assertEquals("application/jwt", result.contentType());

		final var jws = WebSignedPayload.parse(result.content());
		assertEquals(DOCUMENT, IuText.utf8(jws.getPayload()));
		assertEquals("JWT", jws.getSignatures().iterator().next().getHeader().getType());
		jws.verify(issuerKey);
	}

	@Test
	void testRegisteringAnEncryptionAloneAnswersSomethingUnauthenticated() {
		sourceHolds();
		final var clientKey = WebKey.builder(Algorithm.ECDH_ES).ephemeral().build();
		register(null, Encryption.AES_128_CBC_HMAC_SHA_256, clientKey);

		final var result = assertInstanceOf(Jwt.class, endpoint.userinfo(accessToken("openid email")));

		final var jwe = WebEncryption.parse(result.content());
		// no cty: the plaintext is the claims, not a nested JOSE object
		assertNull(jwe.getRecipients().iterator().next().getHeader().getContentType());

		IuTestLogger.allow("iu.crypt", Level.FINE);
		assertEquals(DOCUMENT, jwe.decryptText(clientKey));
	}

	@Test
	void testRegisteringBothSignsThenEncrypts() {
		sourceHolds();
		final var clientKey = WebKey.builder(Algorithm.ECDH_ES).ephemeral().build();
		register(Algorithm.ES256, Encryption.AES_128_CBC_HMAC_SHA_256, clientKey);

		final var result = assertInstanceOf(Jwt.class, endpoint.userinfo(accessToken("openid profile")));

		final var jwe = WebEncryption.parse(result.content());
		assertEquals("JWT", jwe.getRecipients().iterator().next().getHeader().getContentType());

		IuTestLogger.allow("iu.crypt", Level.FINE);
		final var jws = WebSignedPayload.parse(jwe.decryptText(clientKey));
		assertEquals(DOCUMENT, IuText.utf8(jws.getPayload()));
		jws.verify(issuerKey);
	}

	@Test
	void testRegisteringAnEncryptionWithNoKeyIsAConfigurationFault() {
		sourceHolds();
		register(null, Encryption.AES_128_CBC_HMAC_SHA_256, null);

		final var token = accessToken("openid");
		assertEquals("Missing userinfo encryption key for some-client",
				assertThrows(NullPointerException.class, () -> endpoint.userinfo(token)).getMessage());
	}

}
