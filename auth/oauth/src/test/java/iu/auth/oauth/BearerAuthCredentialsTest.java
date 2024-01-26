package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class BearerAuthCredentialsTest {

	@Test
	public void testAccessToken() {
		final var nonce = IdGenerator.generateId();
		// TODO: create JWT with nonce
		final var auth = new BearerAuthCredentials(nonce);
		assertEquals(nonce, auth.getAccessToken());
		assertThrows(UnsupportedOperationException.class, () -> auth.getName());
	}

}
