package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.IuOidcTokenResponse;
import iu.oidc.client.config.IuOidcClient;
import iu.oidc.client.config.IuOidcClientReference;
import iu.oidc.client.config.IuOidcProvider;

@SuppressWarnings("javadoc")
public class RefreshTokenGrantTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	@Test
	public void testGrantType() {
		final var clientId = IdGenerator.generateId();
		final var assertionJwk = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).ephemeral().build();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getAssertionJwk()).thenReturn(assertionJwk);

		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getTokenEndpoint()).thenReturn(tokenEndpoint);
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getMetadata()).thenReturn(metadata);

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);

		final var refreshToken = IdGenerator.generateId();
		final var response = mock(IuOidcTokenResponse.class);
		when(response.getRefreshToken()).thenReturn(refreshToken);
		final var notAfter = Instant.now();
		final var grant = new RefreshTokenGrant(config, response, notAfter);
		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		final var rb = mock(HttpRequest.Builder.class);
		grant.tokenAuth(rb, params);
		assertEquals("refresh_token", params.get("grant_type").iterator().next());
		assertEquals(refreshToken, params.get("refresh_token").iterator().next());
		assertEquals(clientId, params.get("client_id").iterator().next());
		final var assertion = WebToken.verify(params.get("client_assertion").iterator().next(), assertionJwk);
		assertion.validateClaims(URI.create(clientId), tokenEndpoint, Duration.ofMinutes(15L));
		assertEquals(clientId, assertion.getSubject());
	}

}
