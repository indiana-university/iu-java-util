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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
import edu.iu.oidc.IuOidcClaims;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcClaimsSource;
import edu.iu.oidc.config.IuOidcClientAuthorization;
import edu.iu.oidc.config.IuOidcClientConfiguration;
import edu.iu.oidc.config.IuOidcClientEndpoint;
import edu.iu.oidc.config.IuOidcClientResource;
import edu.iu.oidc.config.IuOidcClientRole;
import edu.iu.oidc.config.IuOidcClientSource;
import edu.iu.oidc.config.IuOidcIdentitySource;
import edu.iu.oidc.config.IuOidcProviderConfiguration;
import edu.iu.oidc.config.IuOidcProviderReference;
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

	/** Answers a resource entry; a null URI names this provider's own issuer. */
	private static IuOidcClientResource resource(URI uri, Set<String> scope) {
		final var resource = mock(IuOidcClientResource.class);
		when(resource.getUri()).thenReturn(uri);
		when(resource.getScope()).thenReturn(scope);
		return resource;
	}

	/** Answers one endpoint registered with a shared secret. */
	private static IuOidcClientEndpoint clientEndpoint(URI redirectUri, Iterable<IuOidcClientResource> resources) {
		final var authorization = mock(IuOidcClientAuthorization.class);
		when(authorization.getJwk())
				.thenReturn(WebKey.builder(Algorithm.HS256).keyId(CLIENT_ID).key(IuText.utf8(SECRET)).build());
		when(authorization.getAssertionTtl()).thenReturn(Duration.ofMinutes(2L));

		final var endpoint = mock(IuOidcClientEndpoint.class);
		when(endpoint.getAuthorization()).thenReturn(List.of(authorization));
		when(endpoint.getRedirectUri()).thenReturn(redirectUri);
		when(endpoint.getResources()).thenReturn(resources);
		when(endpoint.getAccessRoles()).thenReturn(List.of("all"));
		return endpoint;
	}

	/** Registers one enabled client over the given endpoints. */
	private void register(Iterable<IuOidcClientEndpoint> endpoints) {
		final var client = mock(IuOidcClientConfiguration.class);
		when(client.isEnabled()).thenReturn(true);
		when(client.getEndpoints()).thenReturn(endpoints);
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

	/** Answers the claims a source holds for one principal. */
	private static IuOidcClaims claims(String sub, String name, String email) {
		final var claims = mock(IuOidcClaims.class);
		when(claims.getSub()).thenReturn(sub);
		when(claims.getName()).thenReturn(name);
		when(claims.getEmail()).thenReturn(email);
		return claims;
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
		claimsFor(PRINCIPAL);

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

	@Test
	void testACodeGrantIssuesAnIdTokenForTheEndUser() {
		register();
		final var subjectClaims = claims(PRINCIPAL, "Some One", "someone@iu.edu");
		when(claimsSource.claims(any(), any(), any(), any())).thenReturn(subjectClaims);

		final var grant = grant("openid");
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
		assertEquals("https://idp.iu.edu", idToken.getClaim("idp", String.class));
		assertNotNull(idToken.getClaim("at_hash", String.class));
		assertNotNull(idToken.getClaim("auth_time", Long.class));
	}

	@Test
	void testAnIdTokenSaysNothingTheScopeDoesntAdmit() {
		register();
		// openid alone admits sub and nothing else, so the source is asked for sub
		// alone and answers nothing more
		final var subjectClaims = claims(PRINCIPAL, null, null);
		when(claimsSource.claims(PRINCIPAL, Set.of("sub"), null, null)).thenReturn(subjectClaims);

		final var grant = grant("openid");
		final var idToken = WebToken.verify(issued(codeRequest(grant)).idToken(), issuerKey);

		assertEquals(PRINCIPAL, idToken.getSubject());
		assertNull(idToken.getClaim("name", String.class));
		assertNull(idToken.getClaim("email", String.class));
		assertNull(idToken.getClaim("idp", String.class));

		// a grant that recorded no authentication instant claims no auth_time
		final var undated = grant("openid");
		when(undated.getAuthnInstant()).thenReturn(null);
		assertNull(WebToken.verify(issued(codeRequest(undated)).idToken(), issuerKey).getClaim("auth_time",
				Long.class));
	}

	@Test
	void testNoIdTokenWithoutOpenid() {
		register(List.of(clientEndpoint(REDIRECT, List.of(resource(null, Set.of("read"))))));
		claimsFor(PRINCIPAL);

		final var issued = issued(codeRequest(grant("read")));
		assertNull(issued.idToken());
		assertNotNull(issued.accessToken());
	}

	@Test
	void testARefreshTokenIsIssuedWhileTheAuthenticationIsYoungEnough() {
		register();
		claimsFor(PRINCIPAL);

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
		claimsFor(PRINCIPAL);

		final var request = request("refresh_token");
		final var refreshToken = store(GrantStore.REFRESH, grant("openid"), ACCESS_TTL);
		when(request.getRefreshToken()).thenReturn(refreshToken);

		assertNotNull(issued(request).idToken());
	}

	@Test
	void testARedemptionMayNarrowTheAudienceButNeverWidenIt() {
		register(List.of(clientEndpoint(REDIRECT,
				List.of(resource(null, Set.of("openid")), resource(EXTERNAL, Set.of("openid"))))));
		claimsFor(PRINCIPAL);

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
	void testImpersonationIsIgnoredInProduction() {
		when(reference.isProduction()).thenReturn(true);
		IuTestLogger.expect(OidcTokenEndpoint.class.getName(), Level.WARNING,
				"token-impersonation-denied:production:" + CLIENT_ID + ":" + PRINCIPAL);

		register();
		claimsFor(PRINCIPAL);

		final var grant = grant("openid");
		when(grant.getImpersonatedPrincipalName()).thenReturn("somebody-else");

		// answered as if it had named none, and the backdoor roles are not consulted
		final var idToken = WebToken.verify(issued(codeRequest(grant)).idToken(), issuerKey);
		assertEquals(PRINCIPAL, idToken.getSubject());
		assertNull(idToken.getClaim("act", OidcActor.class));
	}

	@Test
	void testImpersonationNeedsABackdoorRole() {
		final var clientEndpoint = register();
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("support"));
		when(identitySource.hasRole(PRINCIPAL, "support")).thenReturn(false);

		final var grant = grant("openid");
		when(grant.getImpersonatedPrincipalName()).thenReturn("somebody-else");

		assertError("access_denied", "Not authorized to impersonate another principal", 403, codeRequest(grant));
	}

	@Test
	void testAnHonoredImpersonationNamesTheActorOnBothTokens() {
		final var clientEndpoint = register();
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("support"));
		when(identitySource.hasRole(PRINCIPAL, "support")).thenReturn(true);

		final var impersonatedClaims = claims("somebody-else", null, null);
		final var actorClaims = claims(PRINCIPAL, "Some One", "someone@iu.edu");
		when(claimsSource.claims(eq("somebody-else"), any(), any(), any())).thenReturn(impersonatedClaims);
		when(claimsSource.claims(eq(PRINCIPAL), any(), any(), any())).thenReturn(actorClaims);

		final var grant = grant("openid profile email");
		when(grant.getImpersonatedPrincipalName()).thenReturn("somebody-else");

		final var issued = issued(codeRequest(grant));

		// the ID token adds name and email so a relying party can show whose session
		// its user is looking through
		final var idToken = WebToken.verify(issued.idToken(), issuerKey);
		assertEquals("somebody-else", idToken.getSubject());
		final var idActor = (OidcActor) idToken.getClaim("act", OidcActor.class);
		assertEquals(PRINCIPAL, idActor.getSub());
		assertEquals("Some One", idActor.getName());
		assertEquals("someone@iu.edu", idActor.getEmail());

		// the access token names the actor and nothing more
		final var accessToken = WebToken.verify(issued.accessToken(), issuerKey);
		final var accessActor = (OidcActor) accessToken.getClaim("act", OidcActor.class);
		assertEquals(PRINCIPAL, accessActor.getSub());
		assertNull(accessActor.getName());
		assertNull(accessActor.getEmail());
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
		claimsFor(PRINCIPAL);

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
		claimsFor(PRINCIPAL);

		final var role = mock(IuOidcClientRole.class);
		when(role.getRole()).thenReturn("editor");
		when(role.getIdRoles()).thenReturn(List.of("staff"));
		when(identitySource.hasRole(PRINCIPAL, "staff")).thenReturn(true);

		final var unmatched = mock(IuOidcClientRole.class);
		when(unmatched.getIdRoles()).thenReturn(List.of("faculty"));

		when(clientEndpoint.getRoles()).thenReturn(Arrays.asList(null, role, unmatched));

		final var issued = issued(codeRequest(grant("openid")));
		final var accessToken = WebToken.verify(issued.accessToken(), issuerKey);
		assertArrayEquals(new String[] { "editor" }, (String[]) accessToken.getClaim("roles", String[].class));

		final var idToken = WebToken.verify(issued.idToken(), issuerKey);
		assertArrayEquals(new String[] { "editor" }, (String[]) idToken.getClaim("roles", String[].class));
	}

	@Test
	void testAnEndpointDeclaringNoApplicationRolesAddsNone() {
		register();
		claimsFor(PRINCIPAL);

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

		doThrow(new IuOutOfServiceException("down")).when(claimsSource).claims(any(), any(), any(), any());
		assertError("invalid_request", "Invalid principal name", 400, codeRequest(grant("openid")));

		doReturn(null).when(claimsSource).claims(any(), any(), any(), any());
		assertError("invalid_request", "Invalid principal name", 400, codeRequest(grant("openid")));
	}

	@Test
	void testAClaimsSourceAnsweringForSomebodyElseIsRefused() {
		register();
		final var wrongClaims = claims("somebody-else", null, null);
		when(claimsSource.claims(any(), any(), any(), any())).thenReturn(wrongClaims);

		final var request = codeRequest(grant("openid"));
		assertEquals("Claims source answered for somebody-else rather than " + PRINCIPAL,
				assertThrows(IllegalStateException.class, () -> endpoint.token(request)).getMessage());
	}

	@Test
	void testATokenIsEncryptedWhenTheEndpointRegistersAKeyToEncryptTo() {
		final var audienceKey = WebKey.builder(Algorithm.ECDH_ES).keyId("client-key").ephemeral().build();
		final var clientEndpoint = register();
		when(clientEndpoint.getEncryptJwk()).thenReturn(audienceKey);
		when(clientEndpoint.getEnc()).thenReturn(Encryption.A256GCM);
		claimsFor(PRINCIPAL);

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
		claimsFor(PRINCIPAL);

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

	@Test
	void testTheActorRecordCarriesWhatItWasBuiltWith() {
		final var clientEndpoint = register();
		when(clientEndpoint.getBackdoorRoles()).thenReturn(List.of("all"));
		when(claimsSource.claims(any(), any(), any(), any())).thenAnswer(i -> claims(i.getArgument(0), "N", "E"));

		final var grant = grant("openid profile email");
		when(grant.getImpersonatedPrincipalName()).thenReturn("somebody-else");

		final var idToken = WebToken.verify(issued(codeRequest(grant)).idToken(), issuerKey);
		final var actor = (OidcActor) idToken.getClaim("act", OidcActor.class);
		assertEquals(PRINCIPAL, actor.getSub());
		assertEquals("N", actor.getName());
		assertEquals("E", actor.getEmail());
	}

	/** Binds a claims source answering plainly for one principal. */
	private void claimsFor(String principalName) {
		final var held = claims(principalName, null, null);
		when(claimsSource.claims(any(), any(), any(), any())).thenReturn(held);
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
