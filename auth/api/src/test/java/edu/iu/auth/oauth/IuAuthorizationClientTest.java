package edu.iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.spi.IuOAuthSpi;
import edu.iu.test.IuTest;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuAuthorizationClientTest {

	@Test
	public void testUsesSpiFactory() {
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(IuOAuthSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuOAuthSpi.class)).thenReturn(mockSpi);

			final var mockClient = mock(IuAuthorizationClient.class);
			final var mockGrant = mock(IuAuthorizationGrant.class);
			when(mockSpi.initialize(mockClient)).thenReturn(mockGrant);
			assertSame(mockGrant, IuAuthorizationClient.initialize(mockClient));

			final var mockSession = mock(IuAuthorizationSession.class);
			final var uri = mock(URI.class);
			when(mockSpi.createAuthorizationSession("", uri)).thenReturn(mockSession);
			assertSame(mockSession, IuAuthorizationSession.create("", uri));
		}
	}

	@Test
	public void testClientDefaults() {
		final var client = IuTest.mockWithDefaults(IuAuthorizationClient.class);
		assertNull(client.getAuthorizationEndpoint());
		assertNull(client.getRedirectUri());
		assertNull(client.getAuthorizationCodeAttributes());
		assertNull(client.getClientCredentialsAttributes());
		final var a = mock(IuApiCredentials.class);
		assertDoesNotThrow(() -> client.revoke(a));
	}

}
