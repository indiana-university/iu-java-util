package edu.iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.auth.oidc.spi.OpenIDConnectSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class OpenIDProviderTest {

	@Test
	public void testUsesSpiFactory() {
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(OpenIDConnectSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(OpenIDConnectSpi.class)).thenReturn(mockSpi);
			final var mockClient = mock(OpenIDClient.class);
			final var mockProvider = mock(OpenIDProvider.class);
			when(mockSpi.getOpenIDProvider(mockClient)).thenReturn(mockProvider);
			assertSame(mockProvider, OpenIDProvider.forClient(mockClient));
		}
	}

}
