package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class JwkPrincipalVerifierTest {

	@Test
	public void testVerifier() {
		final var uri = URI.create("test:" + IdGenerator.generateId());
		final var keyId = IdGenerator.generateId();
		final var jwkId = new JwkPrincipal(uri, keyId);
		final var jwkId2 = new JwkPrincipal(uri, keyId);

		final var verifier = new JwkPrincipalVerifier(jwkId);
		assertNull(verifier.getAuthenticationEndpoint());
		assertNull(verifier.getAuthScheme());
		assertEquals(uri + "#" + keyId, verifier.getRealm());
		assertSame(JwkPrincipal.class, verifier.getType());
		assertFalse(verifier.isAuthoritative());

		assertDoesNotThrow(() -> verifier.verify(jwkId, verifier.getRealm()));
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(jwkId, IdGenerator.generateId()));
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(jwkId2, verifier.getRealm()));

	}

}
