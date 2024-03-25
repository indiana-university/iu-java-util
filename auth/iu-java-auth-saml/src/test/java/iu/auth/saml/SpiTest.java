package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class SpiTest {

	@Test
	public void testSamlSpi() {
		final var spi = new SamlConnectSpi();
		final var client = mock(SamlClient.class);

		assertThrows(UnsupportedOperationException.class, () -> spi.getSamlProvider(client));
	}

}
