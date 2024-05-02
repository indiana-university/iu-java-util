package edu.iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;
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
	final var client = IuTest.mockWithDefaults(IuSamlClient.class);
	assertNull(client.getIdentityProviderUri());
	assertEquals(client.getMetadataTtl(), Duration.ofMillis(300000L));
	assertFalse(client.failOnAddressMismatch());
}
}
