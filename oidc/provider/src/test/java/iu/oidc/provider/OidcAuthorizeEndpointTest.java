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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.function.Supplier;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuBadRequestException;
import edu.iu.IuIterable;
import edu.iu.crypt.WebKey;
import edu.iu.oidc.config.OidcAuthenticatedPrincipal;
import edu.iu.oidc.config.OidcClientConfiguration;
import edu.iu.oidc.config.OidcClientEndpoint;
import edu.iu.oidc.config.OidcClientResource;
import edu.iu.oidc.config.OidcClientSource;
import edu.iu.oidc.config.OidcProviderConfiguration;
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
	}

	private final OidcIssuer issuer = mock(OidcIssuer.class);
	private final OidcClientSource clients = mock(OidcClientSource.class);
	private final IuSessionHandler sessionHandler = mock(IuSessionHandler.class);
	private final GrantStore grantStore = mock(GrantStore.class);
	private final IuSession session = mock(IuSession.class);
	private final Grant grant = new Grant();

	private OidcAuthorizeEndpoint endpoint;

	@BeforeEach
	void setup() {
		IuTestLogger.allow(OidcAuthorizeEndpoint.class.getName(), Level.INFO);
		IuTestLogger.allow(OidcAuthorizeEndpoint.class.getName(), Level.FINE);

		when(issuer.issuer()).thenReturn(ISSUER);
		when(issuer.endpointUri(OidcProviderMetadata.AUTHORIZE_PATH))
				.thenReturn(URI.create("https://example.iu.edu/oidc/authorize"));

		final var configuration = mock(OidcProviderConfiguration.class);
		when(configuration.getAuthorizationCodeTimeToLive()).thenReturn(Duration.ofMinutes(1L));
		when(issuer.configuration()).thenReturn(configuration);
		when(issuer.issuerKey()).thenReturn(mock(WebKey.class));

		when(session.getDetail(OidcGrant.class)).thenReturn(grant);
		when(sessionHandler.create()).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn("oidc=abc; HttpOnly");

		when(grantStore.put(org.mockito.ArgumentMatchers.eq(GrantStore.CODE), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any())).thenReturn(CODE);

		endpoint = new OidcAuthorizeEndpoint(issuer, clients, sessionHandler, grantStore);
	}

	/** Answers a resource entry; a null URI names this provider's own issuer. */
	private static OidcClientResource resource(URI uri, Set<String> scope) {
		final var resource = mock(OidcClientResource.class);
		when(resource.getUri()).thenReturn(uri);
		when(resource.getScope()).thenReturn(scope);
		return resource;
	}

	/** Registers one enabled client with one endpoint over the given resources. */
	private OidcClientEndpoint register(Iterable<OidcClientResource> resources) {
		final var endpoint = mock(OidcClientEndpoint.class);
		when(endpoint.getRedirectUri()).thenReturn(REDIRECT);
		when(endpoint.getResources()).thenReturn(resources);

		final var client = mock(OidcClientConfiguration.class);
		when(client.isEnabled()).thenReturn(true);
		when(client.getEndpoints()).thenReturn(List.of(endpoint));
		when(clients.client(CLIENT_ID)).thenReturn(client);

		return endpoint;
	}

	/** Registers a client whose one endpoint grants {@code openid} on the issuer. */
	private OidcClientEndpoint register() {
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

	private static Supplier<OidcAuthenticatedPrincipal> authenticated(Instant expires) {
		final var principal = mock(OidcAuthenticatedPrincipal.class);
		when(principal.getName()).thenReturn("someone");
		when(principal.getAuthnAuthority()).thenReturn("https://idp.iu.edu");
		when(principal.getAuthnInstant()).thenReturn(Instant.now().minusSeconds(30L));
		when(principal.getExpires()).thenReturn(expires);
		return () -> principal;
	}

	private static Supplier<OidcAuthenticatedPrincipal> unauthenticated() {
		return () -> null;
	}

	/** Asserts a redirect to the client, and answers its query string. */
	private static String redirectQuery(OidcAuthorizeResult result) {
		final var location = assertInstanceOf(Redirect.class, result).location();
		assertEquals(REDIRECT.getPath(), location.getPath());
		return location.getQuery();
	}

	@Test
	void testEveryCollaboratorIsRequired() {
		assertEquals("Missing issuer", assertThrows(NullPointerException.class,
				() -> new OidcAuthorizeEndpoint(null, clients, sessionHandler, grantStore)).getMessage());
		assertEquals("Missing client source", assertThrows(NullPointerException.class,
				() -> new OidcAuthorizeEndpoint(issuer, null, sessionHandler, grantStore)).getMessage());
		assertEquals("Missing session handler", assertThrows(NullPointerException.class,
				() -> new OidcAuthorizeEndpoint(issuer, clients, null, grantStore)).getMessage());
		assertEquals("Missing grant store", assertThrows(NullPointerException.class,
				() -> new OidcAuthorizeEndpoint(issuer, clients, sessionHandler, null)).getMessage());
	}

	@Test
	void testAPrincipalSupplierIsRequired() {
		// a caller that passes none has made a programming error; absorbing it would
		// read as an ordinary unauthenticated request and never stop redirecting
		final var request = request();
		assertEquals("Missing principal supplier",
				assertThrows(NullPointerException.class, () -> endpoint.authorize(request, null)).getMessage());
	}

	@Test
	void testAClientSourceThatRefusesReadsAsUnregistered() {
		when(clients.client(CLIENT_ID)).thenThrow(new IllegalStateException("no such secret"));

		final var request = request();
		final var principal = unauthenticated();
		assertEquals("invalid_client; Unregistered client_id",
				assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request, principal)).getMessage());
	}

	@Test
	void testAClientSourceThatAnswersNothingReadsAsUnregistered() {
		when(clients.client(CLIENT_ID)).thenReturn(null);

		final var request = request();
		final var principal = unauthenticated();
		assertEquals("invalid_client; Unregistered client_id",
				assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request, principal)).getMessage());
	}

	@Test
	void testADisabledClientIsRefusedAsIfItNeverExisted() {
		final var client = mock(OidcClientConfiguration.class);
		when(client.isEnabled()).thenReturn(false);
		when(clients.client(CLIENT_ID)).thenReturn(client);

		final var request = request();
		final var principal = unauthenticated();
		assertEquals("invalid_client; Unregistered client_id",
				assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request, principal)).getMessage());
	}

	@Test
	void testAnUnregisteredRedirectUriCantBeRedirectedTo() {
		register();

		final var request = request();
		when(request.getRedirectUri()).thenReturn("https://elsewhere.example.iu.edu/cb");

		final var principal = unauthenticated();
		assertEquals("invalid_request; Unregistered redirect_uri",
				assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request, principal)).getMessage());
	}

	@Test
	void testTheClientIsNotConsultedAboutWhoTheEndUserIs() {
		// a request naming an unregistered client is refused without ever asking
		when(clients.client(CLIENT_ID)).thenReturn(null);

		final var consulted = new int[1];
		final Supplier<OidcAuthenticatedPrincipal> principal = () -> {
			consulted[0]++;
			return null;
		};

		final var request = request();
		assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request, principal));
		assertEquals(0, consulted[0]);
	}

	@Test
	void testAMissingResponseTypeIsRelayedToTheClient() {
		register();
		final var request = request();
		when(request.getResponseType()).thenReturn(null);

		assertEquals("error=invalid_request&error_description=Missing+response_type",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testOnlyTheCodeResponseTypeIsAnswered() {
		register();
		final var request = request();
		when(request.getResponseType()).thenReturn("token");

		assertEquals(
				"error=unsupported_response_type&error_description=Only+the+code+response_type+is+supported",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testTheStateIsEchoedOnAnError() {
		register();
		final var request = request();
		when(request.getResponseType()).thenReturn(null);
		when(request.getState()).thenReturn("s1");

		assertEquals("error=invalid_request&error_description=Missing+response_type&state=s1",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testNoResourceForTheRequestedScopeAuthorizesNothing() {
		register(List.of(resource(null, Set.of("something-else"))));
		final var request = request();

		assertEquals("error=invalid_scope&error_description=No+resource+is+registered+for+the+requested+scope",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testAScopeTheClientIsntGrantedIsRefused() {
		register(List.of(resource(null, Set.of("openid"))));
		final var request = request();
		when(request.getScope()).thenReturn("openid admin");

		assertEquals("error=invalid_scope&error_description=Scope+admin+is+not+granted+to+this+client",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testAMalformedResourceIsRefused() {
		register();
		final var request = request();
		when(request.getResource()).thenReturn(List.of("/relative"));

		assertEquals("error=invalid_target&error_description=Malformed+resource+parameter",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testANullResourceValueIsRefused() {
		register();
		final var request = request();
		when(request.getResource()).thenReturn(Arrays.asList((String) null));

		assertEquals("error=invalid_target&error_description=Malformed+resource+parameter",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testAnUnregisteredResourceIsRefused() {
		register();
		final var request = request();
		when(request.getResource()).thenReturn(List.of(EXTERNAL.toString()));

		assertEquals("error=invalid_target&error_description=Unregistered+resource+" + EXTERNAL,
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testANamedResourceIsRecordedOnTheGrant() {
		register(List.of(resource(EXTERNAL, Set.of("openid"))));
		final var request = request();
		when(request.getResource()).thenReturn(List.of(EXTERNAL.toString(), EXTERNAL.toString()));

		endpoint.authorize(request, authenticated(Instant.now().plusSeconds(60L)));
		assertArrayEquals(new String[] { EXTERNAL.toString() }, grant.getResource());
	}

	@Test
	void testAChallengeThisProviderCantVerifyIsRefused() {
		register();
		final var request = request();
		when(request.getCodeChallenge()).thenReturn("abc");
		when(request.getCodeChallengeMethod()).thenReturn("plain");

		assertEquals("error=invalid_request&error_description=Only+the+S256+code_challenge_method+is+supported",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testAChallengeMethodWithNoChallengeIsRefused() {
		register();
		final var request = request();
		when(request.getCodeChallengeMethod()).thenReturn("S256");

		assertEquals("error=invalid_request&error_description=Missing+code_challenge",
				redirectQuery(endpoint.authorize(request, unauthenticated())));
	}

	@Test
	void testAnUnauthenticatedRequestGoesToTheIdentityProvider() {
		register();
		final var request = request();
		when(request.getNonce()).thenReturn("n1");
		when(request.getState()).thenReturn("s1");
		when(request.getImpersonatedPrincipal()).thenReturn("someone-else");
		when(request.getCodeChallenge()).thenReturn("abc");
		when(request.getCodeChallengeMethod()).thenReturn("S256");

		final var result = assertInstanceOf(AuthenticationRequired.class,
				endpoint.authorize(request, unauthenticated()));
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

		// the end user comes back on a request this provider never issued
		verify(session).setStrict(false);
	}

	@Test
	void testAPrincipalLookupThatRefusesReadsAsUnauthenticated() {
		register();
		final var request = request();

		assertInstanceOf(AuthenticationRequired.class, endpoint.authorize(request, () -> {
			throw new IllegalStateException("no session");
		}));
	}

	@Test
	void testAnExpiredAuthenticationSendsTheEndUserBack() {
		register();
		final var request = request();

		assertInstanceOf(AuthenticationRequired.class,
				endpoint.authorize(request, authenticated(Instant.now().minusSeconds(1L))));
	}

	@Test
	void testAnAuthenticationNamingNoEndSendsTheEndUserBack() {
		register();
		final var request = request();

		assertInstanceOf(AuthenticationRequired.class, endpoint.authorize(request, authenticated(null)));
	}

	@Test
	void testAnEstablishedPrincipalSkipsTheRoundTrip() {
		register();
		final var request = request();

		assertEquals("code=" + CODE, redirectQuery(endpoint.authorize(request, authenticated(Instant.now().plusSeconds(60L)))));

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

		assertEquals("code=" + CODE + "&state=s1",
				redirectQuery(endpoint.authorize(request, authenticated(Instant.now().plusSeconds(60L)))));
	}

	@Test
	void testAResumptionReadsTheRequestBackOutOfTheSession() {
		grant.setClientId(CLIENT_ID);
		grant.setRedirectUri(REDIRECT);
		grant.setState("s1");
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(session);

		final var request = resumption();
		assertEquals("code=" + CODE + "&state=s1",
				redirectQuery(endpoint.authorize(request, authenticated(Instant.now().plusSeconds(60L)))));

		// good for exactly one return, so a replay finds nothing to resume
		verify(sessionHandler).remove(request.getCookies());
	}

	@Test
	void testAResumptionWithNoSessionResumesNothing() {
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(null);

		final var request = resumption();
		final var principal = authenticated(Instant.now().plusSeconds(60L));
		assertEquals("invalid_request; Missing client_id request parameter",
				assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request, principal)).getMessage());
	}

	@Test
	void testAResumptionWithNoRecordedRequestResumesNothing() {
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(session);

		final var request = resumption();
		final var principal = authenticated(Instant.now().plusSeconds(60L));
		assertEquals("invalid_request; Missing client_id request parameter",
				assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request, principal)).getMessage());
	}

	@Test
	void testAResumptionTheIdentityProviderDeclinedIsRefused() {
		grant.setClientId(CLIENT_ID);
		when(sessionHandler.activate(org.mockito.ArgumentMatchers.any())).thenReturn(session);

		final var request = resumption();
		final var principal = unauthenticated();
		assertEquals("login_required; User is not authenticated",
				assertThrows(IuBadRequestException.class, () -> endpoint.authorize(request, principal)).getMessage());

		// spent before a principal was even looked for
		verify(sessionHandler).remove(request.getCookies());
	}

	@Test
	void testTheGrantIsHandedOverUnderTheConfiguredLifetime() {
		register();
		final var issuerKey = mock(WebKey.class);
		when(issuer.issuerKey()).thenReturn(issuerKey);

		endpoint.authorize(request(), authenticated(Instant.now().plusSeconds(60L)));

		verify(grantStore).put(GrantStore.CODE, ISSUER, issuerKey, Duration.ofMinutes(1L), grant);
	}

	@Test
	void testResultsAreValues() {
		assertEquals(new Redirect(REDIRECT), new Redirect(REDIRECT));
		assertEquals(new AuthenticationRequired("c", ISSUER), new AuthenticationRequired("c", ISSUER));
		assertSame(REDIRECT, new Redirect(REDIRECT).location());
	}

}
