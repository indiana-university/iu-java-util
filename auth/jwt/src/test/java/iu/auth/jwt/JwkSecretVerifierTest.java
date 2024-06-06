package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.EphemeralKeys;

@SuppressWarnings("javadoc")
public class JwkSecretVerifierTest extends JwtTestCase {

	@Test
	public void testVerifier() {
		final var key = EphemeralKeys.rand(32);
		final var keyId = IdGenerator.generateId();
		final var jwkId = new JwkSecret(keyId, key);
		final var jwkId2 = new JwkSecret(keyId, key);

		final var verifier = new JwkSecretVerifier(jwkId);
		assertNull(verifier.getAuthenticationEndpoint());
		assertNull(verifier.getAuthScheme());
		assertEquals(keyId, verifier.getRealm());
		assertSame(JwkSecret.class, verifier.getType());
		assertTrue(verifier.isAuthoritative());

		assertDoesNotThrow(() -> verifier.verify(jwkId, verifier.getRealm()));
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(jwkId, IdGenerator.generateId()));
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(jwkId2, verifier.getRealm()));

	}

}
