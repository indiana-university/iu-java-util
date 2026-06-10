package iu.oidc.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;

@SuppressWarnings("javadoc")
public class IuOidcClientReferenceTest {

	@Test
	void testDefaults() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var client = mock(IuOidcClient.class);
		when(client.getResourceUri()).thenReturn(resourceUri);

		final var clientRef = mock(IuOidcClientReference.class, CALLS_REAL_METHODS);
		when(clientRef.getClient()).thenReturn(client);

		final var adapter = mock(IuJsonAdapter.class);
		try (final var mockIuJsonAdapter = mockStatic(IuJsonAdapter.class)) {
			mockIuJsonAdapter.when(
					() -> IuJsonAdapter.adapt((Type) getClass(), IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES))
					.thenReturn(adapter);

			assertEquals(resourceUri, clientRef.getResourceUri());
			assertNull(clientRef.getRedirectUri());
			assertNull(clientRef.getScope());
			assertNull(clientRef.getApiResources());
			assertNull(clientRef.getSessionHandler());
			assertEquals(adapter, clientRef.adaptJson(getClass()));
		}
	}

}
