package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.test.IuTestLogger;
import edu.iu.test.VaultProperties;

@SuppressWarnings("javadoc")
public class OpenIDProviderConfigurationIT {

	@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
	@Test
	public void testVaultConfig() throws URISyntaxException {
		IuTestLogger.allow("iu.auth.oauth.HttpUtils", Level.FINE);
		IuTestLogger.allow("iu.auth.oidc.OpenIDProviderConfigurationImpl", Level.INFO);
		final var oidcConfig = VaultProperties.getProperty("iu.auth.oidc.configUrl");
		final var config = new OpenIdProviderConfiguration(new URI(oidcConfig));
		assertNotNull(config.getIssuer());
	}

}
