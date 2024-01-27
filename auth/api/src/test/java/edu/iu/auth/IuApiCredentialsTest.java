package edu.iu.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import edu.iu.auth.spi.IuBasicAuthSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuApiCredentialsTest {

	@Test
	public void testBasicAuth() {
		final var basicAuthSpi = mock(IuBasicAuthSpi.class);
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuBasicAuthSpi.class)).thenReturn(basicAuthSpi);
			IuApiCredentials.basic("foo", "bar");
			verify(basicAuthSpi).createCredentials("foo", "bar");
		}
	}

}
