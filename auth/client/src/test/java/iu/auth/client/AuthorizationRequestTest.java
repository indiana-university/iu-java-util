package iu.auth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.auth.client.IuAuthorizationRequest;
import edu.iu.auth.config.IuAuthorizationResource;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuClientResource;

@SuppressWarnings("javadoc")
public class AuthorizationRequestTest {

	private URI endpointUri;
	private String resourceName;
	private String[] scope;
	private Queue<IuClientResource> clients;
	private IuAuthorizationResource resource;
	private MockedStatic<AuthConfig> mockAuthConfig;

	@BeforeEach
	public void setup() {
		endpointUri = URI.create(IdGenerator.generateId());
		scope = new String[] { IdGenerator.generateId() };

		resourceName = IdGenerator.generateId();

		final var client = mock(IuClientResource.class);
		when(client.getEndpointUri()).thenReturn(endpointUri);
		when(client.getResourceName()).thenReturn(resourceName);

		clients = new ArrayDeque<>();
		clients.offer(client);

		mockAuthConfig = mockStatic(AuthConfig.class);
		mockAuthConfig.when(() -> AuthConfig.get(IuClientResource.class)).thenReturn(clients);

		resource = mock(IuAuthorizationResource.class);
		mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizationResource.class, resourceName)).thenReturn(resource);
	}

	@AfterEach
	public void close() {
		mockAuthConfig.close();
	}

	@Test
	public void testRequiresConfiguredResourceUri() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var error = assertThrows(NullPointerException.class, () -> new AuthorizationRequest(resourceUri, scope));
		assertEquals("No client resource registered for resource URI", error.getMessage());
	}

	@Test
	public void testNullScope() {
		assertNull(new AuthorizationRequest(endpointUri, (String[]) null).getScope());
		assertNull(new AuthorizationRequest(endpointUri).getScope());
	}

	@Test
	public void testProperties() {
		final var request = new AuthorizationRequest(endpointUri, scope);
		assertEquals(endpointUri, request.getResourceUri());
		assertTrue(IuIterable.remaindersAreEqual(IuIterable.iter(scope).iterator(), request.getScope().iterator()),
				() -> scope + " != " + request.getScope());
	}

	@Test
	public void testToString() {
		final var request = new AuthorizationRequest(endpointUri, scope);
		assertEquals("AuthorizationRequest [endpointUri=" + endpointUri + ", scope=" + Arrays.toString(scope) + "]",
				request.toString());
	}

	@Test
	public void testFrom() {
		final var request = AuthorizationRequest.from(new IuAuthorizationRequest() {
			@Override
			public URI getResourceUri() {
				return endpointUri;
			}

			@Override
			public Iterable<String> getScope() {
				return IuIterable.iter(scope);
			}
		});
		assertEquals(request, new AuthorizationRequest(endpointUri, scope));
		assertSame(request, AuthorizationRequest.from(request));
	}
	
	@Test
	public void testFromNoScope() {
		final var request = AuthorizationRequest.from(new IuAuthorizationRequest() {
			@Override
			public URI getResourceUri() {
				return endpointUri;
			}

			@Override
			public Iterable<String> getScope() {
				return null;
			}
		});
		assertEquals(request, new AuthorizationRequest(endpointUri));
		assertSame(request, AuthorizationRequest.from(request));
	}

	@Test
	public void testEqualsHashCode() {
		final Queue<AuthorizationRequest> requests = new ArrayDeque<>();
		for (var i = 0; i < 2; i++) {
			final var endpointUri = URI.create(IdGenerator.generateId());
			for (var j = 0; j < 2; j++) {
				final var scope = new String[] { IdGenerator.generateId() };

				final var resourceName = IdGenerator.generateId();

				final var client = mock(IuClientResource.class);
				when(client.getEndpointUri()).thenReturn(endpointUri);
				when(client.getResourceName()).thenReturn(resourceName);
				clients.offer(client);

				final var resource = mock(IuAuthorizationResource.class);
				mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizationResource.class, resourceName))
						.thenReturn(resource);

				requests.offer(new AuthorizationRequest(endpointUri, scope));
			}
		}
		for (final var a : requests) {
			for (final var b : requests) {
				if (a == b) {
					assertEquals(a, b);
					assertNotEquals(a, new Object());
					assertEquals(a.hashCode(), b.hashCode());
				} else {
					assertNotEquals(a, b);
					assertNotEquals(b, a);
					assertNotEquals(a.hashCode(), b.hashCode());
				}
			}
		}
	}

}
