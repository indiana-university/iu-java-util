package iu.auth.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuClientResource;

@SuppressWarnings("javadoc")
public class AuthorizationClientSpiTest {

	@Test
	public void testClientCredentials() {
		final var endpointUri = URI.create(IdGenerator.generateId());
		final var scope = IdGenerator.generateId();
		final var spi = new AuthorizationClientSpi();
		final Queue<IuClientResource> clients = new ArrayDeque<>();

		final var resourceName = IdGenerator.generateId();
		final var client = mock(IuClientResource.class);
		when(client.getEndpointUri()).thenReturn(endpointUri);
		when(client.getResourceName()).thenReturn(resourceName);
		clients.offer(client);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuClientResource.class)).thenReturn(clients);
			
			final var request = new AuthorizationRequest(endpointUri, scope);
			final var grant = spi.clientCredentials(request);
			assertSame(grant, spi.clientCredentials(request));
		}
	}

}
