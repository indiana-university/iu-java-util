package iu.auth.bundle;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationSession;

@SuppressWarnings("javadoc")
public class OAuthIT {

	@Test
	public void testClient() throws URISyntaxException {
		final var realm = IdGenerator.generateId();
		final var resourceUri = new URI("test:" + IdGenerator.generateId());
		final var client = mock(IuAuthorizationClient.class);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);

		assertNotNull(IuAuthorizationClient.initialize(client));
		assertNotNull(IuAuthorizationSession.create(realm, resourceUri));
	}

}
