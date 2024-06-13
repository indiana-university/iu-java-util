package edu.iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuSamlSessionTest {

	@Test
	public void testCreate() {
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(IuSamlSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuSamlSpi.class)).thenReturn(mockSpi);
			final var mockSession = mock(IuSamlSession.class);
			final var uri = mock(URI.class);
			when(mockSpi.createAuthorizationSession("", uri)).thenReturn(mockSession);
			assertSame(mockSession, IuSamlSession.create("", uri));
		}
	}

}
