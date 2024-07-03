package edu.iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;

@SuppressWarnings("javadoc")
public class IuSamlSessionTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testCreate() {
		final var entryPointUri = mock(URI.class);
		final var postUri = mock(URI.class);
		final var secretKey = mock(Supplier.class);
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(IuSamlSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuSamlSpi.class)).thenReturn(mockSpi);
			final var mockSession = mock(IuSamlSession.class);
			when(mockSpi.createSession(entryPointUri, postUri, secretKey)).thenReturn(mockSession);
			assertSame(mockSession, IuSamlSession.create(entryPointUri, postUri, secretKey));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testActivate() {
		final var tokenizedSession = IdGenerator.generateId();
		final var secretKey = mock(Supplier.class);
		try (final var mockSpiFactory = mockStatic(IuAuthSpiFactory.class)) {
			final var mockSpi = mock(IuSamlSpi.class);
			mockSpiFactory.when(() -> IuAuthSpiFactory.get(IuSamlSpi.class)).thenReturn(mockSpi);
			final var mockSession = mock(IuSamlSession.class);
			when(mockSpi.activateSession(tokenizedSession, secretKey)).thenReturn(mockSession);
			assertSame(mockSession, IuSamlSession.activate(tokenizedSession, secretKey));
		}
	}

}
