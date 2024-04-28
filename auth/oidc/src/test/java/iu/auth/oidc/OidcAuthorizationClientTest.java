package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuTokenResponse;
import edu.iu.auth.oidc.IuAuthoritativeOpenIdClient;
import edu.iu.client.IuJson;

@SuppressWarnings("javadoc")
public class OidcAuthorizationClientTest extends IuOidcTestCase {

	@SuppressWarnings("unchecked")
	@Test
	public void testConfig() {
		final var client = mock(IuAuthoritativeOpenIdClient.class);

		final var provider = mock(OpenIdProvider.class);
		when(provider.client()).thenReturn(client);
		when(provider.authClient()).thenReturn(client);

		final var tokenEndpoint = URI.create("test:" + IdGenerator.generateId());
		final var authorizationEndpoint = URI.create("test:" + IdGenerator.generateId());
		when(provider.config()).thenReturn(IuJson.object() //
				.add("token_endpoint", tokenEndpoint.toString()) //
				.add("authorization_endpoint", authorizationEndpoint.toString()) //
				.build());

		final var authClient = new OidcAuthorizationClient(provider);

		final var realm = IdGenerator.generateId();
		when(client.getRealm()).thenReturn(realm);
		assertEquals(realm, authClient.getRealm());

		final var resourceUri = URI.create("test:" + IdGenerator.generateId());
		when(client.getResourceUri()).thenReturn(resourceUri);
		assertEquals(resourceUri, authClient.getResourceUri());

		final var redirectUri = URI.create("test:" + IdGenerator.generateId());
		when(client.getRedirectUri()).thenReturn(redirectUri);
		assertEquals(redirectUri, authClient.getRedirectUri());

		final var credentials = mock(IuApiCredentials.class);
		when(client.getCredentials()).thenReturn(credentials);
		assertSame(credentials, authClient.getCredentials());

		when(client.getScope()).thenReturn(null, List.of("email", "profile", "openid"));
		assertTrue(IuIterable.remaindersAreEqual(List.of("openid").iterator(), authClient.getScope().iterator()));
		assertTrue(IuIterable.remaindersAreEqual(List.of("openid", "email", "profile").iterator(),
				authClient.getScope().iterator()), () -> authClient.getScope().toString());

		final var authnTimeout = Duration.ofSeconds(1L);
		when(client.getAuthenticationTimeout()).thenReturn(authnTimeout);
		assertEquals(authnTimeout, authClient.getAuthenticationTimeout());

		final var authzTimeout = Duration.ofSeconds(2L);
		when(client.getAuthenticatedSessionTimeout()).thenReturn(null, authzTimeout);

		final var nonce = IdGenerator.generateId();
		final var nonce2 = IdGenerator.generateId();
		when(client.createNonce()).thenReturn(nonce, nonce2);
		final var authCodeAttributes = authClient.getAuthorizationCodeAttributes();
		assertEquals(1, authCodeAttributes.size());
		assertEquals(nonce, authCodeAttributes.get("nonce"));

		assertEquals(authzTimeout, authClient.getAuthorizationTimeToLive());

		when(client.getAuthorizationCodeAttributes()).thenReturn(null, Map.of("foo", "bar"));
		final var authCodeAttributes2 = authClient.getAuthorizationCodeAttributes();
		assertEquals(2, authCodeAttributes2.size());
		assertEquals("2", authCodeAttributes2.get("max_age"));
		assertEquals(nonce2, authCodeAttributes2.get("nonce"));

		final var authCodeAttributes3 = authClient.getAuthorizationCodeAttributes();
		assertEquals(3, authCodeAttributes3.size());
		assertEquals("2", authCodeAttributes3.get("max_age"));
		assertEquals(nonce2, authCodeAttributes3.get("nonce"));
		assertEquals("bar", authCodeAttributes3.get("foo"));

		final var ccAttributes = mock(Map.class);
		when(client.getClientCredentialsAttributes()).thenReturn(ccAttributes);
		assertSame(ccAttributes, authClient.getClientCredentialsAttributes());

		assertEquals(tokenEndpoint, authClient.getTokenEndpoint());
		assertEquals(authorizationEndpoint, authClient.getAuthorizationEndpoint());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testVerify() {
		final var client = mock(IuAuthoritativeOpenIdClient.class);
		final var provider = mock(OpenIdProvider.class);
		when(provider.client()).thenReturn(client);
		when(provider.authClient()).thenReturn(client);

		when(provider.config()).thenReturn(IuJson.object() //
				.build());

		final var authClient = new OidcAuthorizationClient(provider);
		final var tokenResponse = mock(IuTokenResponse.class);
		assertEquals("missing openid scope",
				assertThrows(IllegalArgumentException.class, () -> authClient.verify(tokenResponse)).getMessage());

		when(tokenResponse.getScope()).thenReturn(List.of("openid"));

		final var idToken = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var principal = IdGenerator.generateId();
		when(provider.getClaims(idToken, accessToken)).thenReturn((Map) Map.of("principal", principal));
		when(tokenResponse.getTokenAttributes()).thenReturn((Map) Map.of("id_token", idToken));
		when(tokenResponse.getAccessToken()).thenReturn(accessToken);
		authClient.verify(tokenResponse);

		assertThrows(UnsupportedOperationException.class, () -> authClient.verify(null, null));
	}

}
