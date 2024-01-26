package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.test.IuTestLogger;
import edu.iu.test.VaultProperties;

@SuppressWarnings("javadoc")
public class OpenIDProviderConfigurationIT {

	@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
	@Test
	public void testVaultConfig() throws URISyntaxException {
		final var oidcConfig = VaultProperties.getProperty("iu.auth.oidc.configUrl");
		try (InputStream in = new URL(oidcConfig).openStream()) {
		} catch (Throwable e) {
			e.printStackTrace();
			Assumptions.abort("Unable to open a connection to " + oidcConfig);
			return;
		}

		IuTestLogger.allow("iu.auth.oidc.OpenIDProviderConfigurationImpl", Level.INFO);
		final var config = new OpenIDProviderConfigurationImpl(new URI(oidcConfig));
		assertNotNull(config.getIssuer());
	}

}
