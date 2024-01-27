package iu.auth.oidc;

import java.net.URI;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.auth.oidc.IuOpenIdProviderConfiguration;
import iu.auth.util.HttpUtils;
import jakarta.json.JsonObject;

/**
 * {@link IuOpenIdProviderConfiguration} implementation.
 */
public class OpenIdProviderConfiguration implements IuOpenIdProviderConfiguration {

	private final Logger LOG = Logger.getLogger(OpenIdProviderConfiguration.class.getName());

	private final URI uri;
	private JsonObject parsedConfig;

	/**
	 * Constructor.
	 * 
	 * @param uri {@link URI}
	 */
	public OpenIdProviderConfiguration(URI uri) {
		this.uri = uri;
	}

	@Override
	public String getIssuer() {
		return getConfig().getString("issuer");
	}

	@Override
	public URI getAuthorizationEndpoint() {
		return IuException.unchecked(() -> new URI(getConfig().getString("authorization_endpoint")));
	}

	private JsonObject getConfig() {
		if (parsedConfig == null) {
			parsedConfig = HttpUtils.read(uri).asJsonObject();
			LOG.info("OIDC Provider configuration:\n" + parsedConfig.toString());
		}

		return parsedConfig;
	}
}
