package iu.auth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class OAuthProviderMetadataTest {

	@Test
	public void testProxy() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var jwksUri = URI.create(IdGenerator.generateId());

		final var metadata = OAuthProviderMetadata.getInstance(issuer, tokenEndpoint, jwksUri);
		assertEquals(issuer, metadata.getIssuer());
		assertEquals(tokenEndpoint, metadata.getTokenEndpoint());
		assertEquals(jwksUri, metadata.getJwksUri());
		assertFalse(metadata.isRequestUriParameterSupported());
		assertNull(metadata.getIdTokenEncryptionAlgValuesSupported());

		assertEquals(metadata, metadata);
		assertNotEquals(metadata, OAuthProviderMetadata.getInstance(issuer, tokenEndpoint, jwksUri));
		assertNotEquals(metadata.hashCode(),
				OAuthProviderMetadata.getInstance(issuer, tokenEndpoint, jwksUri).hashCode());

		assertEquals("OAuthProviderMetadata [issuer=" + issuer + ", tokenEndpoint=" + tokenEndpoint + ", jwksUri="
				+ jwksUri + "]", metadata.toString());
	}

}
