package iu.auth.bundle;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oidc.IuOpenIdClient;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class OidcIT {

	@SuppressWarnings("unchecked")
	@Test
	public void testClient() throws URISyntaxException, IOException, InterruptedException {
		final var issuer = IdGenerator.generateId();
		final var jwksUri = new URI("test://localhost/" + IdGenerator.generateId());
		
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockHttpClient = mockStatic(HttpClient.class)) {
			final var configUri = new URI("test://localhost/" + IdGenerator.generateId());
			final var rb = mock(HttpRequest.Builder.class);
			final var r = mock(HttpRequest.class);
			final var h = mock(HttpHeaders.class);
			when(r.headers()).thenReturn(h);
			when(r.uri()).thenReturn(configUri);
			when(rb.build()).thenReturn(r);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(configUri)).thenReturn(rb);

			final Map<String, List<String>> map = new LinkedHashMap<>();
			final var rh = mock(HttpHeaders.class);
			final var resp = mock(HttpResponse.class);
			when(rh.map()).thenReturn(map);
			when(resp.headers()).thenReturn(rh);
			when(resp.request()).thenReturn(r);
			when(resp.statusCode()).thenReturn(200);
			when(resp.body()).thenReturn(new ByteArrayInputStream(Json.createObjectBuilder() //
					.add("issuer", issuer) //
					.add("jwks_uri", jwksUri.toString()) //
					.build().toString().getBytes("UTF-8")));

			final var client = mock(HttpClient.class);
			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(client);
			when(client.send(r, BodyHandlers.ofInputStream())).thenReturn(resp);

			final var idClient = mock(IuOpenIdClient.class);

			IuTestLogger.allow("iu.auth.util.HttpUtils", Level.FINE);
			IuTestLogger.allow("iu.auth.oidc.OpenIdProvider", Level.INFO);
			assertNotNull(IuOpenIdProvider.from(configUri, idClient));
		}
	}

}
