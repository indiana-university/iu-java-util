package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mockConstruction;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;

@SuppressWarnings("javadoc")
public class SpiTest {

	@Test
	public void testCreateSession() {
		final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();

		final var spi = new SamlSpi();
		final var entryPoint = IuException.unchecked(() -> new URI("http://foo"));
		try (final var mockSamlSession = mockConstruction(SamlSession.class)) {
			final var samlSession = spi.createSession(entryPoint, () -> secret);
			assertSame(samlSession, mockSamlSession.constructed().get(0));
		}
	}

	@Test
	public void testActivateSession() {
		final var spi = new SamlSpi();
		final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();
		try (final var mockSamlSession = mockConstruction(SamlSession.class)) {
			final var samlSession = spi.activateSession(IdGenerator.generateId(), () -> secret);
			assertSame(samlSession, mockSamlSession.constructed().get(0));
		}

	}

}
