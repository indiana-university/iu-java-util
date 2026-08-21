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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.IuBadRequestException;
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
		iu.jwt.spi.Init.init();
	}

	private static URI configureRedirectUri(IuOidcClientReference config, IuRequestAttributes requestAttributes) {
		final var redirectUri = URI.create(IdGenerator.generateId());
		when(config.getRedirectUri()).thenReturn(redirectUri);
		when(requestAttributes.getRequestUri()).thenReturn(redirectUri);
		return redirectUri;
	}

	@Test
	void testInit() throws IOException {
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
	void testInitRecordsCallerDetailBeforeStoringTheSession() throws IOException {
		// a caller has no other opportunity to write state that must survive the round
		// trip: the session is created and stored entirely within init
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);
		when(sessionHandler.create()).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(IdGenerator.generateId());

		final var provider = mock(IuOidcProvider.class);
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(URI.create(IdGenerator.generateId()));
		when(provider.getMetadata()).thenReturn(metadata);

		final var config = mock(IuOidcClientReference.class);
		when(config.getRedirectUri()).thenReturn(URI.create(IdGenerator.generateId()));
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var received = new ArrayDeque<IuSession>();
		assertEquals(setCookie,
				new OidcAuthorization(config).init(null, null, received::push).getSetCookie());

		// the session it receives is the one about to be stored, and it is handed over
		// before the store, so one write carries both this flow's detail and the
		// caller's
		assertEquals(1, received.size());
		assertSame(session, received.peek());

		final var order = inOrder(preAuth, sessionHandler);
		order.verify(preAuth).setNonce(any());
		order.verify(sessionHandler).store(session);
	}

	@Test
	void testInitWithExtras() throws IOException {
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);
		when(sessionHandler.create()).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var appUri = URI.create(IdGenerator.generateId());
		final var redirectUri = URI.create(IdGenerator.generateId());
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var scope = IdGenerator.generateId();

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getResourceUri()).thenReturn(resourceUri);

		final var provider = mock(IuOidcProvider.class);
		final var authorizationEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		final var config = mock(IuOidcClientReference.class);
		when(config.getRedirectUri()).thenReturn(redirectUri);
		when(config.getResourceUri()).thenReturn(appUri);
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

	@Test
	void testAuthorizeRedirectUriMismatch() {
		final var expected = URI.create(IdGenerator.generateId());
		final var actual = URI.create(IdGenerator.generateId());
		final var sessionHandler = mock(IuSessionHandler.class);
		final var config = mock(IuOidcClientReference.class);
		when(config.getRedirectUri()).thenReturn(expected);
		when(config.getSessionHandler()).thenReturn(sessionHandler);
		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getRequestUri()).thenReturn(actual);

		final var authorization = new OidcAuthorization(config);
		assertEquals("redirect_uri mismatch, expected " + expected,
				assertThrows(IuBadRequestException.class,
						() -> authorization.authorize(requestAttributes, IdGenerator.generateId(),
								IdGenerator.generateId()))
						.getMessage());
		verifyNoInteractions(sessionHandler);
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeMissingSession() throws IOException {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var sessionHandler = mock(IuSessionHandler.class);

		final var config = mock(IuOidcClientReference.class);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);
		configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		assertEquals("missing or expired preAuth session", assertThrows(IllegalStateException.class,
				() -> authorization.authorize(requestAttributes, code, IdGenerator.generateId())).getMessage());
		assertNull(authorization.getAuthorizedPrincipal(requestAttributes));
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeMissingState() {
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
		configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);

		assertEquals("missing state parameter", assertThrows(IuBadRequestException.class,
				() -> authorization.authorize(requestAttributes, code, null)).getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeInvalidSessionNullState() {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var state = IdGenerator.generateId();
		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(null);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var config = mock(IuOidcClientReference.class);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);
		configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);

		assertEquals("invalid pre-auth session; missing state", assertThrows(IllegalStateException.class,
				() -> authorization.authorize(requestAttributes, code, state)).getMessage());
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
		configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);

		final var wrongState = IdGenerator.generateId();
		assertEquals("state mismatch " + wrongState + " preAuth=" + preAuth, assertThrows(IllegalStateException.class,
				() -> authorization.authorize(requestAttributes, code, wrongState)).getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorize() throws IOException {
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
		final var redirectUri = configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		when(idToken.getNonce()).thenReturn(nonce);
		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			assertEquals(redirectUri, ctx.arguments().get(2));
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
	void testAuthorizeNoNonce() throws IOException {
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
		final var redirectUri = configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			assertEquals(redirectUri, ctx.arguments().get(2));
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
		final var redirectUri = configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			assertEquals(redirectUri, ctx.arguments().get(2));
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
		final var redirectUri = configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		when(idToken.getNonce()).thenReturn(nonce);
		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			assertEquals(redirectUri, ctx.arguments().get(2));
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
		final var redirectUri = configureRedirectUri(config, requestAttributes);

		final var code = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(response.getExpiresIn()).thenReturn(1);

		when(idToken.getNonce()).thenReturn(IdGenerator.generateId());
		try (final var mockAuthorizationGrant = mockConstruction(AuthorizationGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(code, ctx.arguments().get(1));
			assertEquals(redirectUri, ctx.arguments().get(2));
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
	void testGetPrincipalAfterInvalidInitialTokenAndFailedRefresh() throws IOException {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var initialResponse = mock(IuOidcTokenResponse.class);
		when(initialResponse.getIdToken()).thenThrow(new IllegalArgumentException("invalid initial token"));
		final var notAfter = Instant.now().plusSeconds(60L);
		final var postAuth = mock(OidcPostAuthSession.class);
		when(postAuth.getTokenResponse()).thenReturn(initialResponse);
		when(postAuth.getNotAfter()).thenReturn(notAfter);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getTokenEndpoint()).thenReturn(tokenEndpoint);
		final var provider = mock(IuOidcProvider.class);
		when(provider.getMetadata()).thenReturn(metadata);

		final var config = mock(IuOidcClientReference.class);
		when(config.getSessionHandler()).thenReturn(sessionHandler);
		when(config.getProvider()).thenReturn(provider);

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var refreshFailure = new IOException("refresh failed");
		IuHttpAware.mock.when(() -> IuHttp.send(eq(IOException.class), eq(tokenEndpoint), argThat(a -> true),
				eq(IuHttp.READ_JSON_OBJECT))).thenThrow(refreshFailure);
		IuTestLogger.expect(OidcTokenGrant.class.getName(), Level.INFO, "initial token response invalid",
				IllegalArgumentException.class);
		IuTestLogger.expect(OidcAuthorization.class.getName(), Level.INFO,
				"refresh token failed after ID token expired", IOException.class);

		final var authorization = new OidcAuthorization(config);
		assertNull(authorization.getAuthorizedPrincipal(requestAttributes));
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipal() throws IOException {
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

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getDecryptJwk()).thenReturn(null);
		when(client.getResourceUri()).thenReturn(resourceUri);

		final var provider = mock(IuOidcProvider.class);
		final var userinfoEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getUserinfoEndpoint()).thenReturn(userinfoEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

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

			assertNotNull(principal.getSetCookie());

			when(postAuth.isStrict()).thenReturn(true);
			assertNull(authorization.getAuthorizedPrincipal(requestAttributes).getSetCookie());
			
			final var wrongUri = URI.create(IdGenerator.generateId());
			assertEquals("invalid resource URI " + wrongUri + "; access token not verified",
					assertThrows(NullPointerException.class, () -> principal.getAccessToken(wrongUri)).getMessage());

			assertEquals(apiAccessToken2, principal.getAccessToken(apiResourcev2));
			assertEquals(apiAccessToken2, principal.getAccessToken(apiResourcev2));
			assertEquals(1, mockOboGrant.constructed().size());

			assertEquals(apiAccessToken1, principal.getAccessToken(apiResourcev1));

			final var oboFailure = new IOException(IdGenerator.generateId());
			when(mockOboGrant.constructed().get(1).getTokenResponse()).thenThrow(oboFailure);
			assertSame(oboFailure, assertThrows(IOException.class, () -> principal.getAccessToken(apiResourcev1)));
			assertEquals(1, oboFailure.getSuppressed().length);
			assertEquals(IllegalArgumentException.class, oboFailure.getSuppressed()[0].getClass());
			assertEquals("invalid resource URI " + apiResourcev1 + "; access token not verified",
					oboFailure.getSuppressed()[0].getMessage());

		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalAccessTokenLookupUsesJwtAudienceMatch() throws IOException {
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

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getDecryptJwk()).thenReturn(null);
		when(client.getResourceUri()).thenReturn(resourceUri);

		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		final var jwksUri = URI.create(IdGenerator.generateId());

		final var provider = mock(IuOidcProvider.class);
		final var userinfoEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getUserinfoEndpoint()).thenReturn(userinfoEndpoint);
		when(metadata.getJwksUri()).thenReturn(jwksUri);
		when(provider.getMetadata()).thenReturn(metadata);

		IuHttpAware.mock.when(() -> IuHttp.get(jwksUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("keys", IuJson.array().add(IuJson.parse(issuerKey.wellKnown().toString()))) //
				.build());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		final var apiResourcev1 = URI.create(apiResource + "/v1");
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResource, apiResourcev1));

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var refreshToken = IdGenerator.generateId();
		final var sub = IdGenerator.generateId();

		final var accessToken = WebToken.builder() //
				.jti() //
				.iss(URI.create(IdGenerator.generateId())) //
				.aud(apiResourcev1) //
				.sub(sub) //
				.iat() //
				.exp(Instant.now().plusSeconds(60L)) //
				.build() //
				.sign("JWT", Algorithm.EDDSA, issuerKey);

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

		try (final var mockRefreshGrant = mockConstruction(RefreshTokenGrant.class, (a, ctx) -> {
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		}); final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class, (a, ctx) -> fail(
				"should not exchange access token when the JWT audience already includes the requested resource"))) {
			IuHttpAware.mock.when(() -> IuHttp.send(eq(userinfoEndpoint), argThat(a -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Authorization", "Bearer " + accessToken);
				return true;
			}), eq(IuHttp.READ_UTF8))).thenReturn(IuJson.object() //
					.add("sub", sub) //
					.build().toString());

			final var principal = authorization.getAuthorizedPrincipal(requestAttributes);
			assertEquals(accessToken, principal.getAccessToken(apiResourcev1));
			assertEquals(0, mockOboGrant.constructed().size());
		}
	}

	/**
	 * Sets up an authorized session whose token response returns
	 * {@code accessToken}, then asserts that looking up an access token for a
	 * resource outside the client's own resource URI falls back to an
	 * on-behalf-of exchange rather than returning {@code accessToken} directly.
	 *
	 * @param accessToken  access token to return from the (refreshed) token
	 *                     response
	 * @param jwksUri      issuer JWKS URI to mock; null if JWT verification is
	 *                     expected to fail before reaching the JWKS lookup
	 * @param publishedKey key to publish at {@code jwksUri}; ignored if
	 *                     {@code jwksUri} is null
	 */
	private void assertAccessTokenLookupFallsBackToOnBehalfOf(String accessToken, URI jwksUri, WebKey publishedKey)
			throws IOException {
		assertAccessTokenLookupFallsBackToOnBehalfOf(accessToken, jwksUri, publishedKey, null);
	}

	@SuppressWarnings("unchecked")
	private void assertAccessTokenLookupFallsBackToOnBehalfOf(String accessToken, URI jwksUri, WebKey publishedKey,
			IOException oboFailure) throws IOException {
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(IdGenerator.generateId());
		when(client.getDecryptJwk()).thenReturn(null);
		when(client.getResourceUri()).thenReturn(resourceUri);

		final var provider = mock(IuOidcProvider.class);
		final var userinfoEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getUserinfoEndpoint()).thenReturn(userinfoEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);

		if (jwksUri != null) {
			when(metadata.getJwksUri()).thenReturn(jwksUri);
			IuHttpAware.mock.when(() -> IuHttp.get(jwksUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
					.add("keys", IuJson.array().add(IuJson.parse(publishedKey.wellKnown().toString()))) //
					.build());
		}

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		final var apiResourcev1 = URI.create(apiResource + "/v1");
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResource, apiResourcev1));

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var sub = IdGenerator.generateId();

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(idToken.getSubject()).thenReturn(sub);

		when(response.getAccessToken()).thenReturn(accessToken);
		when(response.getExpiresIn()).thenReturn(1);

		final var notAfter = Instant.now().plusSeconds(1L);
		when(postAuth.getTokenResponse()).thenReturn(response);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		final var apiAccessToken1 = IdGenerator.generateId();
		final var oboResponse1 = mock(IuOidcTokenResponse.class);
		when(oboResponse1.getAccessToken()).thenReturn(apiAccessToken1);
		when(oboResponse1.getExpiresIn()).thenReturn(1);

		try (final var mockRefreshGrant = mockConstruction(RefreshTokenGrant.class, (a, ctx) -> {
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		}); final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class, (a, ctx) -> {
			assertEquals(config, ctx.arguments().get(0));
			assertEquals(apiResourcev1, ctx.arguments().get(1));
			assertEquals(accessToken, ctx.arguments().get(2));
			if (oboFailure == null)
				when(a.getTokenResponse()).thenReturn(oboResponse1);
			else
				when(a.getTokenResponse()).thenThrow(oboFailure);
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
			if (oboFailure == null)
				assertEquals(apiAccessToken1, principal.getAccessToken(apiResourcev1));
			else {
				assertSame(oboFailure, assertThrows(IOException.class, () -> principal.getAccessToken(apiResourcev1)));
				assertEquals(1, oboFailure.getSuppressed().length);
				assertEquals(IllegalArgumentException.class, oboFailure.getSuppressed()[0].getClass());
			}
			assertEquals(1, mockOboGrant.constructed().size());
		}
	}

	@Test
	void testGetPrincipalAccessTokenLookupIncludesAudienceWhenOnBehalfOfFails() throws IOException {
		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		final var jwksUri = URI.create(IdGenerator.generateId());

		// signed and verifiable, but for a different audience than the requested
		// resource, so a failed on-behalf-of exchange includes the audience context
		final var accessToken = WebToken.builder() //
				.jti() //
				.iss(URI.create(IdGenerator.generateId())) //
				.aud(URI.create(IdGenerator.generateId())) //
				.sub(IdGenerator.generateId()) //
				.iat() //
				.exp(Instant.now().plusSeconds(60L)) //
				.build() //
				.sign("JWT", Algorithm.EDDSA, issuerKey);

		assertAccessTokenLookupFallsBackToOnBehalfOf(accessToken, jwksUri, issuerKey,
				new IOException(IdGenerator.generateId()));
	}

	@Test
	void testGetPrincipalAccessTokenLookupFallsBackWhenJwtHasNoAudience() throws IOException {
		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		final var jwksUri = URI.create(IdGenerator.generateId());

		// signed and verifiable, but carries no aud claim to match against
		final var accessToken = WebToken.builder() //
				.jti() //
				.iss(URI.create(IdGenerator.generateId())) //
				.sub(IdGenerator.generateId()) //
				.iat() //
				.exp(Instant.now().plusSeconds(60L)) //
				.build() //
				.sign("JWT", Algorithm.EDDSA, issuerKey);

		assertAccessTokenLookupFallsBackToOnBehalfOf(accessToken, jwksUri, issuerKey);
	}

	@Test
	void testGetPrincipalAccessTokenLookupFallsBackWhenJwtHasNoKeyId() throws IOException {
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).ephemeral().build();

		// signed without a key ID, so the issuer's signing key can't be identified;
		// verification never reaches the JWKS lookup
		final var accessToken = WebToken.builder() //
				.jti() //
				.iss(URI.create(IdGenerator.generateId())) //
				.sub(IdGenerator.generateId()) //
				.iat() //
				.exp(Instant.now().plusSeconds(60L)) //
				.build() //
				.sign("JWT", Algorithm.EDDSA, issuerKey);

		assertAccessTokenLookupFallsBackToOnBehalfOf(accessToken, null, null);
	}

	@Test
	void testGetPrincipalAccessTokenLookupFallsBackWhenJwtKeyIdUnknown() throws IOException {
		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		final var publishedKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA)
				.keyId(IdGenerator.generateId()).ephemeral().build();
		final var jwksUri = URI.create(IdGenerator.generateId());

		// signed with a key ID the issuer hasn't published
		final var accessToken = WebToken.builder() //
				.jti() //
				.iss(URI.create(IdGenerator.generateId())) //
				.sub(IdGenerator.generateId()) //
				.iat() //
				.exp(Instant.now().plusSeconds(60L)) //
				.build() //
				.sign("JWT", Algorithm.EDDSA, issuerKey);

		assertAccessTokenLookupFallsBackToOnBehalfOf(accessToken, jwksUri, publishedKey);
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalAccessTokenLookupThrowsWhenJwtSignatureInvalid() throws IOException {
		// a kid the issuer publishes identifies the token as one of theirs; if it
		// doesn't actually verify, that's a hard failure, not a signal to fall back
		// to an on-behalf-of exchange
		final var cookies = (Iterable<HttpCookie>) mock(Iterable.class);
		final var setCookie = IdGenerator.generateId();
		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);

		when(sessionHandler.activate(cookies)).thenReturn(session);
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(IdGenerator.generateId());
		when(client.getDecryptJwk()).thenReturn(null);
		when(client.getResourceUri()).thenReturn(resourceUri);

		final var keyId = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		// same key ID published, but different key material, so signature
		// verification fails even though the key is found
		final var wrongKey = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).keyId(keyId).ephemeral()
				.build();
		final var jwksUri = URI.create(IdGenerator.generateId());

		final var provider = mock(IuOidcProvider.class);
		final var userinfoEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getUserinfoEndpoint()).thenReturn(userinfoEndpoint);
		when(metadata.getJwksUri()).thenReturn(jwksUri);
		when(provider.getMetadata()).thenReturn(metadata);

		IuHttpAware.mock.when(() -> IuHttp.get(jwksUri, IuHttp.READ_JSON_OBJECT)).thenReturn(IuJson.object() //
				.add("keys", IuJson.array().add(IuJson.parse(wrongKey.wellKnown().toString()))) //
				.build());

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);
		when(config.getResourceUri()).thenReturn(resourceUri);
		when(config.getSessionHandler()).thenReturn(sessionHandler);

		final var apiResource = URI.create("api://" + IdGenerator.generateId());
		final var apiResourcev1 = URI.create(apiResource + "/v1");
		when(config.getApiResources()).thenReturn(IuIterable.iter(apiResource, apiResourcev1));

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var sub = IdGenerator.generateId();

		final var accessToken = WebToken.builder() //
				.jti() //
				.iss(URI.create(IdGenerator.generateId())) //
				.sub(sub) //
				.iat() //
				.exp(Instant.now().plusSeconds(60L)) //
				.build() //
				.sign("JWT", Algorithm.EDDSA, issuerKey);

		final var authorization = new OidcAuthorization(config);
		final var response = mock(IuOidcTokenResponse.class);
		final var idToken = mock(WebToken.class);
		when(idToken.getSubject()).thenReturn(sub);

		when(response.getAccessToken()).thenReturn(accessToken);
		when(response.getExpiresIn()).thenReturn(1);

		final var notAfter = Instant.now().plusSeconds(1L);
		when(postAuth.getTokenResponse()).thenReturn(response);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		try (final var mockRefreshGrant = mockConstruction(RefreshTokenGrant.class, (a, ctx) -> {
			when(a.getTokenResponse()).thenReturn(response);
			when(a.getIdToken()).thenReturn(idToken);
		}); final var mockOboGrant = mockConstruction(OnBehalfOfGrant.class, (a, ctx) -> fail(
				"a token identified by a published kid that fails verification must not be treated as a fallback signal"))) {
			IuHttpAware.mock.when(() -> IuHttp.send(eq(userinfoEndpoint), argThat(a -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Authorization", "Bearer " + accessToken);
				return true;
			}), eq(IuHttp.READ_UTF8))).thenReturn(IuJson.object() //
					.add("sub", sub) //
					.build().toString());

			final var principal = authorization.getAuthorizedPrincipal(requestAttributes);
			assertThrows(IllegalArgumentException.class, () -> principal.getAccessToken(apiResourcev1));
			assertEquals(0, mockOboGrant.constructed().size());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalWithRefreshAndEncrypted() throws IOException {
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

		final var resourceUri = URI.create(IdGenerator.generateId());

		final var clientId = IdGenerator.generateId();
		final var client = mock(IuOidcClient.class);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getResourceUri()).thenReturn(resourceUri);
		final var dkid = IdGenerator.generateId();
		final var decryptJwk = WebKey.builder(WebKey.Type.X25519).algorithm(Algorithm.ECDH_ES).keyId(dkid).ephemeral()
				.build();
		when(client.getDecryptJwk()).thenReturn(IuIterable.iter(decryptJwk));

		final var provider = mock(IuOidcProvider.class);
		final var userinfoEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getUserinfoEndpoint()).thenReturn(userinfoEndpoint);
		when(provider.getMetadata()).thenReturn(metadata);


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

			assertNotNull(principal.getSetCookie());
		}
	}


}
