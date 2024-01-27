package edu.iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.auth.spi.IuOpenIdConnectSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class OpenIDProviderTest {

	@Test
	public void testUsesSpiFactory() {
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(IuOpenIdConnectSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuOpenIdConnectSpi.class)).thenReturn(mockSpi);
			final var configUri = mock(URI.class);
			final var mockProvider = mock(IuOpenIdProvider.class);
			when(mockSpi.getOpenIdProvider(configUri)).thenReturn(mockProvider);
			assertSame(mockProvider, IuOpenIdProvider.from(configUri));
		}
	}

}
