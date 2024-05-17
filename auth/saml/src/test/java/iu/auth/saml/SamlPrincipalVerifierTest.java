package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;

@SuppressWarnings("javadoc")
public class SamlPrincipalVerifierTest {

	@Test
	public void testVerify() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var verifier = new SamlPrincipalVerifier(true, realm);
		assertTrue(verifier.isAuthoritative());
		assertEquals(realm, verifier.getRealm());
		assertEquals(SamlPrincipal.class, verifier.getType());

		final var id = mock(SamlPrincipal.class);
		when(id.realm()).thenReturn(realm);
		verifier.verify(id, realm);
	}

	@Test
	public void testWrongRealm() {
		final var realm = IdGenerator.generateId();
		final var verifier = new SamlPrincipalVerifier(true, realm);
		assertTrue(verifier.isAuthoritative());
		assertEquals(realm, verifier.getRealm());
		assertEquals(SamlPrincipal.class, verifier.getType());

		final var id = mock(SamlPrincipal.class);
		when(id.realm()).thenReturn(IdGenerator.generateId());
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(id, realm));
	}
}
