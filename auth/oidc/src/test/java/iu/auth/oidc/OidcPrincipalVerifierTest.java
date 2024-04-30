package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;

@SuppressWarnings("javadoc")
public class OidcPrincipalVerifierTest extends IuOidcTestCase {

	@Test
	public void testVerify() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var verifier = new OidcPrincipalVerifier(true, realm);
		assertTrue(verifier.isAuthoritative());
		assertEquals(realm, verifier.getRealm());
		assertEquals(OidcPrincipal.class, verifier.getType());

		final var id = mock(OidcPrincipal.class);
		when(id.realm()).thenReturn(realm);
		verifier.verify(id, realm);
	}

	@Test
	public void testWrongRealm() {
		final var realm = IdGenerator.generateId();
		final var verifier = new OidcPrincipalVerifier(true, realm);
		assertTrue(verifier.isAuthoritative());
		assertEquals(realm, verifier.getRealm());
		assertEquals(OidcPrincipal.class, verifier.getType());

		final var id = mock(OidcPrincipal.class);
		when(id.realm()).thenReturn(IdGenerator.generateId());
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(id, realm));
	}

//	@Test
//	public void testRevoked() throws IuAuthenticationException {
//		final var realm = IdGenerator.generateId();
//		final var verifier = new OidcPrincipalVerifier(true, realm);
//		assertTrue(verifier.isAuthoritative());
//		assertEquals(realm, verifier.getRealm());
//		assertEquals(OidcPrincipal.class, verifier.getType());
//
//		final var id = mock(OidcPrincipal.class);
//		when(id.realm()).thenReturn(realm);
//		when(id.revoked()).thenReturn(true);
//		assertThrows(IllegalStateException.class, () -> verifier.verify(id, realm));
//	}

}
