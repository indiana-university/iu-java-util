package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.session.IuSession;
import edu.iu.session.IuSessionHandler;
import iu.oidc.client.config.IuOidcClient;
import iu.oidc.client.config.IuOidcClientReference;
import iu.oidc.client.config.IuOidcProvider;

@SuppressWarnings("javadoc")
public class OidcAuthorizationTest {

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

}
