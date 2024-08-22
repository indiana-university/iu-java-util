package edu.iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.auth.spi.IuSessionHandlerSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuSessionHandlerTest {

	@Test
	public void testCreateSession() {
		final var resourceUri = mock(URI.class);
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(IuSessionHandlerSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuSessionHandlerSpi.class)).thenReturn(mockSpi);
			final var mockSessionHandler = mock(IuSessionHandler.class);
			when(mockSpi.createSession(resourceUri)).thenReturn(mockSessionHandler);
			assertSame(mockSessionHandler, IuSessionHandler.createSession(resourceUri));
		}
	}
}
