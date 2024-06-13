package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.iu.auth.config.IuSamlClient;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.saml.IuSamlSession;
import edu.iu.auth.spi.IuOAuthSpi;
import edu.iu.auth.spi.IuSamlSpi;
import edu.iu.test.IuTest;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuSamlClientTest {

	
@Test
public void testUsesSpiFactory() {
	try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
		final var mockSpi = mock(IuSamlSpi.class);
		mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuSamlSpi.class)).thenReturn(mockSpi);
		final var mockSession = mock(IuSamlSession.class);
		final var uri = mock(URI.class);
		when(mockSpi.createAuthorizationSession("", uri)).thenReturn(mockSession);
		assertSame(mockSession, IuSamlSession.create("", uri));
	}
}


@Test
public void testSamlClientDefault() {
	final var client = 	mock(IuSamlClient.class, withSettings()
            .defaultAnswer(CALLS_REAL_METHODS)
               );

	assertNull(client.getIdentityProviderUri());
	assertEquals(client.getMetadataTtl(), Duration.ofMinutes(5L));
	assertEquals(client.getAuthenticatedSessionTimeout(), Duration.ofHours(12L));
	assertFalse(client.isFailOnAddressMismatch());
}
}
