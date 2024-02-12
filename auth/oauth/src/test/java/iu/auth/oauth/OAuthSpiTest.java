package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.IuAuthorizationClient;

@SuppressWarnings("javadoc")
public class OAuthSpiTest {

	@Test
	public void testIsRoot() throws URISyntaxException {
		assertTrue(OAuthSpi.isRoot(new URI("foo:bar"), new URI("foo:bar")));
		assertFalse(OAuthSpi.isRoot(new URI("foo:bar"), new URI("foo:baz")));
		assertFalse(OAuthSpi.isRoot(new URI("foo:/bar"), new URI("foo:/baz")));
		assertFalse(OAuthSpi.isRoot(new URI("foo:/bar"), new URI("/baz")));
		assertFalse(OAuthSpi.isRoot(new URI("bar:/foo"), new URI("foo:/bar")));
		assertFalse(OAuthSpi.isRoot(new URI("foo://bar/baz"), new URI("foo://baz/bar")));
		assertTrue(OAuthSpi.isRoot(new URI("foo://bar/baz"), new URI("foo://bar/baz/foo")));
		assertTrue(OAuthSpi.isRoot(new URI("foo://bar/baz/"), new URI("foo://bar/baz/foo")));
		assertFalse(OAuthSpi.isRoot(new URI("foo://bar/baz"), new URI("foo://bar/bazfoo")));
	}

	@Test
	public void testInitClient() {
		final var spi = new OAuthSpi();
		final var realm = IdGenerator.generateId();
		assertThrows(IllegalStateException.class, () -> OAuthSpi.getClient(realm));

		final var client = mock(IuAuthorizationClient.class);
		when(client.getRealm()).thenReturn(realm);
		final var uri = mock(URI.class);
		when(client.getResourceUri()).thenReturn(uri);
		assertThrows(IllegalArgumentException.class, () -> spi.initialize(client));

		when(uri.isOpaque()).thenReturn(true, false);
		assertThrows(IllegalArgumentException.class, () -> spi.initialize(client));

		when(uri.isAbsolute()).thenReturn(true);
		spi.initialize(client);
		assertSame(client, OAuthSpi.getClient(realm));

		assertThrows(IllegalStateException.class, () -> spi.initialize(client));
	}

	@Test
	public void testCreateAuthorizationSession() {
		final var spi = new OAuthSpi();
		final var realm = IdGenerator.generateId();
		final var entryPoint = mock(URI.class);
		try (final var mockAuthSession = mockConstruction(AuthorizationSession.class)) {
			final var authSession = spi.createAuthorizationSession(realm, entryPoint);
			assertSame(authSession, mockAuthSession.constructed().get(0));
		}
	}
}
