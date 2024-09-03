package iu.auth.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJsonAdapter;
import iu.auth.config.AuthConfig;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class OpenIdProviderMetadataCacheTest extends IuAuthClientTestCase {

	@Test
	public void testGetByUri() {
		final var uri = URI.create(IdGenerator.generateId());
		
		final var metadata = mock(IuOpenIdProviderMetadata.class);
		final var metadataJson = mock(JsonValue.class);
		final var metadataAdapter = mock(IuJsonAdapter.class);
		when(metadataAdapter.fromJson(metadataJson)).thenReturn(metadata);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class); //
				final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.get(uri, IuHttp.READ_JSON)).thenReturn(metadataJson);
			mockAuthConfig.when(() -> AuthConfig.adaptJson(IuOpenIdProviderMetadata.class)).thenReturn(metadataAdapter);
			
			assertSame(metadata, OpenIdProviderMetadataCache.get(uri));
			mockHttp.verify(() -> IuHttp.get(uri, IuHttp.READ_JSON));
			
			assertSame(metadata, OpenIdProviderMetadataCache.get(uri));
			mockHttp.verifyNoMoreInteractions();
		}
	}

}
