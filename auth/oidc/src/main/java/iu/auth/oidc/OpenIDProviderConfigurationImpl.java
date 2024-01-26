package iu.auth.oidc;

import java.net.URI;
import java.util.logging.Logger;

import edu.iu.auth.oidc.OpenIDProviderConfiguration;
import iu.auth.oauth.HttpUtils;
import jakarta.json.JsonObject;

/**
 * {@link OpenIDProviderConfiguration} implementation.
 */
public class OpenIDProviderConfigurationImpl implements OpenIDProviderConfiguration {

	private final Logger LOG = Logger.getLogger(OpenIDProviderConfigurationImpl.class.getName());

	private final URI uri;
	private JsonObject parsedConfig;

	/**
	 * Constructor.
	 * 
	 * @param uri {@link URI}
	 */
	public OpenIDProviderConfigurationImpl(URI uri) {
		this.uri = uri;
	}

	@Override
	public String getIssuer() {
		return getConfig().getString("issuer");
	}

	private JsonObject getConfig() {
		if (parsedConfig == null) {
			parsedConfig = HttpUtils.read(uri).asJsonObject();
			LOG.info("OIDC Provider configuration:\n" + parsedConfig.toString());
		}

		return parsedConfig;
	}
}
