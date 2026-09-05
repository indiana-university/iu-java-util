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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuDataStore;
import edu.iu.IuIterable;
import edu.iu.IuOutOfServiceException;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.IuAuthorizationDetails;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcAuthenticatedPrincipal;
import edu.iu.oidc.config.IuOidcAuthorizationDetailsSource;
import edu.iu.oidc.config.IuOidcClientConfiguration;
import edu.iu.oidc.config.IuOidcClientEndpoint;
import edu.iu.oidc.config.IuOidcClientResource;
import edu.iu.oidc.config.IuOidcClientSource;
import edu.iu.oidc.config.IuOidcProviderConfiguration;
import edu.iu.oidc.config.IuOidcProviderReference;
import edu.iu.session.IuSession;
import edu.iu.session.IuSessionHandler;
import edu.iu.test.IuTestLogger;
import iu.oidc.provider.OidcAuthorizeResult.AuthenticationRequired;
import iu.oidc.provider.OidcAuthorizeResult.Redirect;

@SuppressWarnings("javadoc")
public class OidcAuthorizeEndpointTest {

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");
	private static final URI REDIRECT = URI.create("https://client.example.iu.edu/cb");
	private static final URI EXTERNAL = URI.create("https://api.example.iu.edu");
	private static final String CLIENT_ID = "some-client";
	private static final String CODE = "the-code";

	/** What a client asked for; the module reads nothing but the type. */
	private static final Iterable<? extends IuAuthorizationDetails> REQUESTED_DETAILS = List.of(() -> "record");

	/** Distinct, so releasing is visibly not echoing. */
	private static final Iterable<? extends IuAuthorizationDetails> RELEASED_DETAILS = List.of(() -> "record-readonly");

	/** Mutable grant, standing in for what a session handler proxies. */
	private static final class Grant implements OidcGrant {
		private String principalName;
		private String impersonatedPrincipalName;
		private String authnAuthority;
		private Instant authnInstant;
		private String clientId;
		private URI redirectUri;
		private String scope;
		private String[] resource;
		private String state;
		private String nonce;
		private String codeChallenge;
		private Iterable<? extends IuAuthorizationDetails> requestedAuthorizationDetails;
		private Iterable<? extends IuAuthorizationDetails> releasedAuthorizationDetails;

		@Override
		public String getPrincipalName() {
			return principalName;
		}

		@Override
		public void setPrincipalName(String principalName) {
			this.principalName = principalName;
		}

		@Override
		public String getImpersonatedPrincipalName() {
			return impersonatedPrincipalName;
		}

		@Override
		public void setImpersonatedPrincipalName(String impersonatedPrincipalName) {
			this.impersonatedPrincipalName = impersonatedPrincipalName;
		}

		@Override
		public String getAuthnAuthority() {
			return authnAuthority;
		}

		@Override
		public void setAuthnAuthority(String authnAuthority) {
			this.authnAuthority = authnAuthority;
		}

		@Override
		public Instant getAuthnInstant() {
			return authnInstant;
		}

		@Override
		public void setAuthnInstant(Instant authnInstant) {
			this.authnInstant = authnInstant;
		}

		@Override
		public String getClientId() {
			return clientId;
		}

		@Override
		public void setClientId(String clientId) {
			this.clientId = clientId;
		}

		@Override
		public URI getRedirectUri() {
			return redirectUri;
		}

		@Override
		public void setRedirectUri(URI redirectUri) {
			this.redirectUri = redirectUri;
		}

		@Override
		public String getScope() {
			return scope;
		}

		@Override
		public void setScope(String scope) {
			this.scope = scope;
		}

		@Override
		public String[] getResource() {
			return resource;
		}

		@Override
		public void setResource(String[] resource) {
			this.resource = resource;
		}

		@Override
		public String getState() {
			return state;
		}

		@Override
		public void setState(String state) {
			this.state = state;
		}

		@Override
		public String getNonce() {
			return nonce;
		}

		@Override
		public void setNonce(String nonce) {
			this.nonce = nonce;
		}

		@Override
		public String getCodeChallenge() {
			return codeChallenge;
		}

		@Override
		public void setCodeChallenge(String codeChallenge) {
			this.codeChallenge = codeChallenge;
		}

		@Override
		public Iterable<? extends IuAuthorizationDetails> getRequestedAuthorizationDetails() {
			return requestedAuthorizationDetails;
		}

		@Override
		public void setRequestedAuthorizationDetails(Iterable<? extends IuAuthorizationDetails> requestedAuthorizationDetails) {
			this.requestedAuthorizationDetails = requestedAuthorizationDetails;
		}

		@Override
		public Iterable<? extends IuAuthorizationDetails> getReleasedAuthorizationDetails() {
			return releasedAuthorizationDetails;
		}

		@Override
		public void setReleasedAuthorizationDetails(Iterable<? extends IuAuthorizationDetails> releasedAuthorizationDetails) {
			this.releasedAuthorizationDetails = releasedAuthorizationDetails;
		}
	}

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	private final IuOidcProviderReference reference = mock(IuOidcProviderReference.class);
	private final IuOidcClientSource clients = mock(IuOidcClientSource.class);
	private final IuSessionHandler sessionHandler = mock(IuSessionHandler.class);
	private final IuDataStore dataStore = mock(IuDataStore.class);
	private final IuOidcAuthorizationDetailsSource authorizationDetails = mock(IuOidcAuthorizationDetailsSource.class);
	private final IuSession session = mock(IuSession.class);
	private final Grant grant = new Grant();

	private OidcAuthorizeEndpoint endpoint;

	@BeforeEach
	void setup() {
		IuTestLogger.allow(OidcAuthorizeEndpoint.class.getName(), Level.INFO);
		IuTestLogger.allow(OidcAuthorizeEndpoint.class.getName(), Level.FINE);

		// the issuer and grant store are built inside the endpoint now, so the
		// configuration has to carry a key that can really sign
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getIssuer()).thenReturn(ISSUER);

		final var issuerKey = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral().build();
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
			public Duration getAuthorizationCodeTimeToLive() {
				return Duration.ofMinutes(1L);
			}
		};

		when(session.getDetail(OidcGrant.class)).thenReturn(grant);
		when(sessionHandler.create()).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn("oidc=abc; HttpOnly");

		when(reference.getConfiguration()).thenReturn(configuration);
		when(reference.getClientSource()).thenReturn(clients);
		when(reference.getSessionHandler()).thenReturn(sessionHandler);
		when(reference.getDataStore()).thenReturn(dataStore);
		when(reference.getAuthorizationDetailsSource()).thenReturn(authorizationDetails);

		endpoint = new OidcAuthorizeEndpoint(reference);
	}

	/**
	 * Binds the principal the reference reports, then answers the request.
	 *
	 * @param request   incoming request
	 * @param principal established principal; null when nobody is signed in
	 * @return what the request came to
	 */
	private OidcAuthorizeResult authorize(OidcAuthorizeRequest request, IuOidcAuthenticatedPrincipal principal) {
		when(reference.getAuthenticatedPrincipal(any())).thenReturn(principal);
		return endpoint.authorize(request);
	}

	/** Answers a resource entry; a null URI names this provider's own issuer. */
	private static IuOidcClientResource resource(URI uri, Set<String> scope) {
		final var resource = mock(IuOidcClientResource.class);
		when(resource.getUri()).thenReturn(uri);
		when(resource.getScope()).thenReturn(scope);
		return resource;
	}

	/** Registers one enabled client with one endpoint over the given resources. */
	private IuOidcClientEndpoint register(Iterable<IuOidcClientResource> resources) {
		final var endpoint = mock(IuOidcClientEndpoint.class);
		when(endpoint.getRedirectUri()).thenReturn(REDIRECT);
		when(endpoint.getResources()).thenReturn(resources);

		final var client = mock(IuOidcClientConfiguration.class);
		when(client.isEnabled()).thenReturn(true);
		when(client.getEndpoints()).thenReturn(List.of(endpoint));
		when(clients.client(CLIENT_ID)).thenReturn(client);

		return endpoint;
	}

	/** Registers a client whose one endpoint grants {@code openid} on the issuer. */
	private IuOidcClientEndpoint register() {
		return register(List.of(resource(null, Set.of("openid"))));
	}

	/** A first-pass request naming a client, with nothing else set. */
	private static OidcAuthorizeRequest request() {
		final var request = mock(OidcAuthorizeRequest.class);
		when(request.getClientId()).thenReturn(CLIENT_ID);
		when(request.getRedirectUri()).thenReturn(REDIRECT.toString());
		when(request.getResponseType()).thenReturn("code");
		when(request.getScope()).thenReturn("openid");
		// naming the parameter not at all reads differently from naming it emptily,
		// and a mock answers an empty iterable unless told otherwise
		when(request.getResource()).thenReturn(null);
		return request;
	}

	/** A second-pass request, carrying only cookies. */
	private static OidcAuthorizeRequest resumption() {
		final var request = mock(OidcAuthorizeRequest.class);
		when(request.getCookies()).thenReturn(IuIterable.iter(new HttpCookie("oidc", "abc")));
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		return request;
	}

	private static IuOidcAuthenticatedPrincipal authenticated(Instant expires) {
		final var principal = mock(IuOidcAuthenticatedPrincipal.class);
		when(principal.getName()).thenReturn("someone");
		when(principal.getAuthnAuthority()).thenReturn("https://idp.iu.edu");
		when(principal.getAuthnInstant()).thenReturn(Instant.now().minusSeconds(30L));
		when(principal.getExpires()).thenReturn(expires);
		return principal;
	}

	private static IuOidcAuthenticatedPrincipal unauthenticated() {
		return null;
	}

	/** Asserts a code redirect, and answers the code it carries. */
	private String issuedCode(OidcAuthorizeResult result) {
		final var query = redirectQuery(result);
		assertTrue(query.startsWith("code="), query);
		final var end = query.indexOf('&');
		return end < 0 ? query.substring(5) : query.substring(5, end);
	}

	/** Asserts a redirect to the client, and answers its query string. */
	private static String redirectQuery(OidcAuthorizeResult result) {
		final var location = assertInstanceOf(Redirect.class, result).location();
		assertEquals(REDIRECT.getPath(), location.getPath());
		return location.getQuery();
	}

	@Test
	void testTheReferenceIsRequired() {
		assertEquals("Missing provider reference",
				assertThrows(NullPointerException.class, () -> new OidcAuthorizeEndpoint(null)).getMessage());
	}

	@Test
	void testAClientSourceThatRefusesReadsAsUnregistered() {
		when(clients.client(CLIENT_ID)).thenThrow(new IllegalStateException("no such secret"));

		final var request = request();
		final var principal = unauthenticated();
		assertEquals("invalid_client; Unregistered client_id",
				assertThrows(IuBadRequestException.class, () -> authorize(request, principal)).getMessage());
	}

	@Test
	void testAClientSourceThatAnswersNothingReadsAsUnregistered() {
		when(clients.client(CLIENT_ID)).thenReturn(null);

		final var request = request();
		final var principal = unauthenticated();
		assertEquals("invalid_client; Unregistered client_id",
				assertThrows(IuBadRequestException.class, () -> authorize(request, principal)).getMessage());
	}

	@Test
	void testADisabledClientIsRefusedAsIfItNeverExisted() {
		final var client = mock(IuOidcClientConfiguration.class);
		when(client.isEnabled()).thenReturn(false);
		when(clients.client(CLIENT_ID)).thenReturn(client);

		final var request = request();
		final var principal = unauthenticated();
		assertEquals("invalid_client; Unregistered client_id",
				assertThrows(IuBadRequestException.class, () -> authorize(request, principal)).getMessage());
	}

	@Test
	void testAnUnregisteredRedirectUriCantBeRedirectedTo() {
		register();

		final var request = request();
		when(request.getRedirectUri()).thenReturn("https://elsewhere.example.iu.edu/cb");

		final var principal = unauthenticated();
		assertEquals("invalid_request; Unregistered redirect_uri",
				assertThrows(IuBadRequestException.class, () -> authorize(request, principal)).getMessage());
	}

	@Test
	void testTheClientIsNotConsultedAboutWhoTheEndUserIs() {
		// a request naming an unregistered client is refused without ever asking
		when(clients.client(CLIENT_ID)).thenReturn(null);

		final var request = request();
		assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request));
		verify(reference, never()).getAuthenticatedPrincipal(any());
	}

	@Test
	void testAMissingResponseTypeIsRelayedToTheClient() {
		register();
		final var request = request();
		when(request.getResponseType()).thenReturn(null);

		assertEquals("error=invalid_request&error_description=Missing+response_type",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testOnlyTheCodeResponseTypeIsAnswered() {
		register();
		final var request = request();
		when(request.getResponseType()).thenReturn("token");

		assertEquals(
				"error=unsupported_response_type&error_description=Only+the+code+response_type+is+supported",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testTheStateIsEchoedOnAnError() {
		register();
		final var request = request();
		when(request.getResponseType()).thenReturn(null);
		when(request.getState()).thenReturn("s1");

		assertEquals("error=invalid_request&error_description=Missing+response_type&state=s1",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testNoResourceForTheRequestedScopeAuthorizesNothing() {
		register(List.of(resource(null, Set.of("something-else"))));
		final var request = request();

		assertEquals("error=invalid_scope&error_description=No+resource+is+registered+for+the+requested+scope",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testAScopeTheClientIsntGrantedIsRefused() {
		register(List.of(resource(null, Set.of("openid"))));
		final var request = request();
		when(request.getScope()).thenReturn("openid admin");

		assertEquals("error=invalid_scope&error_description=Scope+admin+is+not+granted+to+this+client",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testAMalformedResourceIsRefused() {
		register();
		final var request = request();
		when(request.getResource()).thenReturn(List.of("/relative"));

		assertEquals("error=invalid_target&error_description=Malformed+resource+parameter",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testANullResourceValueIsRefused() {
		register();
		final var request = request();
		when(request.getResource()).thenReturn(Arrays.asList((String) null));

		assertEquals("error=invalid_target&error_description=Malformed+resource+parameter",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testAnUnregisteredResourceIsRefused() {
		register();
		final var request = request();
		when(request.getResource()).thenReturn(List.of(EXTERNAL.toString()));

		assertEquals("error=invalid_target&error_description=Unregistered+resource+" + EXTERNAL,
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testANamedResourceIsRecordedOnTheGrant() {
		register(List.of(resource(EXTERNAL, Set.of("openid"))));
		final var request = request();
		when(request.getResource()).thenReturn(List.of(EXTERNAL.toString(), EXTERNAL.toString()));

		authorize(request, authenticated(Instant.now().plusSeconds(60L)));
		assertArrayEquals(new String[] { EXTERNAL.toString() }, grant.getResource());
	}

	@Test
	void testAChallengeThisProviderCantVerifyIsRefused() {
		register();
		final var request = request();
		when(request.getCodeChallenge()).thenReturn("abc");
		when(request.getCodeChallengeMethod()).thenReturn("plain");

		assertEquals("error=invalid_request&error_description=Only+the+S256+code_challenge_method+is+supported",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testAChallengeMethodWithNoChallengeIsRefused() {
		register();
		final var request = request();
		when(request.getCodeChallengeMethod()).thenReturn("S256");

		assertEquals("error=invalid_request&error_description=Missing+code_challenge",
				redirectQuery(authorize(request, unauthenticated())));
	}

	@Test
	void testAnUnauthenticatedRequestGoesToTheIdentityProvider() {
		register();
		final var request = request();
		when(request.getNonce()).thenReturn("n1");
		when(request.getState()).thenReturn("s1");
		when(request.getImpersonatedPrincipal()).thenReturn("someone-else");
		doReturn(REQUESTED_DETAILS).when(request).getAuthorizationDetails();
		when(request.getCodeChallenge()).thenReturn("abc");
		when(request.getCodeChallengeMethod()).thenReturn("S256");

		final var result = assertInstanceOf(AuthenticationRequired.class,
				authorize(request, unauthenticated()));
		assertEquals("oidc=abc; HttpOnly", result.setCookie());
		assertEquals(URI.create("https://example.iu.edu/oidc/authorize"), result.returnUri());

		// the validated request rides in the session, not in the return URI
		assertNull(result.returnUri().getQuery());
		assertEquals(CLIENT_ID, grant.getClientId());
		assertEquals(REDIRECT, grant.getRedirectUri());
		assertEquals("openid", grant.getScope());
		assertEquals("n1", grant.getNonce());
		assertEquals("s1", grant.getState());
		assertEquals("abc", grant.getCodeChallenge());
		assertEquals("someone-else", grant.getImpersonatedPrincipalName());

		// recorded before the end user is known, since a client states what it wants
		// without waiting to find out who is asking
		assertSame(REQUESTED_DETAILS, grant.getRequestedAuthorizationDetails());
		assertNull(grant.getReleasedAuthorizationDetails());

		// the end user comes back on a request this provider never issued
		verify(session).setStrict(false);
	}

	@Test
	void testAPrincipalLookupThatRefusesReadsAsUnauthenticated() {
		register();
		final var request = request();

		when(reference.getAuthenticatedPrincipal(any())).thenThrow(new IllegalStateException("no session"));
		assertInstanceOf(AuthenticationRequired.class, endpoint.authorize(request));
	}

	@Test
	void testAnExpiredAuthenticationSendsTheEndUserBack() {
		register();
		final var request = request();

		assertInstanceOf(AuthenticationRequired.class,
				authorize(request, authenticated(Instant.now().minusSeconds(1L))));
	}

	@Test
	void testAnAuthenticationNamingNoEndSendsTheEndUserBack() {
		register();
		final var request = request();

		assertInstanceOf(AuthenticationRequired.class, authorize(request, authenticated(null)));
	}

	@Test
	void testAnEstablishedPrincipalSkipsTheRoundTrip() {
		register();
		final var request = request();

		assertNotNull(issuedCode(authorize(request, authenticated(Instant.now().plusSeconds(60L)))));

		assertEquals("someone", grant.getPrincipalName());
		assertEquals("https://idp.iu.edu", grant.getAuthnAuthority());

		// no session outlives an issued code
		verify(sessionHandler, never()).store(session);
	}

	@Test
	void testTheStateIsEchoedWithTheCode() {
		register();
		final var request = request();
		when(request.getState()).thenReturn("s1");

		assertTrue(redirectQuery(authorize(request, authenticated(Instant.now().plusSeconds(60L)))).endsWith("&state=s1"));
	}

	@Test
	void testAResumptionReadsTheRequestBackOutOfTheSession() {
		grant.setClientId(CLIENT_ID);
		grant.setRedirectUri(REDIRECT);
		grant.setState("s1");
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(session);

		final var request = resumption();
		assertTrue(redirectQuery(authorize(request, authenticated(Instant.now().plusSeconds(60L)))).endsWith("&state=s1"));

		// good for exactly one return, so a replay finds nothing to resume
		verify(sessionHandler).remove(request.getCookies());
	}

	@Test
	void testAResumptionWithNoSessionResumesNothing() {
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(null);

		final var request = resumption();
		final var principal = authenticated(Instant.now().plusSeconds(60L));
		assertEquals("invalid_request; Missing client_id request parameter",
				assertThrows(IuBadRequestException.class, () -> authorize(request, principal)).getMessage());
	}

	@Test
	void testAResumptionWithNoRecordedRequestResumesNothing() {
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(session);

		final var request = resumption();
		final var principal = authenticated(Instant.now().plusSeconds(60L));
		assertEquals("invalid_request; Missing client_id request parameter",
				assertThrows(IuBadRequestException.class, () -> authorize(request, principal)).getMessage());
	}

	@Test
	void testAResumptionTheIdentityProviderDeclinedIsRefused() {
		grant.setClientId(CLIENT_ID);
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(session);

		final var request = resumption();
		final var principal = unauthenticated();
		assertEquals("login_required; User is not authenticated",
				assertThrows(IuBadRequestException.class, () -> authorize(request, principal)).getMessage());

		// spent before a principal was even looked for
		verify(sessionHandler).remove(request.getCookies());
	}

	@Test
	void testAuthorizationDetailsAreReleasedAgainstTheAuthenticatedPrincipal() {
		register();
		final var request = request();
		doReturn(REQUESTED_DETAILS).when(request).getAuthorizationDetails();
		doReturn(RELEASED_DETAILS).when(authorizationDetails).authorize(REQUESTED_DETAILS, "someone");

		authorize(request, authenticated(Instant.now().plusSeconds(60L)));

		verify(authorizationDetails).authorize(REQUESTED_DETAILS, "someone");
		assertSame(REQUESTED_DETAILS, grant.getRequestedAuthorizationDetails());
		assertSame(RELEASED_DETAILS, grant.getReleasedAuthorizationDetails());
	}

	@Test
	void testTheDecisionIsMadeBeforeTheGrantIsHandedOver() {
		// a client redeeming this grant reads a decision already made, rather than one
		// remade on every redemption
		register();
		doReturn(RELEASED_DETAILS).when(authorizationDetails).authorize(any(), org.mockito.ArgumentMatchers.eq("someone"));

		// the grant is serialized into the store, so what the store receives is proof
		// the decision was already on it
		doAnswer(a -> {
			assertSame(RELEASED_DETAILS, grant.getReleasedAuthorizationDetails());
			return null;
		}).when(dataStore).put(any(), any(), any());

		authorize(request(), authenticated(Instant.now().plusSeconds(60L)));
		verify(dataStore).put(any(), any(), any());
	}

	@Test
	void testASourceReleasingNothingReleasesNothing() {
		register();
		doReturn(null).when(authorizationDetails).authorize(any(), any());

		authorize(request(), authenticated(Instant.now().plusSeconds(60L)));
		assertNull(grant.getReleasedAuthorizationDetails());
	}

	@Test
	void testAResumptionReleasesAgainstTheReturningPrincipal() {
		// both authenticated paths converge on the same decision point
		grant.setClientId(CLIENT_ID);
		grant.setRedirectUri(REDIRECT);
		grant.setRequestedAuthorizationDetails(REQUESTED_DETAILS);
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(session);
		doReturn(RELEASED_DETAILS).when(authorizationDetails).authorize(REQUESTED_DETAILS, "someone");

		authorize(resumption(), authenticated(Instant.now().plusSeconds(60L)));

		assertSame(RELEASED_DETAILS, grant.getReleasedAuthorizationDetails());
	}

	@Test
	void testMalformedDetailsAreTheOneRefusalTheClientHearsAbout() {
		register();
		doThrow(new IuBadRequestException("type is not registered")).when(authorizationDetails).authorize(any(), any());

		assertEquals("error=invalid_authorization_details&error_description=type+is+not+registered",
				redirectQuery(authorize(request(), authenticated(Instant.now().plusSeconds(60L)))));
	}

	@Test
	void testMalformedDetailsAreRelayedOnAResumptionToo() {
		// the resumption path has no error-to-redirect boundary of its own, so the
		// redirect is built from what the grant already recorded
		grant.setClientId(CLIENT_ID);
		grant.setRedirectUri(REDIRECT);
		grant.setState("s1");
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(session);
		doThrow(new IuBadRequestException("nope")).when(authorizationDetails).authorize(any(), any());

		assertEquals("error=invalid_authorization_details&error_description=nope&state=s1",
				redirectQuery(authorize(resumption(), authenticated(Instant.now().plusSeconds(60L)))));
	}

	@Test
	void testARefusedEndUserIsNotToldAsIfADecisionWasMade() {
		// forbidden at the caller's error boundary, not an OAuth error the client
		// could mistake for an answer
		register();
		doThrow(new IuAuthorizationFailedException("not delegated")).when(authorizationDetails).authorize(any(), any());

		final var request = request();
		final var principal = authenticated(Instant.now().plusSeconds(60L));
		assertEquals("not delegated", assertThrows(IuAuthorizationFailedException.class,
				() -> authorize(request, principal)).getMessage());
	}

	@Test
	void testAnUnreachableSourceSaysSoRatherThanRefusing() {
		register();
		doThrow(new IuOutOfServiceException("directory is down")).when(authorizationDetails).authorize(any(), any());

		final var request = request();
		final var principal = authenticated(Instant.now().plusSeconds(60L));
		assertEquals("directory is down", assertThrows(IuOutOfServiceException.class,
				() -> authorize(request, principal)).getMessage());
	}

	@Test
	void testAnyOtherFailureIsAServerFault() {
		register();
		doThrow(new IllegalStateException("bug")).when(authorizationDetails).authorize(any(), any());

		final var request = request();
		final var principal = authenticated(Instant.now().plusSeconds(60L));
		assertEquals("bug",
				assertThrows(IllegalStateException.class, () -> authorize(request, principal)).getMessage());
	}

	@Test
	void testTheGrantIsHandedOverUnderTheConfiguredLifetime() {
		register();

		final var code = issuedCode(authorize(request(), authenticated(Instant.now().plusSeconds(60L))));

		// the reference the client carries is the key the entry is encrypted to, and
		// the entry lives exactly as long as the code remains redeemable
		verify(dataStore).put(any(), any(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(1L)));
		assertNotNull(code);
	}

	@Test
	void testResultsAreValues() {
		assertEquals(new Redirect(REDIRECT), new Redirect(REDIRECT));
		assertEquals(new AuthenticationRequired("c", ISSUER), new AuthenticationRequired("c", ISSUER));
		assertSame(REDIRECT, new Redirect(REDIRECT).location());
	}

}
