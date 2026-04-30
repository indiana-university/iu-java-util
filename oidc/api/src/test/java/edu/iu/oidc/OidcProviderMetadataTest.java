package edu.iu.oidc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class OidcProviderMetadataTest {

	@Test
	void testDefaults() {
		final var metadata = mock(IuOidcProviderMetadata.class, CALLS_REAL_METHODS);
		assertTrue(metadata.isRequestUriParameterSupported());
	}

}
