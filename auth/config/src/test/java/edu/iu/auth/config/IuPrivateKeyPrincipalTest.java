package edu.iu.auth.config;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuPrivateKeyPrincipalTest {

	@Test
	public void testDefaults() {
		final var pkp = mock(IuPrivateKeyPrincipal.class, CALLS_REAL_METHODS);
		pkp.getEncryptAlg();
		verify(pkp).getAlg();
		pkp.getEncryptJwk();
		verify(pkp).getJwk();
	}
}
