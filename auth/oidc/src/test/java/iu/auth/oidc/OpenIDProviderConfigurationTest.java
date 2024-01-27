package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.URI;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import iu.auth.util.HttpUtils;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class OpenIDProviderConfigurationTest {

	@Test
	public void testIssuer() {
		IuTestLogger.allow("iu.auth.oidc.OpenIDProviderConfigurationImpl", Level.INFO);
		final var uri = mock(URI.class);
		final var iss = IdGenerator.generateId();
		final var config = new OpenIdProviderConfiguration(uri);
		try (final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			mockHttpUtils.when(() -> HttpUtils.read(uri))
					.thenReturn(Json.createObjectBuilder().add("issuer", iss).build());
			assertEquals(iss, config.getIssuer());
			assertEquals(iss, config.getIssuer());
			mockHttpUtils.verify(() -> HttpUtils.read(uri)); // once
		}
	}

}
