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

import static iu.oidc.provider.OidcTokenEndpoint.ACCESS_TOKEN_TOKEN_TYPE;
import static iu.oidc.provider.OidcTokenEndpoint.PRINCIPAL_NAME_TOKEN_TYPE;
import static iu.oidc.provider.OidcTokenEndpoint.TOKEN_EXCHANGE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuDigest;
import edu.iu.IuIterable;
import edu.iu.IuOutOfServiceException;
import edu.iu.IuText;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.IuAuthorizationDetails;
import edu.iu.jwt.WebToken;
import edu.iu.jwt.WebTokenBuilder;
import edu.iu.oidc.IuOidcActor;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcClaimsSource;
import edu.iu.oidc.config.IuOidcClaimsSource.Usage;
import edu.iu.oidc.config.IuOidcClientAuthorization;
import edu.iu.oidc.config.IuOidcClientConfiguration;
import edu.iu.oidc.config.IuOidcClientEndpoint;
import edu.iu.oidc.config.IuOidcClientResource;
import edu.iu.oidc.config.IuOidcClientRole;
import edu.iu.oidc.config.IuOidcClientSource;
import edu.iu.oidc.config.IuOidcIdentitySource;
import edu.iu.oidc.config.IuOidcProviderConfiguration;
import edu.iu.test.IuTestLogger;
import iu.oidc.provider.OidcTokenResult.Error;
import iu.oidc.provider.OidcTokenResult.Issued;

@SuppressWarnings("javadoc")
public class OidcTokenEndpointTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");
	private static final URI REDIRECT = URI.create("https://client.example.iu.edu/cb");
	private static final URI EXTERNAL = URI.create("https://api.example.iu.edu");

	/** Client IDs are URIs, since an ID token names one as its audience. */
	private static final String CLIENT_ID = "https://client.example.iu.edu";

	private static final String SECRET = "hunter2";
	private static final String PRINCIPAL = "someone";

	private static final Duration ACCESS_TTL = Duration.ofMinutes(15L);
	private static final Duration REFRESH_TTL = Duration.ofHours(12L);

	private final IuOidcProviderReference reference = mock(IuOidcProviderReference.class);
	private final IuOidcClientSource clients = mock(IuOidcClientSource.class);
	private final IuOidcClaimsSource claimsSource = mock(IuOidcClaimsSource.class);
	private final IuOidcIdentitySource identitySource = mock(IuOidcIdentitySource.class);
	private final MemoryDataStore data = new MemoryDataStore();
	private final WebKey issuerKey = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral()
			.build();

	private OidcTokenEndpoint endpoint;

	@BeforeEach
	void setup() {
		IuTestLogger.allow(OidcTokenEndpoint.class.getName(), Level.INFO);
		IuTestLogger.allow(OidcTokenEndpoint.class.getName(), Level.FINE);
		IuTestLogger.allow(ClientAuthenticator.class.getName(), Level.FINE);
		IuTestLogger.allow(ClientAuthenticator.class.getName(), Level.FINER);
		IuTestLogger.allow(GrantStore.class.getName(), Level.INFO);
		IuTestLogger.allow("iu.crypt", Level.FINE);

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

			@Override
			public Duration getAccessTokenTimeToLive() {
				return ACCESS_TTL;
			}

			@Override
			public Duration getRefreshTokenTimeToLive() {
				return REFRESH_TTL;
			}
		};

		when(reference.getConfiguration()).thenReturn(configuration);
		when(reference.getClientSource()).thenReturn(clients);
		when(reference.getDataStore()).thenReturn(data);
		when(reference.getClaimsSource()).thenReturn(claimsSource);
		when(reference.getIdentitySource()).thenReturn(identitySource);
		when(reference.isProduction()).thenReturn(false);

		endpoint = new OidcTokenEndpoint(reference);
	}

	/**
	 * Answers a resource entry; a null URI names this provider's own issuer. The
	 * scope is answered as a bare {@link Iterable} rather than a collection, since
	 * that is all a registration promises.
	 */
	private static IuOidcClientResource resource(URI uri, Iterable<String> scope) {
		final var resource = mock(IuOidcClientResource.class);
		when(resource.getUri()).thenReturn(uri);
		when(resource.getScope()).thenReturn(scope == null ? null : IuIterable.of(scope::iterator));
		return resource;
	}

	/** Answers one endpoint registered with a shared secret. */
	private static IuOidcClientEndpoint clientEndpoint(URI redirectUri, Iterable<IuOidcClientResource> resources) {
		final var authorization = mock(IuOidcClientAuthorization.class);
		when(authorization.getJwk())
				.thenReturn(WebKey.builder(Algorithm.HS256).keyId(CLIENT_ID).key(IuText.utf8(SECRET)).build());
		when(authorization.getAssertionTtl()).thenReturn(Duration.ofMinutes(2L));

		final var endpoint = mock(IuOidcClientEndpoint.class);
		doReturn(List.of(authorization)).when(endpoint).getAuthorizations();
		when(endpoint.getRedirectUri()).thenReturn(redirectUri);
		doReturn(resources).when(endpoint).getResources();
		when(endpoint.getAccessRoles()).thenReturn(List.of("all"));
		return endpoint;
	}

	/** Registers one enabled client over the given endpoints. */
	private void register(Iterable<IuOidcClientEndpoint> endpoints) {
		final var client = mock(IuOidcClientConfiguration.class);
		when(client.isEnabled()).thenReturn(true);
		doReturn(endpoints).when(client).getEndpoints();
		when(clients.client(CLIENT_ID)).thenReturn(client);
	}

	/** Registers a client whose one endpoint grants {@code openid} on the issuer. */
	private IuOidcClientEndpoint register() {
		final var clientEndpoint = clientEndpoint(REDIRECT,
				List.of(resource(null, new LinkedHashSet<>(List.of("openid", "offline_access")))));
		register(List.of(clientEndpoint));
		return clientEndpoint;
	}

	/** A request presenting a secret in the body, with nothing else set. */
	private static OidcTokenRequest request(String grantType) {
		final var request = mock(OidcTokenRequest.class);
		when(request.getGrantType()).thenReturn(grantType);
		when(request.getClientId()).thenReturn(CLIENT_ID);
		when(request.getClientSecret()).thenReturn(SECRET);
		// naming the parameter not at all reads differently from naming it emptily,
		// and a mock answers an empty iterable unless told otherwise
		when(request.getResource()).thenReturn(null);
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		return request;
	}

	/** Binds a source writing the named claims onto whatever token it is given. */
	private void sourceWrites(String name, String email) {
		doAnswer(a -> {
			final Set<String> admitted = a.getArgument(1);
			final WebTokenBuilder builder = a.getArgument(2);
			if (name != null && admitted.contains("name"))
				builder.claim("name", name, String.class);
			if (email != null && admitted.contains("email"))
				builder.claim("email", email, String.class);
			return null;
		}).when(claimsSource).claims(any(), any(), any());
	}

	/** Files a grant in the store and answers the reference that redeems it. */
	private String store(String type, OidcGrant grant, Duration ttl) {
		return new GrantStore(data).put(type, ISSUER, issuerKey, ttl, grant);
	}

	/** Answers a grant as an authorization endpoint would have completed one. */
	private static OidcGrant grant(String scope) {
		final var grant = mock(OidcGrant.class);
		when(grant.getPrincipalName()).thenReturn(PRINCIPAL);
		when(grant.getClientId()).thenReturn(CLIENT_ID);
		when(grant.getScope()).thenReturn(scope);
		when(grant.getRedirectUri()).thenReturn(REDIRECT);
		when(grant.getAuthnInstant()).thenReturn(Instant.now().minusSeconds(30L));
		// how request(...) authenticates, so a grant filed directly as a refresh token
		// reads as one a code redemption over the same credential produced; a code
		// redemption answers its own regardless of what the filed grant says
		when(grant.getTokenEndpointAuthMethod()).thenReturn("client_secret_post");
		// a mock answers an unstubbed Iterable with an empty one rather than null,
		// and releasing nothing has to read as nothing
		doReturn(null).when(grant).getReleasedAuthorizationDetails();
		return grant;
	}

	private Error error(OidcTokenRequest request) {
		return assertInstanceOf(Error.class, endpoint.token(request));
	}

	private Issued issued(OidcTokenRequest request) {
		return assertInstanceOf(Issued.class, endpoint.token(request));
	}

	private void assertError(String code, String description, int status, OidcTokenRequest request) {
		final var error = error(request);
		assertEquals(code, error.error());
		assertEquals(description, error.errorDescription());
		assertEquals(status, error.status());
	}

	@Test
	void testAReferenceIsRequired() {
		assertEquals("Missing provider reference",
				assertThrows(NullPointerException.class, () -> new OidcTokenEndpoint(null)).getMessage());
	}

	@Test
	void testAGrantTypeIsRequired() {
		assertError("invalid_request", "Missing grant_type", 400, request(null));
	}

	@Test
	void testAnUnsupportedGrantTypeIsNamedBack() {
		register();
		assertError("unsupported_grant_type", "Unsupported grant_type device_code", 400, request("device_code"));
	}

	@Test
	void testOnlyAnUnauthorizedRefusalCarriesAChallenge() {
		// RFC 6749 answers invalid_client 401, which a transport must accompany with
		// WWW-Authenticate; every other refusal carries none
		assertEquals("Bearer", error(request("client_credentials")).challenge());
		assertNull(error(request(null)).challenge());
	}

	@Test
	void testAMalformedResourceIsRefusedBeforeAnythingElse() {
		final var request = request("client_credentials");
		when(request.getResource()).thenReturn(List.of("not a uri"));
		assertError("invalid_target", "Malformed resource parameter", 400, request);

		when(request.getResource()).thenReturn(Arrays.asList((String) null));
		assertError("invalid_target", "Malformed resource parameter", 400, request);
	}

	@Test
	void testAnAssertionMustNameTheAssertionType() {
		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getClientAssertion()).thenReturn("a.b.c");

		assertError("invalid_request", "Unsupported client_assertion_type; expected " + ClientAuthenticator.JWT_BEARER,
				400, request);
	}

	@Test
	void testOnlyOneCredentialMayBePresented() {
		final var withAssertion = request("client_credentials");
		when(withAssertion.getClientAssertion()).thenReturn("a.b.c");
		when(withAssertion.getClientAssertionType()).thenReturn(ClientAuthenticator.JWT_BEARER);
		assertError("invalid_request", "Present only one client credential", 400, withAssertion);

		when(withAssertion.getClientSecret()).thenReturn(null);
		when(withAssertion.getAuthorization()).thenReturn("Basic abc");
		assertError("invalid_request", "Present only one client credential", 400, withAssertion);

		final var withHeader = request("client_credentials");
		when(withHeader.getAuthorization()).thenReturn("Basic abc");
		assertError("invalid_request", "Present only one client credential", 400, withHeader);
	}

	@Test
	void testOnlyBasicIsUnderstoodInTheAuthorizationHeader() {
		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getAuthorization()).thenReturn("Bearer abc");

		assertError("invalid_client", "Unsupported Authorization scheme", 401, request);
	}

	@Test
	void testABasicCredentialMustDecode() {
		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);

		// not base64
		when(request.getAuthorization()).thenReturn("Basic !!!");
		assertError("invalid_client", "Malformed Basic credential", 401, request);

		// base64, but no separator
		when(request.getAuthorization()).thenReturn("Basic " + IuText.base64(IuText.utf8("nocolon")));
		assertError("invalid_client", "Malformed Basic credential", 401, request);

		// separated, but not form-urlencoded
		when(request.getAuthorization()).thenReturn("Basic " + IuText.base64(IuText.utf8("a%zz:b")));
		assertError("invalid_client", "Malformed Basic credential", 401, request);
	}

	@Test
	void testABasicCredentialNamesItsOwnClient() {
		register();

		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getClientId()).thenReturn(null);
		when(request.getAuthorization()).thenReturn("Basic " + basic(CLIENT_ID, SECRET));

		// the client ID is a URI, so its colon must not be taken for the separator
		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testABasicUsernameAndClientIdMustAgree() {
		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getClientId()).thenReturn("somebody-else");
		when(request.getAuthorization()).thenReturn("Basic " + basic(CLIENT_ID, SECRET));

		assertError("invalid_request", "client_id does not match the Basic credential", 400, request);

		// and passes when the two agree
		when(request.getClientId()).thenReturn(CLIENT_ID);
		register();
		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testAnAssertionNamesItsOwnClient() {
		register();

		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getClientId()).thenReturn(null);
		when(request.getClientAssertion()).thenReturn(assertion());
		when(request.getClientAssertionType()).thenReturn(ClientAuthenticator.JWT_BEARER);
		when(request.getClientAssertionIssuer()).thenReturn(CLIENT_ID);

		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testAnAssertionThatNamesNoIssuerIsMalformed() {
		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getClientAssertion()).thenReturn("a.b.c");
		when(request.getClientAssertionType()).thenReturn(ClientAuthenticator.JWT_BEARER);
		when(request.getClientAssertionIssuer()).thenReturn(null);

		assertError("invalid_client", "Malformed client_assertion", 401, request);
	}

	@Test
	void testAnAssertionIssuerAndClientIdMustAgree() {
		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getClientId()).thenReturn("somebody-else");
		when(request.getClientAssertion()).thenReturn("a.b.c");
		when(request.getClientAssertionType()).thenReturn(ClientAuthenticator.JWT_BEARER);
		when(request.getClientAssertionIssuer()).thenReturn(CLIENT_ID);

		assertError("invalid_request", "client_id does not match the client_assertion issuer", 400, request);

		// and passes when the two agree
		when(request.getClientId()).thenReturn(CLIENT_ID);
		when(request.getClientAssertion()).thenReturn(assertion());
		register();
		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testAPublicRequestStillHasToNameItsClient() {
		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getClientId()).thenReturn(null);

		assertError("invalid_request", "Missing client_id", 400, request);
	}

	@Test
	void testAnUnregisteredClientIsRefusedTheSameWayADisabledOneIs() {
		// a source that refuses, one that answers nothing, a disabled registration,
		// and one registering no endpoint all read the same from outside
		doThrow(new IuOutOfServiceException("down")).when(clients).client(CLIENT_ID);
		assertError("invalid_client", "Unregistered client_id", 401, request("client_credentials"));

		doReturn(null).when(clients).client(CLIENT_ID);
		assertError("invalid_client", "Unregistered client_id", 401, request("client_credentials"));

		final var disabled = mock(IuOidcClientConfiguration.class);
		doReturn(disabled).when(clients).client(CLIENT_ID);
		doReturn(null).when(disabled).getEndpoints();
		assertError("invalid_client", "Unregistered client_id", 401, request("client_credentials"));

		when(disabled.isEnabled()).thenReturn(true);
		assertError("invalid_client", "Unregistered client_id", 401, request("client_credentials"));
	}

	@Test
	void testAResourceNoEndpointRegistersIsNotACredentialFailure() {
		register();

		final var request = request("client_credentials");
		when(request.getResource()).thenReturn(List.of(EXTERNAL.toString()));

		assertError("invalid_target", "Unregistered resource", 400, request);
	}

	@Test
	void testOnlyAnEndpointRegisteringEveryNamedResourceIsEligible() {
		final var partial = clientEndpoint(REDIRECT, List.of(resource(EXTERNAL, Set.of("read"))));
		final var complete = clientEndpoint(URI.create("https://client.example.iu.edu/other"),
				List.of(resource(EXTERNAL, Set.of("read")), resource(null, Set.of("openid"))));
		register(Arrays.asList(null, partial, complete));

		final var request = request("client_credentials");
		when(request.getResource()).thenReturn(List.of(EXTERNAL.toString(), ISSUER.toString()));

		// the first endpoint registers only one of the two, so it is skipped rather
		// than refused
		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testARedirectUriSelectsTheEndpointItIsRegisteredFor() {
		final var wrong = clientEndpoint(URI.create("https://client.example.iu.edu/other"),
				List.of(resource(null, Set.of("openid"))));
		final var right = clientEndpoint(REDIRECT, List.of(resource(null, Set.of("openid"))));
		register(List.of(clientEndpoint(null, List.of()), wrong, right));

		final var request = request("client_credentials");
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());

		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testACredentialThatVerifiesNowhereIsRefused() {
		register();

		final var request = request("client_credentials");
		when(request.getClientSecret()).thenReturn("hunter3");

		assertError("invalid_client", "Client authentication failed", 401, request);
	}

	@Test
	void testClientCredentialsAnswersForTheClientItself() {
		register();

		final var issued = issued(request("client_credentials"));
		assertEquals("Bearer", issued.tokenType());
		assertEquals(ACCESS_TTL.getSeconds(), issued.expiresIn());
		assertEquals("openid offline_access", issued.scope());
		// no end user, so neither an ID token nor a refresh token
		assertNull(issued.idToken());
		assertNull(issued.refreshToken());
		assertNull(issued.authorizationDetails());

		final var token = WebToken.verify(issued.accessToken(), issuerKey);
		assertEquals(CLIENT_ID, token.getSubject());
		assertEquals(ISSUER, token.getIssuer());
	}

	@Test
	void testARequestedScopeNarrowsWhatClientCredentialsGrants() {
		register();

		final var request = request("client_credentials");
		when(request.getScope()).thenReturn("openid");

		assertEquals("openid", issued(request).scope());
	}

	@Test
	void testANamedResourceThatGrantsNothingIsRefused() {
		register(List.of(clientEndpoint(REDIRECT, List.of(resource(null, Set.of("openid"))))));

		final var request = request("client_credentials");
		when(request.getResource()).thenReturn(List.of(ISSUER.toString()));
		when(request.getScope()).thenReturn("write");

		assertError("invalid_target", "No scope granted for the requested resource", 400, request);
	}

	@Test
	void testAnEndpointRegisteringNoResourceHasNoAudienceToName() {
		register(List.of(clientEndpoint(REDIRECT, List.of())));

		assertError("invalid_target", "No resource configured for the granted scope", 400,
				request("client_credentials"));
	}

	@Test
	void testAnAuthorizationCodeNamesBothACodeAndTheUriItWasIssuedTo() {
		register();

		final var request = request("authorization_code");
		assertError("invalid_request", "Missing code", 400, request);

		when(request.getCode()).thenReturn("nonsense");
		assertError("invalid_request", "Missing redirect_uri", 400, request);
	}

	@Test
	void testACodeThatDoesntRedeemIsRefused() {
		register();

		final var request = request("authorization_code");
		when(request.getCode()).thenReturn(IuText.base64Url(IuDigest.sha256(IuText.utf8("nonsense"))));
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());

		assertError("invalid_grant", "Invalid or expired " + GrantStore.CODE + " reference", 400, request);
	}

	@Test
	void testACodeIssuedToAnotherClientIsRefused() {
		register();

		final var grant = grant("openid");
		when(grant.getClientId()).thenReturn("somebody-else");

		final var request = request("authorization_code");
		final var code = store(GrantStore.CODE, grant, ACCESS_TTL);
		when(request.getCode()).thenReturn(code);
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());

		assertError("invalid_grant", "Authorization code was issued to a different client", 400, request);
	}

	@Test
	void testARefreshTokenIsRequiredAndMustNameItsOwnClient() {
		register();

		final var request = request("refresh_token");
		assertError("invalid_request", "Missing refresh_token", 400, request);

		final var grant = grant("openid");
		when(grant.getClientId()).thenReturn("somebody-else");
		final var refreshToken = store(GrantStore.REFRESH, grant, ACCESS_TTL);
		when(request.getRefreshToken()).thenReturn(refreshToken);

		assertError("invalid_grant", "Refresh token was issued to a different client", 400, request);
	}

	@Test
	void testAPkceChallengeIsEitherSatisfiedOrAbsentFromBothSides() {
		register();
		claimsHoldNothing();

		final var verifier = IdGenerator.generateId();
		final var challenge = IuText.base64Url(IuDigest.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));

		// a verifier against a code that recorded no challenge
		final var noChallenge = request("authorization_code");
		when(noChallenge.getRedirectUri()).thenReturn(REDIRECT.toString());
		final var plainCode = store(GrantStore.CODE, grant("openid"), ACCESS_TTL);
		when(noChallenge.getCode()).thenReturn(plainCode);
		when(noChallenge.getCodeVerifier()).thenReturn(verifier);
		assertError("invalid_grant", "No code_challenge was recorded for this code", 400, noChallenge);

		final var challenged = grant("openid");
		when(challenged.getCodeChallenge()).thenReturn(challenge);

		final var missing = request("authorization_code");
		when(missing.getRedirectUri()).thenReturn(REDIRECT.toString());
		final var missingCode = store(GrantStore.CODE, challenged, ACCESS_TTL);
		when(missing.getCode()).thenReturn(missingCode);
		assertError("invalid_grant", "Missing code_verifier", 400, missing);

		final var wrong = request("authorization_code");
		when(wrong.getRedirectUri()).thenReturn(REDIRECT.toString());
		final var wrongCode = store(GrantStore.CODE, challenged, ACCESS_TTL);
		when(wrong.getCode()).thenReturn(wrongCode);
		when(wrong.getCodeVerifier()).thenReturn(IdGenerator.generateId());
		assertError("invalid_grant", "code_verifier does not satisfy the recorded code_challenge", 400, wrong);

		final var right = request("authorization_code");
		when(right.getRedirectUri()).thenReturn(REDIRECT.toString());
		final var rightCode = store(GrantStore.CODE, challenged, ACCESS_TTL);
		when(right.getCode()).thenReturn(rightCode);
		when(right.getCodeVerifier()).thenReturn(verifier);
		assertNotNull(issued(right).accessToken());
	}

	/** Registers one endpoint that verifies nothing a client presents. */
	private IuOidcClientEndpoint registerPublic() {
		final var authorization = mock(IuOidcClientAuthorization.class);
		// no key at all is what makes a registration public, as distinct from one
		// registering no authorization, which accepts nothing
		doReturn(null).when(authorization).getJwk();

		// built before the stubbing below, since a mock created inside a when(...)
		// argument leaves the outer stubbing unfinished
		final var resources = List.of(resource(null, new LinkedHashSet<>(List.of("openid", "offline_access"))));

		final var clientEndpoint = mock(IuOidcClientEndpoint.class);
		doReturn(List.of(authorization)).when(clientEndpoint).getAuthorizations();
		when(clientEndpoint.getRedirectUri()).thenReturn(REDIRECT);
		doReturn(resources).when(clientEndpoint).getResources();
		when(clientEndpoint.getAccessRoles()).thenReturn(List.of("all"));

		register(List.of(clientEndpoint));
		return clientEndpoint;
	}

	/** A request from a public client, which presents no credential at all. */
	private static OidcTokenRequest publicRequest(String grantType) {
		final var request = request(grantType);
		when(request.getClientSecret()).thenReturn(null);
		return request;
	}

	@Test
	void testAPublicClientMustRedeemACodeWithPkce() {
		// RFC 9700 §2.1.1. Nothing was verified about who is presenting this code, so
		// the verifier is the only thing tying the caller to the request that began it
		registerPublic();
		claimsHoldNothing();

		final var request = publicRequest("authorization_code");
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());
		final var code = store(GrantStore.CODE, grant("openid"), ACCESS_TTL);
		when(request.getCode()).thenReturn(code);

		assertError("invalid_grant", "A public client must redeem an authorization code with PKCE", 400, request);
	}

	@Test
	void testAPublicClientRedeemsACodeThatCarriedPkce() {
		// so the refusal above is about the missing challenge, not about being public
		registerPublic();
		claimsHoldNothing();

		final var verifier = IdGenerator.generateId();
		final var challenged = grant("openid");
		when(challenged.getCodeChallenge())
				.thenReturn(IuText.base64Url(IuDigest.sha256(verifier.getBytes(StandardCharsets.US_ASCII))));

		final var request = publicRequest("authorization_code");
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());
		final var code = store(GrantStore.CODE, challenged, ACCESS_TTL);
		when(request.getCode()).thenReturn(code);
		when(request.getCodeVerifier()).thenReturn(verifier);

		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testAConfidentialClientStillRedeemsACodeWithoutPkce() {
		// PKCE stays optional for a client that authenticated: what the gate reads is
		// the authentication method, not the grant type
		register();
		claimsHoldNothing();

		final var request = request("authorization_code");
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());
		final var code = store(GrantStore.CODE, grant("openid"), ACCESS_TTL);
		when(request.getCode()).thenReturn(code);

		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testAPublicClientCannotUseClientCredentials() {
		// RFC 6749 §4.4 has no unauthenticated form: the client is the resource owner,
		// so the credential is the whole authorization and client_id alone is not one
		registerPublic();

		assertError("invalid_client", "client_credentials requires an authenticated client", 401,
				publicRequest("client_credentials"));
	}

	@Test
	void testACodeRecordingNoRedirectUriRedeemsNowhere() {
		// nothing to compare the presented value against, so there is no way to
		// establish it is the one the code was issued to -- which is the check, not a
		// formality to skip when the grant is silent
		register();

		final var grant = grant("openid");
		doReturn(null).when(grant).getRedirectUri();

		final var request = request("authorization_code");
		final var code = store(GrantStore.CODE, grant, ACCESS_TTL);
		when(request.getCode()).thenReturn(code);
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());

		assertError("invalid_grant", "redirect_uri does not match the authorization request", 400, request);
	}

	@Test
	void testACodeIsRedeemedOnlyAtTheRedirectUriItWasIssuedTo() {
		// RFC 6749 §4.1.3. Endpoint eligibility matched this value against a
		// registration, which is not the same as matching the one the code came from --
		// a client registering both would otherwise be able to cross them
		final var other = URI.create("https://client.example.iu.edu/staging");
		register(List.of(clientEndpoint(REDIRECT, List.of(resource(null, Set.of("openid")))),
				clientEndpoint(other, List.of(resource(null, Set.of("openid"))))));
		claimsHoldNothing();

		final var request = request("authorization_code");
		final var code = store(GrantStore.CODE, grant("openid"), ACCESS_TTL);
		when(request.getCode()).thenReturn(code);
		when(request.getRedirectUri()).thenReturn(other.toString());

		assertError("invalid_grant", "redirect_uri does not match the authorization request", 400, request);
	}

	@Test
	void testACodeGrantIssuesAnIdTokenForTheEndUser() {
		register();
		sourceWrites("Some One", "someone@iu.edu");

		// profile and email are what admit these two; openid alone would not, and the
		// source writes nothing it wasn't told it could
		final var grant = grant("openid profile email");
		when(grant.getNonce()).thenReturn("the-nonce");
		when(grant.getAuthnAuthority()).thenReturn("https://idp.iu.edu");

		final var issued = issued(codeRequest(grant));
		assertNotNull(issued.idToken());

		final var idToken = WebToken.verify(issued.idToken(), issuerKey);
		assertEquals(PRINCIPAL, idToken.getSubject());
		assertIterableEquals(List.of(URI.create(CLIENT_ID)), idToken.getAudience());
		assertEquals("the-nonce", idToken.getNonce());
		assertEquals("Some One", idToken.getClaim("name", String.class));
		assertEquals("someone@iu.edu", idToken.getClaim("email", String.class));
		assertEquals("https://idp.iu.edu", idToken.getClaim("acr", String.class));
		assertNotNull(idToken.getClaim("at_hash", String.class));
		assertNotNull(idToken.getClaim("auth_time", Long.class));
	}

	@Test
	void testAcrNamesWhoAuthenticatedTheSubjectAndIsAbsentWhenNobodyDid() {
		// RFC 9700 §4.15: nothing stops a client_id from reading like a principal
		// name, so a resource server cannot tell an end user from a client by looking
		// at sub. acr settles it, and its absence carries as much as its value
		register();
		claimsHoldNothing();

		final var grant = grant("openid");
		when(grant.getAuthnAuthority()).thenReturn("https://idp.iu.edu");

		// an end user names the authority that authenticated them, on the access token
		// as well as the ID token -- the access token is the one a resource server
		// reads, so it is the one that has to be legible
		final var issued = issued(codeRequest(grant));
		assertEquals("https://idp.iu.edu",
				WebToken.verify(issued.accessToken(), issuerKey).getClaim("acr", String.class));
		assertEquals("https://idp.iu.edu",
				WebToken.verify(issued.idToken(), issuerKey).getClaim("acr", String.class));

		// client_credentials answers for the client itself: nobody authenticated, so
		// there is no authority to name and sub reads as a client
		final var clientCredentials = WebToken.verify(issued(request("client_credentials")).accessToken(), issuerKey);
		assertEquals(CLIENT_ID, clientCredentials.getSubject());
		assertNull(clientCredentials.getClaim("acr", String.class));
	}

	@Test
	void testAnIdTokenSaysNothingTheScopeDoesntAdmit() {
		register();
		// openid alone admits sub and nothing else, so the source is asked for sub
		// alone and answers nothing more
		sourceWrites(null, null);

		final var grant = grant("openid");
		final var idToken = WebToken.verify(issued(codeRequest(grant)).idToken(), issuerKey);

		assertEquals(PRINCIPAL, idToken.getSubject());
		assertNull(idToken.getClaim("name", String.class));
		assertNull(idToken.getClaim("email", String.class));
		assertNull(idToken.getClaim("acr", String.class));

		// a grant that recorded no authentication instant claims no auth_time
		final var undated = grant("openid");
		when(undated.getAuthnInstant()).thenReturn(null);
		assertNull(WebToken.verify(issued(codeRequest(undated)).idToken(), issuerKey).getClaim("auth_time",
				Long.class));
	}

	@Test
	void testNoIdTokenWithoutOpenid() {
		register(List.of(clientEndpoint(REDIRECT, List.of(resource(null, Set.of("read"))))));
		claimsHoldNothing();

		final var issued = issued(codeRequest(grant("read")));
		assertNull(issued.idToken());
		assertNotNull(issued.accessToken());

		// no openid, so §5.4 admits nothing at all, and read names nothing the source
		// releases either -- with no claim to write, it is never asked to write one
		verify(claimsSource).admitted(Set.of("read"), Usage.ID_TOKEN);
		verify(claimsSource).admitted(Set.of("read"), Usage.ACCESS_TOKEN);
		verify(claimsSource, never()).claims(any(), any(), any());
	}

	@Test
	void testAScopeOpenIdConnectDoesntDefineIsTheSourcesToAnswerFor() {
		register(List.of(clientEndpoint(REDIRECT, List.of(resource(null, Set.of("openid", "read"))))));
		claimsHoldNothing();
		when(claimsSource.admitted(Set.of("read"), Usage.ID_TOKEN)).thenReturn(Set.of("affiliation"));

		issued(codeRequest(grant("openid read")));

		// only what §5.4 leaves over reaches the source, and it is asked under the
		// destination -- an ID token is kept, where a UserInfo response is fetched
		verify(claimsSource).admitted(Set.of("read"), Usage.ID_TOKEN);
		verify(claimsSource).claims(eq(PRINCIPAL), eq(Set.of("sub", "affiliation")), any());
	}

	@Test
	void testAnAccessTokenCarriesWhatTheDeploymentReleasesAndNoStandardClaim() {
		register(List.of(clientEndpoint(REDIRECT, List.of(resource(null, Set.of("openid", "profile", "read"))))));
		sourceWrites("Some One", "someone@iu.edu");
		when(claimsSource.admitted(Set.of("read"), Usage.ACCESS_TOKEN)).thenReturn(Set.of("name"));

		final var accessToken = WebToken.verify(issued(codeRequest(grant("openid profile read"))).accessToken(),
				issuerKey);

		// RFC 9068 defines no claim describing the end user, so profile puts nothing on
		// an access token; what the deployment names for a scope of its own does
		assertEquals("Some One", accessToken.getClaim("name", String.class));
		assertNull(accessToken.getClaim("email", String.class));
		verify(claimsSource).claims(eq(PRINCIPAL), eq(Set.of("name")), any());
	}

	@Test
	void testAnAccessTokenIsNotAskedForWhenTheDeploymentReleasesNothing() {
		register();
		claimsHoldNothing();

		issued(codeRequest(grant("openid")));

		// the §5.4 sets never reach an access token, so with nothing of the
		// deployment's own admitted there is no access-token claim to ask for
		verify(claimsSource).admitted(Set.of(), Usage.ACCESS_TOKEN);
		verify(claimsSource, never()).claims(eq(PRINCIPAL), eq(Set.of()), any());
	}

	@Test
	void testClientCredentialsNeverAsksAboutAnEndUser() {
		// no principal to ask about, so a deployment issuing these alone never needs a
		// claims source bound -- the default refuses by name
		register();
		issued(request("client_credentials"));
		verifyNoInteractions(claimsSource);
	}

	@Test
	void testARefreshTokenIsIssuedWhileTheAuthenticationIsYoungEnough() {
		register();
		claimsHoldNothing();

		final var fresh = grant("openid offline_access");
		assertNotNull(issued(codeRequest(fresh)).refreshToken());

		// one that couldn't outlive even one more access token isn't worth issuing
		final var stale = grant("openid offline_access");
		when(stale.getAuthnInstant()).thenReturn(Instant.now().minus(REFRESH_TTL).plusSeconds(60L));
		assertNull(issued(codeRequest(stale)).refreshToken());
	}

	@Test
	void testARefreshTokenRedeemsTheSameGrant() {
		register();
		claimsHoldNothing();

		final var request = request("refresh_token");
		final var refreshToken = store(GrantStore.REFRESH, grant("openid"), ACCESS_TTL);
		when(request.getRefreshToken()).thenReturn(refreshToken);

		assertNotNull(issued(request).idToken());
	}

	/**
	 * Registers one endpoint accepting either its shared secret or nothing at all,
	 * as one moving a client between the two might.
	 */
	private void registerSecretOrPublic() {
		final var clientEndpoint = register();
		final var keyed = clientEndpoint.getAuthorizations().iterator().next();
		final var publicRecord = mock(IuOidcClientAuthorization.class);
		doReturn(null).when(publicRecord).getJwk();
		doReturn(List.of(keyed, publicRecord)).when(clientEndpoint).getAuthorizations();
	}

	/** Redeems a freshly filed code over request(...) and answers its refresh token. */
	private String refreshTokenFromCode() {
		final var grant = grant("openid offline_access");
		// what an authorization endpoint files: no code has been redeemed for it yet
		when(grant.getTokenEndpointAuthMethod()).thenReturn(null);
		return assertInstanceOf(String.class, issued(codeRequest(grant)).refreshToken());
	}

	@Test
	void testARefreshTokenCarriesHowItsCodeWasRedeemedThroughEveryRotation() {
		registerSecretOrPublic();
		claimsHoldNothing();

		final var refreshToken = refreshTokenFromCode();
		final var first = request("refresh_token");
		when(first.getRefreshToken()).thenReturn(refreshToken);
		final var rotated = issued(first).refreshToken();

		// filed from the grant it redeemed, so the method rides along without the
		// code redemption having to happen again
		final var second = request("refresh_token");
		when(second.getRefreshToken()).thenReturn(rotated);
		assertNotNull(issued(second).accessToken());
	}

	@Test
	void testARefreshTokenACodeRedeemedWithASecretBeganIsNotRedeemedWithNothing() {
		// the endpoint would accept a bare request, and the refresh token is a bearer
		// value; the method it was bound to is what refuses the line to whoever holds
		// a copy of it without the secret
		registerSecretOrPublic();
		claimsHoldNothing();

		final var refreshToken = refreshTokenFromCode();
		final var bare = publicRequest("refresh_token");
		when(bare.getRefreshToken()).thenReturn(refreshToken);
		assertError("invalid_grant",
				"Refresh token must be redeemed with the authentication method its code was redeemed with", 400,
				bare);
	}

	@Test
	void testARefreshTokenIsBoundToTheExactMethodNotJustAnyCredential() {
		registerSecretOrPublic();
		claimsHoldNothing();

		final var refreshToken = refreshTokenFromCode();

		// the same secret, presented the other way RFC 6749 §2.3.1 allows
		final var request = request("refresh_token");
		when(request.getClientSecret()).thenReturn(null);
		when(request.getAuthorization()).thenReturn("Basic " + basic(CLIENT_ID, SECRET));
		when(request.getRefreshToken()).thenReturn(refreshToken);
		assertError("invalid_grant",
				"Refresh token must be redeemed with the authentication method its code was redeemed with", 400,
				request);
	}

	@Test
	void testARefreshTokenRecordingNoMethodIsRefused() {
		// filed before the method was recorded: nothing says it matched, so it isn't
		// trusted to have
		register();
		claimsHoldNothing();

		final var unbound = grant("openid");
		when(unbound.getTokenEndpointAuthMethod()).thenReturn(null);

		final var refreshToken = store(GrantStore.REFRESH, unbound, ACCESS_TTL);
		final var request = request("refresh_token");
		when(request.getRefreshToken()).thenReturn(refreshToken);
		assertError("invalid_grant",
				"Refresh token must be redeemed with the authentication method its code was redeemed with", 400,
				request);
	}

	@Test
	void testAPublicClientRefreshesALineItBeganWithPkce() {
		registerPublic();
		claimsHoldNothing();

		final var verifier = IdGenerator.generateId();
		final var challenged = grant("openid offline_access");
		when(challenged.getTokenEndpointAuthMethod()).thenReturn(null);
		when(challenged.getCodeChallenge())
				.thenReturn(IuText.base64Url(IuDigest.sha256(verifier.getBytes(StandardCharsets.US_ASCII))));

		final var code = store(GrantStore.CODE, challenged, ACCESS_TTL);
		final var redeem = publicRequest("authorization_code");
		when(redeem.getRedirectUri()).thenReturn(REDIRECT.toString());
		when(redeem.getCode()).thenReturn(code);
		when(redeem.getCodeVerifier()).thenReturn(verifier);
		final var refreshToken = issued(redeem).refreshToken();
		assertNotNull(refreshToken);

		final var refresh = publicRequest("refresh_token");
		when(refresh.getRefreshToken()).thenReturn(refreshToken);
		assertNotNull(issued(refresh).accessToken());
	}

	@Test
	void testARedemptionMayNarrowTheAudienceButNeverWidenIt() {
		register(List.of(clientEndpoint(REDIRECT,
				List.of(resource(null, Set.of("openid")), resource(EXTERNAL, Set.of("openid"))))));
		claimsHoldNothing();

		final var bounded = grant("openid");
		when(bounded.getResource()).thenReturn(new String[] { ISSUER.toString() });

		// naming a subset of what the grant recorded narrows it
		final var narrowing = codeRequest(bounded);
		when(narrowing.getResource()).thenReturn(List.of(ISSUER.toString()));
		assertNotNull(issued(narrowing).accessToken());

		// naming one the grant never authorized is refused rather than widening it
		final var widening = codeRequest(bounded);
		when(widening.getResource()).thenReturn(List.of(EXTERNAL.toString()));
		assertError("invalid_target", "resource was not authorized when this grant was issued", 400, widening);

		// a request naming none is bounded by what the grant recorded
		assertNotNull(issued(codeRequest(bounded)).accessToken());

		// a grant recording nothing imposes no bound of its own
		final var unbounded = grant("openid");
		when(unbounded.getResource()).thenReturn(new String[0]);
		assertNotNull(issued(codeRequest(unbounded)).accessToken());
	}

	@Test
	void testAPrincipalWithoutAnAccessRoleGetsNoToken() {
		final var clientEndpoint = register();
		when(clientEndpoint.getAccessRoles()).thenReturn(List.of("staff"));
		when(identitySource.hasRole(PRINCIPAL, "staff")).thenReturn(false);

		assertError("access_denied", "Not authorized for this endpoint", 403, codeRequest(grant("openid")));
	}

	@Test
	void testARoleNamingEveryoneOrThePrincipalNeedsNoLookup() {
		final var clientEndpoint = register();
		claimsHoldNothing();

		// "all", the principal's own name, and a null entry are all settled here
		when(clientEndpoint.getAccessRoles()).thenReturn(Arrays.asList(null, "ALL"));
		assertNotNull(issued(codeRequest(grant("openid"))).accessToken());

		when(clientEndpoint.getAccessRoles()).thenReturn(List.of(PRINCIPAL));
		assertNotNull(issued(codeRequest(grant("openid"))).accessToken());

		verify(identitySource, never()).hasRole(any(), any());
	}

	@Test
	void testAnEndpointAdmittingNobodyAdmitsNobody() {
		final var clientEndpoint = register();

		// no roles at all, and a list of nothing but nulls, both admit no one
		when(clientEndpoint.getAccessRoles()).thenReturn(null);
		assertError("access_denied", "Not authorized for this endpoint", 403, codeRequest(grant("openid")));

		when(clientEndpoint.getAccessRoles()).thenReturn(Arrays.asList((String) null));
		assertError("access_denied", "Not authorized for this endpoint", 403, codeRequest(grant("openid")));
	}

	@Test
	void testAPrincipalTheIdentitySourceCantResolveIsABadRequest() {
		final var clientEndpoint = register();
		when(clientEndpoint.getAccessRoles()).thenReturn(List.of("staff"));
		when(identitySource.hasRole(PRINCIPAL, "staff")).thenThrow(new IllegalArgumentException("who?"));

		assertError("invalid_request", "Invalid principal name", 400, codeRequest(grant("openid")));
	}

	@Test
	void testApplicationRolesRideAlongOnBothTokens() {
		final var clientEndpoint = register();
		claimsHoldNothing();

		final var role = mock(IuOidcClientRole.class);
		when(role.getRole()).thenReturn("editor");
		when(role.getIdRoles()).thenReturn(List.of("staff"));
		when(identitySource.hasRole(PRINCIPAL, "staff")).thenReturn(true);

		final var unmatched = mock(IuOidcClientRole.class);
		when(unmatched.getIdRoles()).thenReturn(List.of("faculty"));

		doReturn(Arrays.asList(null, role, unmatched)).when(clientEndpoint).getRoles();

		final var issued = issued(codeRequest(grant("openid")));
		final var accessToken = WebToken.verify(issued.accessToken(), issuerKey);
		assertArrayEquals(new String[] { "editor" }, (String[]) accessToken.getClaim("roles", String[].class));

		final var idToken = WebToken.verify(issued.idToken(), issuerKey);
		assertArrayEquals(new String[] { "editor" }, (String[]) idToken.getClaim("roles", String[].class));
	}

	@Test
	void testAnEndpointDeclaringNoApplicationRolesAddsNone() {
		register();
		claimsHoldNothing();

		// declaring none at all, and declaring an empty list, both add none
		final var accessToken = WebToken.verify(issued(codeRequest(grant("openid"))).accessToken(), issuerKey);
		assertNull(accessToken.getClaim("roles", String[].class));

		final var reregistered = register();
		doReturn(null).when(reregistered).getRoles();
		final var neither = WebToken.verify(issued(codeRequest(grant("openid"))).accessToken(), issuerKey);
		assertNull(neither.getClaim("roles", String[].class));
	}

	@Test
	void testAClaimsSourceThatRefusesOrAnswersNothingIsABadRequest() {
		register();

		doThrow(new IuOutOfServiceException("down")).when(claimsSource).claims(any(), any(), any());
		assertError("invalid_request", "Invalid principal name", 400, codeRequest(grant("openid")));
	}

	@Test
	void testATokenIsEncryptedWhenTheEndpointRegistersAKeyToEncryptTo() {
		final var audienceKey = WebKey.builder(Algorithm.ECDH_ES).keyId("client-key").ephemeral().build();
		final var clientEndpoint = register();
		when(clientEndpoint.getEncryptJwk()).thenReturn(audienceKey);
		when(clientEndpoint.getEnc()).thenReturn(Encryption.A256GCM);
		claimsHoldNothing();

		final var issued = issued(codeRequest(grant("openid")));
		// five segments rather than three: the signature is inside the encryption
		assertEquals(5, issued.accessToken().split("\\.").length);
		assertEquals(5, issued.idToken().split("\\.").length);

		// a key with no content encryption registered alongside it encrypts nothing
		when(clientEndpoint.getEnc()).thenReturn(null);
		assertEquals(3, issued(codeRequest(grant("openid"))).accessToken().split("\\.").length);
	}

	@Test
	void testReleasedAuthorizationDetailsRideAlongOnBothTokens() {
		register();
		claimsHoldNothing();

		final var grant = grant("openid");
		doReturn(Arrays.asList(null, (IuAuthorizationDetails) () -> "record")).when(grant)
				.getReleasedAuthorizationDetails();

		final var issued = issued(codeRequest(grant));

		// RFC 9396 00a77 has the response state what was granted, since it may be
		// narrower than what was asked for
		assertIterableEquals(List.of("record"),
				IuIterable.map(IuIterable.filter(issued.authorizationDetails(), d -> d != null),
						IuAuthorizationDetails::getType));

		// a resource server reads the access token, a relying party the ID token
		assertIterableEquals(List.of("record"),
				IuIterable.map(WebToken.verify(issued.accessToken(), issuerKey)
						.getAuthorizationDetails(IuAuthorizationDetails.class, "record"),
						IuAuthorizationDetails::getType));
		assertIterableEquals(List.of("record"),
				IuIterable.map(WebToken.verify(issued.idToken(), issuerKey)
						.getAuthorizationDetails(IuAuthorizationDetails.class, "record"),
						IuAuthorizationDetails::getType));
	}

	/** Signs an access token of this provider's own shape. */
	private String accessToken(String sub, String clientId, String scope, Instant authTime, URI... audience) {
		return accessToken(sub, clientId, scope, authTime, null, audience);
	}

	/** An access token this provider issued, naming the authority that authenticated. */
	private String accessToken(String sub, String clientId, String scope, Instant authTime, String acr,
			URI... audience) {
		final var builder = WebToken.builder() //
				.jti() //
				.iss(ISSUER) //
				.sub(sub) //
				.aud(audience) //
				.iat() //
				.exp(Instant.now().plus(ACCESS_TTL)) //
				.claim("client_id", clientId, String.class) //
				.claim("scope", scope, String.class);

		if (authTime != null)
			builder.claim("auth_time", authTime.getEpochSecond(), Long.class);

		if (acr != null)
			builder.claim("acr", acr, String.class);

		return OidcJose.sign(builder.build().toString(), "at+jwt", issuerKey);
	}

	/** An actor token as a plain code redemption would have issued one. */
	private String actorToken() {
		return accessToken(PRINCIPAL, CLIENT_ID, "openid", Instant.now().minusSeconds(30L), ISSUER);
	}

	/** An exchange asking to answer for {@code subject} on the strength of a token. */
	private static OidcTokenRequest exchangeRequest(String subject, String actorToken) {
		final var request = request(TOKEN_EXCHANGE);
		when(request.getSubjectToken()).thenReturn(subject);
		when(request.getSubjectTokenType()).thenReturn(PRINCIPAL_NAME_TOKEN_TYPE);
		when(request.getActorToken()).thenReturn(actorToken);
		when(request.getActorTokenType()).thenReturn(ACCESS_TOKEN_TOKEN_TYPE);
		return request;
	}

	/** Registers a client whose endpoint opens the backdoor to a named role. */
	private IuOidcClientEndpoint registerBackdoor() {
		final var clientEndpoint = register();
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("support"));
		when(identitySource.hasRole(PRINCIPAL, "support")).thenReturn(true);
		return clientEndpoint;
	}

	/** Stands in for an act claim already present on a presented token. */
	private record TestActor(String sub) implements IuOidcActor {
		@Override
		public String getSub() {
			return sub;
		}

		@Override
		public String getName() {
			return null;
		}

		@Override
		public String getEmail() {
			return null;
		}

		@Override
		public Long getAuthTime() {
			return null;
		}

		@Override
		public String getAcr() {
			return null;
		}
	}

	@Test
	void testAnExchangeMustNameASubjectToken() {
		register();
		assertError("invalid_request", "Missing subject_token", 400, exchangeRequest(null, actorToken()));
	}

	@Test
	void testAnExchangeMustNameASubjectTokenType() {
		register();
		final var request = exchangeRequest("somebody-else", actorToken());
		when(request.getSubjectTokenType()).thenReturn(null);
		assertError("invalid_request", "Missing subject_token_type", 400, request);
	}

	@Test
	void testOnlyAPrincipalNameSubjectTokenIsAnswered() {
		// nobody holds a token for the party being impersonated, which is the point
		// of asking; a caller presenting one is asking for something else entirely
		register();
		final var request = exchangeRequest("somebody-else", actorToken());
		when(request.getSubjectTokenType()).thenReturn(ACCESS_TOKEN_TOKEN_TYPE);
		assertError("invalid_request", "Unsupported subject_token_type " + ACCESS_TOKEN_TOKEN_TYPE, 400, request);
	}

	@Test
	void testAnExchangeMustNameAnActorToken() {
		register();
		assertError("invalid_request", "Missing actor_token", 400, exchangeRequest("somebody-else", null));
	}

	@Test
	void testAnExchangeMustNameAnActorTokenType() {
		register();
		final var request = exchangeRequest("somebody-else", actorToken());
		when(request.getActorTokenType()).thenReturn(null);
		assertError("invalid_request", "Missing actor_token_type", 400, request);
	}

	@Test
	void testOnlyAnAccessTokenActsAsTheActorToken() {
		register();
		final var request = exchangeRequest("somebody-else", actorToken());
		when(request.getActorTokenType()).thenReturn("urn:ietf:params:oauth:token-type:id_token");
		assertError("invalid_request", "Unsupported actor_token_type urn:ietf:params:oauth:token-type:id_token", 400,
				request);
	}

	@Test
	void testAnExchangeMayAskForAnAccessTokenExplicitly() {
		registerBackdoor();
		claimsForAnyone();
		final var request = exchangeRequest("somebody-else", actorToken());
		when(request.getRequestedTokenType()).thenReturn(ACCESS_TOKEN_TOKEN_TYPE);
		assertNotNull(issued(request).accessToken());
	}

	@Test
	void testATokenTypeThisProviderDoesNotIssueIsRefused() {
		// answering with something the client didn't ask for is worse than refusing
		register();
		final var request = exchangeRequest("somebody-else", actorToken());
		when(request.getRequestedTokenType()).thenReturn("urn:ietf:params:oauth:token-type:saml2");
		assertError("invalid_request", "Unsupported requested_token_type urn:ietf:params:oauth:token-type:saml2", 400,
				request);
	}

	@Test
	void testAnActorTokenThisProviderCantVerifyIsRefused() {
		register();
		assertError("invalid_grant", "actor_token is not a valid access token addressed to this provider", 400,
				exchangeRequest("somebody-else", "not.a.token"));
	}

	@Test
	void testAnActorTokenAddressedElsewhereIsRefused() {
		// one issued for an external API resource was never addressed to this
		// provider, so a signature alone is not enough to honor it here
		register();
		assertError("invalid_grant", "actor_token is not a valid access token addressed to this provider", 400,
				exchangeRequest("somebody-else", accessToken(PRINCIPAL, CLIENT_ID, "openid", null, EXTERNAL)));
	}

	@Test
	void testAnActorTokenBelongingToAnotherClientIsRefused() {
		register();
		assertError("invalid_grant", "actor_token was issued to a different client", 400,
				exchangeRequest("somebody-else", accessToken(PRINCIPAL, "other-client", "openid", null, ISSUER)));
	}

	@Test
	void testAnExchangeCannotBeChained() {
		// RFC 8693 wants the previous actor nested inside the new one, and the act
		// claim has nowhere to put it, so chaining is refused rather than answered
		// with a token that drops who was really behind the one before it
		register();
		final var chained = WebToken.builder().jti().iss(ISSUER).sub("somebody-else").aud(ISSUER).iat()
				.exp(Instant.now().plus(ACCESS_TTL)) //
				.claim("client_id", CLIENT_ID, String.class) //
				.claim("scope", "openid", String.class) //
				.claim("act", (IuOidcActor) new TestActor(PRINCIPAL), IuOidcActor.class);

		assertError("invalid_grant", "actor_token already names an actor", 400, exchangeRequest("a-third-party",
				OidcJose.sign(chained.build().toString(), "at+jwt", issuerKey)));
	}

	@Test
	void testAnExchangeCannotAnswerForItsOwnActor() {
		// grants nothing the caller doesn't already hold, and would make an exchange
		// a way to renew a token past the age its authentication was good for
		register();
		assertError("invalid_grant", "actor_token already answers for this subject", 400,
				exchangeRequest(PRINCIPAL, actorToken()));
	}

	@Test
	void testAPublicClientCannotExchangeATokenForAnother() {
		// everything else an exchange needs is in place, so the refusal is about the
		// missing client credential: the actor_token would be the only one presented
		final var clientEndpoint = registerPublic();
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("support"));
		when(identitySource.hasRole(PRINCIPAL, "support")).thenReturn(true);
		claimsForAnyone();

		final var request = exchangeRequest("somebody-else", actorToken());
		when(request.getClientSecret()).thenReturn(null);
		assertError("invalid_client", "Token exchange requires an authenticated client", 401, request);
	}

	@Test
	void testAnExchangeIsRefusedInProduction() {
		when(reference.isProduction()).thenReturn(true);
		IuTestLogger.expect(OidcTokenEndpoint.class.getName(), Level.WARNING,
				"token-impersonation-denied:production:" + CLIENT_ID + ":" + PRINCIPAL);

		registerBackdoor();
		claimsForAnyone();

		// refused rather than quietly answered for the caller: a client that asked
		// for somebody else's token must not be handed its own without being told
		assertError("access_denied", "Token exchange is not available in this deployment", 403,
				exchangeRequest("somebody-else", actorToken()));
	}

	@Test
	void testAnExchangeNeedsABackdoorRole() {
		final var clientEndpoint = register();
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("support"));
		when(identitySource.hasRole(PRINCIPAL, "support")).thenReturn(false);
		claimsHoldNothing();

		IuTestLogger.expect(OidcTokenEndpoint.class.getName(), Level.WARNING,
				"token-impersonation-denied:norole:" + CLIENT_ID + ":" + PRINCIPAL);
		assertError("access_denied", "Not authorized to impersonate another principal", 403,
				exchangeRequest("somebody-else", actorToken()));
	}

	@Test
	void testAWildcardBackdoorRoleAdmitsNobody() {
		// "all" is a defensible thing for an access role to say -- a resource open to
		// anyone the provider authenticated. It is not a defensible thing for an
		// impersonation gate to say, since it would let every principal answer as every
		// other, which is never what naming a role is for
		final var clientEndpoint = register();
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("all"));
		claimsHoldNothing();

		IuTestLogger.expect(OidcTokenEndpoint.class.getName(), Level.WARNING,
				"token-impersonation-denied:norole:" + CLIENT_ID + ":" + PRINCIPAL);
		assertError("access_denied", "Not authorized to impersonate another principal", 403,
				exchangeRequest("somebody-else", actorToken()));

		// and it is settled here rather than asked about, so no lookup is made for it
		verify(identitySource, never()).hasRole(any(), any());
	}

	@Test
	void testAnEndpointNamingNoBackdoorRoleRefusesEveryone() {
		final var clientEndpoint = register();
		// a mock answers an unstubbed Iterable with an empty one rather than null,
		// and refusing everyone has to read the same either way
		when(clientEndpoint.getBackdoorRoles()).thenReturn(null);
		claimsHoldNothing();

		IuTestLogger.expect(OidcTokenEndpoint.class.getName(), Level.WARNING,
				"token-impersonation-denied:norole:" + CLIENT_ID + ":" + PRINCIPAL);
		assertError("access_denied", "Not authorized to impersonate another principal", 403,
				exchangeRequest("somebody-else", actorToken()));
	}

	@Test
	void testAnHonoredExchangeNamesTheActorOnBothTokens() {
		registerBackdoor();

		doAnswer(a -> {
			final WebTokenBuilder builder = a.getArgument(2);
			if (PRINCIPAL.equals(a.getArgument(0))) {
				builder.claim("name", "Some One", String.class);
				builder.claim("email", "someone@iu.edu", String.class);
			}
			return null;
		}).when(claimsSource).claims(any(), any(), any());

		final var authTime = Instant.now().minusSeconds(30L).truncatedTo(ChronoUnit.SECONDS);
		final var request = exchangeRequest("somebody-else",
				accessToken(PRINCIPAL, CLIENT_ID, "openid profile email", authTime, ISSUER));
		when(request.getScope()).thenReturn("openid profile email");

		final var issued = issued(request);
		assertEquals(ACCESS_TOKEN_TOKEN_TYPE, issued.issuedTokenType());

		// the ID token adds name and email so a relying party can show whose session
		// its user is looking through
		final var idToken = WebToken.verify(issued.idToken(), issuerKey);
		assertEquals("somebody-else", idToken.getSubject());
		final var idActor = (IuOidcActor) idToken.getClaim("act", IuOidcActor.class);
		assertEquals(PRINCIPAL, idActor.getSub());
		assertEquals("Some One", idActor.getName());
		assertEquals("someone@iu.edu", idActor.getEmail());
		assertEquals(authTime.getEpochSecond(), idActor.getAuthTime());

		// the subject of an exchanged token never authenticated, so nothing claims
		// they did; the actor's own authentication time rides inside act instead
		assertNull(idToken.getClaim("auth_time", Instant.class));

		// the access token names the actor and when they authenticated, nothing more
		final var accessToken = WebToken.verify(issued.accessToken(), issuerKey);
		final var accessActor = (IuOidcActor) accessToken.getClaim("act", IuOidcActor.class);
		assertEquals(PRINCIPAL, accessActor.getSub());
		assertNull(accessActor.getName());
		assertNull(accessActor.getEmail());
		assertEquals(authTime.getEpochSecond(), accessActor.getAuthTime());
	}

	@Test
	void testAnExchangeCannotBroadenWhatItWasGiven() {
		// exchanging never gains authority the caller didn't already have
		final var clientEndpoint = clientEndpoint(REDIRECT,
				List.of(resource(null, new LinkedHashSet<>(List.of("openid", "profile")))));
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("support"));
		register(List.of(clientEndpoint));
		when(identitySource.hasRole(PRINCIPAL, "support")).thenReturn(true);
		claimsForAnyone();

		final var request = exchangeRequest("somebody-else", actorToken());
		when(request.getScope()).thenReturn("openid profile");

		// the endpoint grants profile and the request asked for it, but the token it
		// was bought with never carried it
		assertEquals("openid", issued(request).scope());
	}

	@Test
	void testAnExchangeAsksForWhatItHoldsWhenItNamesNoScope() {
		registerBackdoor();
		claimsForAnyone();
		assertEquals("openid", issued(exchangeRequest("somebody-else", actorToken())).scope());
	}

	@Test
	void testAnExchangeNamesTheActorsAcrInsideActRatherThanAtTheTopLevel() {
		// the same reason auth_time does not stand at this level for an exchange: the
		// subject is somebody a caller asked to answer for, and they never did, so no
		// authority of theirs exists to name. The actor did authenticate, and act is
		// where their authority belongs -- which is also what tells a delegation apart
		// from client_credentials, where nobody authenticated at all
		registerBackdoor();
		claimsForAnyone();

		final var actorToken = accessToken(PRINCIPAL, CLIENT_ID, "openid", Instant.now().minusSeconds(30L),
				"https://idp.iu.edu", ISSUER);
		final var issued = issued(exchangeRequest("somebody-else", actorToken));

		final var accessToken = WebToken.verify(issued.accessToken(), issuerKey);
		assertEquals("somebody-else", accessToken.getSubject());
		assertNull(accessToken.getClaim("acr", String.class));

		final var accessActor = accessToken.getClaim("act", IuOidcActor.class);
		assertEquals(PRINCIPAL, accessActor.getSub());
		assertEquals("https://idp.iu.edu", accessActor.getAcr());

		// and the ID token says the same, so a relying party reads it either way
		final var idActor = WebToken.verify(issued.idToken(), issuerKey).getClaim("act", IuOidcActor.class);
		assertEquals("https://idp.iu.edu", idActor.getAcr());
	}

	@Test
	void testAnExchangeLeftWithNothingToGrantIsRefused() {
		registerBackdoor();
		assertError("invalid_scope", "No scope granted for this exchange", 400,
				exchangeRequest("somebody-else", accessToken(PRINCIPAL, CLIENT_ID, "something-else", null, ISSUER)));
	}

	@Test
	void testAnExchangeNamingAResourceIsBoundedByIt() {
		final var clientEndpoint = clientEndpoint(REDIRECT, List.of(resource(null, Set.of("openid")),
				resource(EXTERNAL, new LinkedHashSet<>(List.of("openid", "read")))));
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("support"));
		register(List.of(clientEndpoint));
		when(identitySource.hasRole(PRINCIPAL, "support")).thenReturn(true);
		claimsForAnyone();

		final var request = exchangeRequest("somebody-else",
				accessToken(PRINCIPAL, CLIENT_ID, "openid read", null, ISSUER));
		when(request.getResource()).thenReturn(List.of(EXTERNAL.toString()));
		when(request.getScope()).thenReturn("read");

		assertEquals("read", issued(request).scope());
	}

	@Test
	void testNoRefreshTokenDescendsFromAnExchange() {
		// an impersonated session cannot outlive the token that bought it
		registerBackdoor();
		claimsForAnyone();

		final var request = exchangeRequest("somebody-else",
				accessToken(PRINCIPAL, CLIENT_ID, "openid offline_access", null, ISSUER));
		when(request.getScope()).thenReturn("openid offline_access");

		final var issued = issued(request);
		assertNull(issued.refreshToken());
		assertEquals("openid", issued.scope());
	}

	@Test
	void testOnlyAnExchangeNamesWhatItIssued() {
		// RFC 8693 requires it; every other grant type answers a response shape that
		// has no such member
		register();
		claimsHoldNothing();
		assertNull(issued(codeRequest(grant("openid"))).issuedTokenType());
	}

	@Test
	void testAnExchangeRidesOnATokenThisProviderReallyIssued() {
		// the round trip: redeem a code, then exchange what it answered
		registerBackdoor();
		sourceWrites("N", "E");

		final var first = issued(codeRequest(grant("openid")));
		assertEquals(PRINCIPAL, WebToken.verify(first.accessToken(), issuerKey).getSubject());

		final var idToken = WebToken
				.verify(issued(exchangeRequest("somebody-else", first.accessToken())).idToken(), issuerKey);
		assertEquals("somebody-else", idToken.getSubject());

		final var actor = (IuOidcActor) idToken.getClaim("act", IuOidcActor.class);
		assertEquals(PRINCIPAL, actor.getSub());
		assertNotNull(actor.getAuthTime());
	}

	/** Binds a claims source that writes nothing about anyone. */
	private void claimsForAnyone() {
		sourceWrites(null, null);
	}

	/** Binds a claims source holding nothing but the subject about anyone. */
	private void claimsHoldNothing() {
		sourceWrites(null, null);
	}

	/** A code-redemption request over a grant filed in the store. */
	private OidcTokenRequest codeRequest(OidcGrant grant) {
		final var request = request("authorization_code");
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());
		final var code = store(GrantStore.CODE, grant, ACCESS_TTL);
		when(request.getCode()).thenReturn(code);
		return request;
	}

	/** Encodes an HTTP Basic credential the way RFC 6749 §2.3.1 requires. */
	private static String basic(String user, String secret) {
		return IuText.base64(IuText.utf8(URLEncoder.encode(user, StandardCharsets.UTF_8) + ':'
				+ URLEncoder.encode(secret, StandardCharsets.UTF_8)));
	}

	/** Signs a client assertion naming itself as issuer and subject. */
	private static String assertion() {
		return WebToken.builder() //
				.jti() //
				.iss(URI.create(CLIENT_ID)) //
				.sub(CLIENT_ID) //
				.aud(URI.create(ISSUER + OidcProviderMetadata.TOKEN_PATH)) //
				.iat() //
				.exp(Instant.now().plusSeconds(60L)) //
				.build() //
				.sign("JWT", Algorithm.HS256,
						WebKey.builder(Algorithm.HS256).keyId(CLIENT_ID).key(IuText.utf8(SECRET)).build());
	}

}
