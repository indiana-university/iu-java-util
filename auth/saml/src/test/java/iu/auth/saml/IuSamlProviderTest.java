package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.auth.config.IuSamlProvider;
import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuSamlProviderTest {
	
@Test
public void testSpiFactory() {
	try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
		final var mockSpi = mock(IuSamlSpi.class);
		mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuSamlSpi.class)).thenReturn(mockSpi);
		final var mockProvider = mock(SamlServiceProvider.class);
		final var mockClient = mock(IuSamlServiceProviderMetadata.class);
		when(mockSpi.getSamlProvider(mockClient)).thenReturn(mockProvider);
		assertSame(mockProvider, SamlServiceProvider.from(mockClient));
	}
}

}
