package iu.oidc.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.oidc.IuOidcProviderMetadata;

@SuppressWarnings("javadoc")
public class IuOidcProviderTest {

	@Test
	void testHardDefaults() {
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(provider.getMetadata()).thenReturn(metadata);
		assertNull(provider.getMetadataUri());
		assertNull(provider.getMetadataTtl());
	}

	@Test
	void testUriDefaults() {
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		final var issuer = URI.create(IdGenerator.generateId());
		when(provider.getIssuer()).thenReturn(issuer);
		assertEquals(URI.create(issuer + "/.well-known/openid-configuration"), provider.getMetadataUri());
		assertEquals(Duration.ofMinutes(15L), provider.getMetadataTtl());
	}

}
