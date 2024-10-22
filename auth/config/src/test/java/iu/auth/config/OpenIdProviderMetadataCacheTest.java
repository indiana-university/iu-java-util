package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class OpenIdProviderMetadataCacheTest extends AuthConfigTestCase {

	@Test
	public void testRead() {
		final var uri = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOpenIdProviderMetadata.class);
		final var json = mock(JsonObject.class);
		try (final var mockIuHttp = mockStatic(IuHttp.class); //
				final var mockIuJson = mockStatic(IuJson.class)) {
			mockIuHttp.when(() -> IuHttp.get(uri, IuHttp.READ_JSON_OBJECT)).thenReturn(json);
			mockIuJson.when(() -> IuJson.wrap(json, IuOpenIdProviderMetadata.class)).thenReturn(metadata);
			assertSame(metadata, OpenIdProviderMetadataCache.read(uri));
		}
	}

	@Test
	public void testCache() {
		final var uri = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOpenIdProviderMetadata.class);
		final var json = mock(JsonObject.class);
		try (final var mockIuHttp = mockStatic(IuHttp.class); //
				final var mockIuJson = mockStatic(IuJson.class)) {
			mockIuHttp.when(() -> IuHttp.get(uri, IuHttp.READ_JSON_OBJECT)).thenReturn(json);
			mockIuJson.when(() -> IuJson.wrap(json, IuOpenIdProviderMetadata.class)).thenReturn(metadata);
			assertSame(metadata, OpenIdProviderMetadataCache.get(uri));
			mockIuHttp.verify(() -> IuHttp.get(uri, IuHttp.READ_JSON_OBJECT));
			
			assertSame(metadata, OpenIdProviderMetadataCache.get(uri));
			mockIuHttp.verifyNoMoreInteractions();
		}
	}

}
