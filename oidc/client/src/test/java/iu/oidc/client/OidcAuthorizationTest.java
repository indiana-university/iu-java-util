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
package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuRequestAttributes;
import edu.iu.IuWebUtils;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.IuOidcTokenResponse;
import edu.iu.session.IuSession;
import edu.iu.session.IuSessionHandler;
import edu.iu.test.IuTestLogger;
import iu.oidc.client.config.IuOidcClient;
import iu.oidc.client.config.IuOidcClientReference;
import iu.oidc.client.config.IuOidcProvider;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OidcAuthorizationTest {

	static {
		edu.iu.crypt.Init.init();
	}

	@Test
	void testInit() {
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);
		when(sessionHandler.create()).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var redirectUri = URI.create(IdGenerator.generateId());

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);

		final var provider = mock(IuOidcProvider.class);
		final var authorizationEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var config = mock(IuOidcClientReference.class);
		when(config.getRedirectUri()).thenReturn(redirectUri);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var authorization = new OidcAuthorization(config);
		final var redirect = authorization.init(null, null);
		assertEquals(setCookie, redirect.getSetCookie());

		final var params = IuWebUtils.parseQueryString(redirect.getLocation().getRawQuery());
		assertEquals("code", params.get("response_type").iterator().next());
		assertEquals(clientId, params.get("client_id").iterator().next());
		assertEquals(redirectUri.toString(), params.get("redirect_uri").iterator().next());
		verify(preAuth).setState(params.get("state").iterator().next());
		verify(preAuth).setNonce(params.get("nonce").iterator().next());
	}

	@Test
	void testInitWithExtras() {
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);
		when(sessionHandler.create()).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var redirectUri = URI.create(IdGenerator.generateId());
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var scope = IdGenerator.generateId();

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);

		final var provider = mock(IuOidcProvider.class);
		final var authorizationEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var config = mock(IuOidcClientReference.class);
		when(config.getRedirectUri()).thenReturn(redirectUri);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getScope()).thenReturn(scope);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var authorization = new OidcAuthorization(config);
		final var delegatingPrincipal = IdGenerator.generateId();
		final var impersonatedPrincipal = IdGenerator.generateId();
		final var redirect = authorization.init(delegatingPrincipal, impersonatedPrincipal);
		assertEquals(setCookie, redirect.getSetCookie());

		final var params = IuWebUtils.parseQueryString(redirect.getLocation().getRawQuery());
		assertEquals("code", params.get("response_type").iterator().next());
		assertEquals(clientId, params.get("client_id").iterator().next());
		assertEquals(redirectUri.toString(), params.get("redirect_uri").iterator().next());
		assertEquals(resourceUri.toString(), params.get("resource").iterator().next());
		assertEquals(delegatingPrincipal, params.get("delegating_principal").iterator().next());
		assertEquals(impersonatedPrincipal, params.get("impersonated_principal").iterator().next());
		verify(preAuth).setState(params.get("state").iterator().next());
		verify(preAuth).setNonce(params.get("nonce").iterator().next());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeMissingSession() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var sessionHandler = mock(IuSessionHandler.class);

		final var config = mock(IuOidcClientReference.class);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		assertEquals("missing or expired preAuth session", assertThrows(IllegalStateException.class,
				() -> authorization.authorize(requestAttributes, code, IdGenerator.generateId())).getMessage());
		assertEquals("missing or expired authorization session",
				assertThrows(IllegalStateException.class, () -> authorization.getAuthorizedPrincipal(requestAttributes))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeStateMismatch() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var state = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var config = mock(IuOidcClientReference.class);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);

		final var wrongState = IdGenerator.generateId();
		assertEquals("state mismatch " + wrongState + " preAuth=" + preAuth, assertThrows(IllegalStateException.class,
				() -> authorization.authorize(requestAttributes, code, wrongState)).getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorize() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(preAuth.getNonce()).thenReturn(nonce);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);

		final var provider = mock(IuOidcProvider.class);
		final var authorizationEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		when(idToken.getNonce()).thenReturn(nonce);
		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		})) {
			final var redirect = authorization.authorize(requestAttributes, code, state);
			verify(postAuth).setTokenResponse(response);
			verify(postAuth).setNotAfter(any(Instant.class));
			assertEquals(resourceUri, redirect.getLocation());
			assertEquals(setCookie, redirect.getSetCookie());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeNoNonce() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var state = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);

		final var provider = mock(IuOidcProvider.class);
		final var authorizationEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		})) {
			final var redirect = authorization.authorize(requestAttributes, code, state);
			verify(postAuth).setTokenResponse(response);
			verify(postAuth).setNotAfter(any(Instant.class));
			assertEquals(resourceUri, redirect.getLocation());
			assertEquals(setCookie, redirect.getSetCookie());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testMissingIdTokenNonce() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(preAuth.getNonce()).thenReturn(nonce);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);

		final var provider = mock(IuOidcProvider.class);
		final var authorizationEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		})) {
			assertEquals("Expected nonce claim", assertThrows(IllegalArgumentException.class,
					() -> authorization.authorize(requestAttributes, code, state)).getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testMissingPreAuthNonce() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);

		final var provider = mock(IuOidcProvider.class);
		final var authorizationEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		when(idToken.getNonce()).thenReturn(nonce);
		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		})) {
			assertEquals("Unexpected nonce claim", assertThrows(IllegalArgumentException.class,
					() -> authorization.authorize(requestAttributes, code, state)).getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testNonceMismatch() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(preAuth.getNonce()).thenReturn(nonce);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);

		final var provider = mock(IuOidcProvider.class);
		final var authorizationEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		when(idToken.getNonce()).thenReturn(IdGenerator.generateId());
		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		})) {
			assertEquals("nonce mismatch", assertThrows(IllegalArgumentException.class,
					() -> authorization.authorize(requestAttributes, code, state)).getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testPostAuthNotAfter() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var config = mock(IuOidcClientReference.class);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var authorization = new OidcAuthorization(config);
		assertEquals("missing post-auth not-after date",
				assertThrows(IllegalStateException.class, () -> authorization.getAuthorizedPrincipal(requestAttributes))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipal() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(preAuth.getNonce()).thenReturn(nonce);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getDecryptJwk()).thenReturn(null);

		final var provider = mock(IuOidcProvider.class);
		final var userinfoEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getUserinfoEndpoint()).thenReturn(userinfoEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		final var apiResourcev1 = URI.create(apiResource + "/v1");
		final var apiResourcev2 = URI.create(apiResource + "/v2");
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResourcev2, apiResource, apiResourcev1));

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var refreshToken = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();

		final var sub = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(idToken.getSubject()).thenReturn(sub);
		when(idToken.getNonce()).thenReturn(nonce);

		when(response.getIdToken()).thenReturn(refreshToken);
		when(response.getAccessToken()).thenReturn(accessToken);
		when(response.getRefreshToken()).thenReturn(refreshToken);
		when(response.getExpiresIn()).thenReturn(1);

		final var notAfter = Instant.now().plusSeconds(1L);
		when(postAuth.getTokenResponse()).thenReturn(response);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		final var apiAccessToken1 = IdGenerator.generateId();
		final var oboResponse1 = mock(IuOidcTokenResponse.class);
		when(oboResponse1.getAccessToken()).thenReturn(apiAccessToken1);
		when(oboResponse1.getExpiresIn()).thenReturn(1);

		final var apiAccessToken2 = IdGenerator.generateId();
		final var oboResponse2 = mock(IuOidcTokenResponse.class);
		when(oboResponse2.getAccessToken()).thenReturn(apiAccessToken2);
		when(oboResponse2.getExpiresIn()).thenReturn(1);

		try (final var mockRefreshGrant = mockConstruction(RefreshTokenGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(response, ctx.arguments().get(1));
			assertEquals(notAfter, ctx.arguments().get(2));
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		}); final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			if (apiResourcev1.equals(ctx.arguments().get(1)))
				when(a.getTokenResponse()).thenReturn(oboResponse1);
			else if (apiResourcev2.equals(ctx.arguments().get(1)))
				when(a.getTokenResponse()).thenReturn(oboResponse2);
			else
				fail();
			assertEquals(accessToken, ctx.arguments().get(2));
		})) {
			IuHttpAware.mock.when(() -> IuHttp.send(eq(userinfoEndpoint), argThat(a -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Authorization", "Bearer " + accessToken);
				return true;
			}), eq(IuHttp.READ_UTF8))).thenReturn(IuJson.object() //
					.add("sub", sub) //
					.build().toString());
			final var principal = authorization.getAuthorizedPrincipal(requestAttributes);
			assertEquals(sub, principal.getName());
			assertEquals(accessToken, principal.getAccessToken(resourceUri));

			final var wrongUri = URI.create(IdGenerator.generateId());
			assertThrows(NullPointerException.class, () -> principal.getAccessToken(wrongUri));

			assertEquals(apiAccessToken2, principal.getAccessToken(apiResourcev2));
			assertEquals(apiAccessToken2, principal.getAccessToken(apiResourcev2));
			assertEquals(1, mockOboGrant.constructed().size());

			assertEquals(apiAccessToken1, principal.getAccessToken(apiResourcev1));

		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalWithRefreshAndEncrypted() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(preAuth.getNonce()).thenReturn(nonce);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);
		final var dkid = IdGenerator.generateId();
		final var decryptJwk = WebKey.builder(WebKey.Type.X25519).algorithm(Algorithm.ECDH_ES).keyId(dkid).ephemeral()
				.build();
		when(client.getDecryptJwk()).thenReturn(IuIterable.iter(decryptJwk));

		final var provider = mock(IuOidcProvider.class);
		final var userinfoEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getUserinfoEndpoint()).thenReturn(userinfoEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var refreshToken = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();

		final var sub = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(idToken.getSubject()).thenReturn(sub);
		when(idToken.getNonce()).thenReturn(nonce);

		when(response.getIdToken()).thenReturn(refreshToken);
		when(response.getAccessToken()).thenReturn(accessToken);
		when(response.getRefreshToken()).thenReturn(refreshToken);
		when(response.getExpiresIn()).thenReturn(1);

		final var oldResponse = mock(IuOidcTokenResponse.class);
		final var notAfter = Instant.now().plusSeconds(1L);
		when(postAuth.getTokenResponse()).thenReturn(oldResponse);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		try (final var mockRefreshGrant = mockConstruction(RefreshTokenGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(oldResponse, ctx.arguments().get(1));
			assertEquals(notAfter, ctx.arguments().get(2));
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		})) {
			IuTestLogger.allow("iu.crypt", Level.FINE);
			IuHttpAware.mock.when(() -> IuHttp.send(eq(userinfoEndpoint), argThat(a -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Authorization", "Bearer " + accessToken);
				return true;
			}), eq(IuHttp.READ_UTF8)))
					.thenReturn(WebEncryption.to(Encryption.A256GCM, Algorithm.ECDH_ES).key(decryptJwk.wellKnown()) //
							.keyId(dkid) //
							.encrypt(IuJson.object() //
									.add("sub", sub) //
									.build().toString())
							.compact());

			final var principal = authorization.getAuthorizedPrincipal(requestAttributes);
			assertEquals(sub, principal.getName());
			assertEquals(accessToken, principal.getAccessToken(resourceUri));
		}
	}

}
