package iu.auth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuAuthorizationResource;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuClientResource;

@SuppressWarnings("javadoc")
public class ClientCredentialsGrantTest {

	@Test
	public void testAuthorize() {
		final var endpointUri = URI.create(IdGenerator.generateId());
		final var scope = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();

		final Queue<IuClientResource> clients = new ArrayDeque<>();
		final var resourceName = IdGenerator.generateId();
		final var client = mock(IuClientResource.class);
		when(client.getEndpointUri()).thenReturn(endpointUri);
		when(client.getResourceName()).thenReturn(resourceName);
		clients.offer(client);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuClientResource.class)).thenReturn(clients);
			final var request = new AuthorizationRequest(endpointUri, scope);
			final var grant = new ClientCredentialsGrant(request);

			final var resource = mock(IuAuthorizationResource.class);

			final var bearer = grant.authorize();
			assertEquals(accessToken, bearer.getAccessToken());
		}
	}

}
