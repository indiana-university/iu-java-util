package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class RelayStateTest {

	@Test
	public void getSession() {
		final var uri = URI.create("test://localhost/" + IdGenerator.generateId());

		RelayState state = new RelayState(uri);
		assertNotNull(state.getSession());
	}
}
