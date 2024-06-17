package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.iu.auth.saml.IuSamlAssertion;

@SuppressWarnings("javadoc")
public class IuSamlServiceProviderMetadataTest {

	@Test
	public void testSamlClientDefault() {
		final var client = mock(IuSamlServiceProviderMetadata.class, CALLS_REAL_METHODS);
		assertEquals(client.getMetadataTtl(), Duration.ofMinutes(5L));
		assertEquals(client.getAuthenticatedSessionTimeout(), Duration.ofHours(12L));
		assertFalse(client.isFailOnAddressMismatch());
		assertFalse(client.getAllowedRange().iterator().hasNext());
		assertEquals(IuSamlAssertion.EDU_PERSON_PRINCIPAL_NAME_OID, client.getPrincipalNameAttribute());
	}
}
