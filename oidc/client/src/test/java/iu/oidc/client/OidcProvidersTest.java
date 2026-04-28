package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.test.IuTestLogger;
import iu.oidc.client.config.IuOidcProvider;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OidcProvidersTest {

	@Test
	void testDirect() {
		final var metadata = mock(IuOidcProviderMetadata.class);
		final var provider = mock(IuOidcProvider.class);
		when(provider.getMetadata()).thenReturn(metadata);
		assertEquals(metadata, OidcProviders.getMetadata(provider));
	}

	@Test
	void testReadsFromUri() {
		final var issuer = URI.create(IdGenerator.generateId());
		final var metadataUri = URI.create(IdGenerator.generateId());
		final var metadataTtl = Duration.ofSeconds(1L);
		final var provider = mock(IuOidcProvider.class);
		when(provider.getIssuer()).thenReturn(issuer);
		when(provider.getMetadataUri()).thenReturn(metadataUri);
		when(provider.getMetadataTtl()).thenReturn(metadataTtl);

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT))
				.thenThrow(IllegalStateException.class);
		assertThrows(IllegalStateException.class, () -> OidcProviders.getMetadata(provider));

		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT)) //
				.thenReturn(IuJson.object() //
						.add("issuer", issuer.toString()) //
						.build());
		assertEquals(issuer, OidcProviders.getMetadata(provider).getIssuer());
		assertEquals(issuer, OidcProviders.getMetadata(provider).getIssuer());

		assertDoesNotThrow(() -> Thread.sleep(1000L));
		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, IuHttp.READ_JSON_OBJECT))
				.thenThrow(IllegalStateException.class);

		IuTestLogger.expect(OidcProviders.class.getName(), Level.INFO,
				"OIDC provider metadata lookup failure " + metadataUri + "; using last good version",
				IllegalStateException.class);
		assertEquals(issuer, OidcProviders.getMetadata(provider).getIssuer());
	}

}
