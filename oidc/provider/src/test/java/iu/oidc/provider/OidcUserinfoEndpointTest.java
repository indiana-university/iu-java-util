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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcClaims;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.OidcClaimsSource;
import edu.iu.oidc.config.OidcClientConfiguration;
import edu.iu.oidc.config.OidcClientSource;
import edu.iu.oidc.config.OidcProviderConfiguration;
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

	private final String keyId = IdGenerator.generateId();
	private final WebKey issuerKey = WebKey.builder(Algorithm.ES256).keyId(keyId).ephemeral().build();
	private final OidcIssuer issuer = mock(OidcIssuer.class);
	private final OidcClientSource clients = mock(OidcClientSource.class);
	private final OidcClaimsSource claimsSource = mock(OidcClaimsSource.class);

	private OidcUserinfoEndpoint endpoint;

	@BeforeEach
	void setup() {
		IuTestLogger.allow(OidcUserinfoEndpoint.class.getName(), Level.INFO);

		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getIssuer()).thenReturn(ISSUER);

		final var configuration = new OidcProviderConfiguration() {

			@Override
			public IuOidcProviderMetadata getMetadata() {
				return metadata;
			}

			@Override
			public Iterable<WebKey> getJwks() {
				return List.of(issuerKey);
			}
		};

		when(issuer.configuration()).thenReturn(configuration);
		when(issuer.issuer()).thenReturn(ISSUER);
		when(issuer.issuerKey(Algorithm.ES256)).thenReturn(issuerKey);

		endpoint = new OidcUserinfoEndpoint(issuer, clients, claimsSource);
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

	/** Holds a name and an email address, and nothing else. */
	private void sourceHolds() {
		final var claims = mock(IuOidcClaims.class);
		when(claims.getName()).thenReturn("Some One");
		when(claims.getEmail()).thenReturn("someone@iu.edu");
		when(claimsSource.claims(SUB)).thenReturn(claims);
	}

	/** Registers a client with the given UserInfo response settings. */
	private OidcClientConfiguration register(Algorithm algorithm, Encryption encryption, WebKey jwk) {
		final var client = mock(OidcClientConfiguration.class);
		when(client.getClientId()).thenReturn(CLIENT_ID);
		when(client.getUserinfoAlg()).thenReturn(algorithm);
		when(client.getUserinfoEnc()).thenReturn(encryption);
		when(client.getUserinfoJwk()).thenReturn(jwk);
		when(clients.client(CLIENT_ID)).thenReturn(client);
		return client;
	}

	/** Renders what the endpoint admitted, the way a transport would. */
	private static String render(IuOidcClaims claims) {
		return "{\"sub\":\"" + claims.getSub() + "\",\"name\":" + quote(claims.getName()) + ",\"email\":"
				+ quote(claims.getEmail()) + "}";
	}

	private static String quote(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	@Test
	void testEveryCollaboratorIsRequired() {
		assertEquals("Missing issuer", assertThrows(NullPointerException.class,
				() -> new OidcUserinfoEndpoint(null, clients, claimsSource)).getMessage());
		assertEquals("Missing client source", assertThrows(NullPointerException.class,
				() -> new OidcUserinfoEndpoint(issuer, null, claimsSource)).getMessage());
		assertEquals("Missing claims source", assertThrows(NullPointerException.class,
				() -> new OidcUserinfoEndpoint(issuer, clients, null)).getMessage());
	}

	@Test
	void testAnUnverifiableTokenIsRefusedBeforeAnythingIsRead() {
		assertThrows(SecurityException.class, () -> endpoint.userinfo("not a token", OidcUserinfoEndpointTest::render));

		// nothing was asked of the claims source, and no serializer ran
		org.mockito.Mockito.verify(claimsSource, org.mockito.Mockito.never()).claims(SUB);
	}

	@Test
	void testTheGrantsScopeIsWhatTheResponseCarries() {
		sourceHolds();
		register(null, null, null);

		final var result = assertInstanceOf(Json.class, //
				endpoint.userinfo(accessToken("openid profile"), OidcUserinfoEndpointTest::render));

		// profile admits the name; nothing granted email
		assertEquals("{\"sub\":\"someone\",\"name\":\"Some One\",\"email\":null}", result.content());
		assertEquals("application/json", result.contentType());
	}

	@Test
	void testATokenCarryingNoScopeCarriesNoClaims() {
		sourceHolds();
		register(null, null, null);

		final var result = endpoint.userinfo(accessToken(null), OidcUserinfoEndpointTest::render);
		assertEquals("{\"sub\":\"someone\",\"name\":null,\"email\":null}", result.content());
	}

	@Test
	void testTheSubjectIsBoundToTheTokenNotTheSource() {
		final var claims = mock(IuOidcClaims.class);
		when(claims.getSub()).thenReturn("some-other-sub");
		when(claimsSource.claims(SUB)).thenReturn(claims);
		register(null, null, null);

		final var result = endpoint.userinfo(accessToken("openid"), OidcUserinfoEndpointTest::render);
		assertTrue(result.content().startsWith("{\"sub\":\"someone\""), result::content);
	}

	@Test
	void testASourceHoldingNothingStillNamesTheSubject() {
		when(claimsSource.claims(SUB)).thenReturn(null);
		register(null, null, null);

		final var result = endpoint.userinfo(accessToken("openid profile email"), OidcUserinfoEndpointTest::render);
		assertEquals("{\"sub\":\"someone\",\"name\":null,\"email\":null}", result.content());
	}

	@Test
	void testAClientRegisteringNothingGetsAPlainDocument() {
		sourceHolds();
		register(null, null, null);

		assertInstanceOf(Json.class, endpoint.userinfo(accessToken("openid"), OidcUserinfoEndpointTest::render));
	}

	@Test
	void testATokenNamingNoClientGetsAPlainDocument() {
		sourceHolds();

		final var token = OidcJose.sign(WebToken.builder().jti().iss(ISSUER).sub(SUB).aud(ISSUER).iat()
				.exp(Instant.now().plus(Duration.ofMinutes(5L))).build().toString(), "at+jwt", issuerKey);

		assertInstanceOf(Json.class, endpoint.userinfo(token, OidcUserinfoEndpointTest::render));
	}

	@Test
	void testARegistrationSinceGoneMissingDoesntTakeATokenOutOfService() {
		sourceHolds();
		when(clients.client(CLIENT_ID)).thenReturn(null);

		assertInstanceOf(Json.class, endpoint.userinfo(accessToken("openid"), OidcUserinfoEndpointTest::render));
	}

	@Test
	void testAClientSourceThatRefusesDoesntTakeATokenOutOfService() {
		sourceHolds();
		when(clients.client(CLIENT_ID)).thenThrow(new IllegalStateException("no such registration"));

		assertInstanceOf(Json.class, endpoint.userinfo(accessToken("openid"), OidcUserinfoEndpointTest::render));
	}

	@Test
	void testRegisteringAnAlgorithmSignsTheResponse() {
		sourceHolds();
		register(Algorithm.ES256, null, null);

		final var result = assertInstanceOf(Jwt.class,
				endpoint.userinfo(accessToken("openid email"), OidcUserinfoEndpointTest::render));
		assertEquals("application/jwt", result.contentType());

		final var jws = WebSignedPayload.parse(result.content());
		assertEquals("{\"sub\":\"someone\",\"name\":null,\"email\":\"someone@iu.edu\"}",
				edu.iu.IuText.utf8(jws.getPayload()));
		assertEquals("JWT", jws.getSignatures().iterator().next().getHeader().getType());
		jws.verify(issuerKey);
	}

	@Test
	void testRegisteringAnEncryptionAloneAnswersSomethingUnauthenticated() {
		sourceHolds();
		final var clientKey = WebKey.builder(Algorithm.ECDH_ES).ephemeral().build();
		register(null, Encryption.AES_128_CBC_HMAC_SHA_256, clientKey);

		final var result = assertInstanceOf(Jwt.class,
				endpoint.userinfo(accessToken("openid email"), OidcUserinfoEndpointTest::render));

		final var jwe = WebEncryption.parse(result.content());
		// no cty: the plaintext is the claims, not a nested JOSE object
		assertNull(jwe.getRecipients().iterator().next().getHeader().getContentType());

		IuTestLogger.allow("iu.crypt", Level.FINE);
		assertEquals("{\"sub\":\"someone\",\"name\":null,\"email\":\"someone@iu.edu\"}", jwe.decryptText(clientKey));
	}

	@Test
	void testRegisteringBothSignsThenEncrypts() {
		sourceHolds();
		final var clientKey = WebKey.builder(Algorithm.ECDH_ES).ephemeral().build();
		register(Algorithm.ES256, Encryption.AES_128_CBC_HMAC_SHA_256, clientKey);

		final var result = assertInstanceOf(Jwt.class,
				endpoint.userinfo(accessToken("openid profile"), OidcUserinfoEndpointTest::render));

		final var jwe = WebEncryption.parse(result.content());
		assertEquals("JWT", jwe.getRecipients().iterator().next().getHeader().getContentType());

		IuTestLogger.allow("iu.crypt", Level.FINE);
		final var jws = WebSignedPayload.parse(jwe.decryptText(clientKey));
		assertEquals("{\"sub\":\"someone\",\"name\":\"Some One\",\"email\":null}",
				edu.iu.IuText.utf8(jws.getPayload()));
		jws.verify(issuerKey);
	}

	@Test
	void testRegisteringAnEncryptionWithNoKeyIsAConfigurationFault() {
		sourceHolds();
		register(null, Encryption.AES_128_CBC_HMAC_SHA_256, null);

		final var token = accessToken("openid");
		assertEquals("Missing userinfo encryption key for some-client", assertThrows(NullPointerException.class,
				() -> endpoint.userinfo(token, OidcUserinfoEndpointTest::render)).getMessage());
	}

	@Test
	void testTheSerializerIsCalledOnceOnWhatWasAdmitted() {
		sourceHolds();
		register(null, null, null);

		final var calls = new int[1];
		endpoint.userinfo(accessToken("openid email"), claims -> {
			calls[0]++;
			assertEquals(SUB, claims.getSub());
			assertEquals("someone@iu.edu", claims.getEmail());
			assertNull(claims.getName());
			assertEquals(Set.of(), Set.of());
			return "{}";
		});

		assertEquals(1, calls[0]);
	}

}
