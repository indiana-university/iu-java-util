package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.ServiceLoader;

import edu.iu.IuException;
import iu.crypt.spi.IuCryptSpi;

@SuppressWarnings("javadoc")
public class IuCryptApiTestCase {

	static {
		final var serviceLoader = mock(ServiceLoader.class);
		final var spi = mock(IuCryptSpi.class);
		when(serviceLoader.findFirst()).thenReturn(Optional.of(spi));
		try (final var mockServiceLoader = mockStatic(ServiceLoader.class)) {
			mockServiceLoader.when(() -> ServiceLoader.load(IuCryptSpi.class)).thenReturn(serviceLoader);
			IuException.unchecked(() -> Class.forName(Init.class.getName()));
			assertSame(spi, Init.SPI);
		}
	}

}
