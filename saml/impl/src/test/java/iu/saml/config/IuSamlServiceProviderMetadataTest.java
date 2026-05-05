package iu.saml.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.iu.saml.IuSamlAssertion;

@SuppressWarnings("javadoc")
public class IuSamlServiceProviderMetadataTest {

	@Test
	void testDefaults() {
		final var config = mock(IuSamlServiceProviderMetadata.class, CALLS_REAL_METHODS);
		assertFalse(config.isFailOnAddressMismatch());
		assertEquals(Duration.ofHours(12L), config.getAuthenticatedSessionTimeout());
		assertEquals(Duration.ofMinutes(5L), config.getMetadataTtl());
		assertFalse(config.getAllowedRange().iterator().hasNext());
		assertEquals(IuSamlAssertion.EDU_PERSON_PRINCIPAL_NAME_OID, config.getPrincipalNameAttribute());
	}

}
