package iu.auth.oidc;

import java.net.URI;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oidc.IuOpenIdProvider;
import iu.auth.util.HttpUtils;
import jakarta.json.JsonObject;

/**
 * {@link IuOpenIdProvider} implementation.
 */
public class OpenIdProvider implements IuOpenIdProvider {

	private final Logger LOG = Logger.getLogger(OpenIdProvider.class.getName());

	private final URI configUri;
	private JsonObject config;

	/**
	 * Constructor.
	 * 
	 * @param configUri Well-known configuration URI
	 */
	public OpenIdProvider(URI configUri) {
		this.configUri = configUri;
	}

	@Override
	public IuAuthorizationClient createAuthorizationClient(URI resourceUri, IuApiCredentials clientCredentials) {
		return new OidcAuthorizationClient(getConfig(), resourceUri, clientCredentials);
	}

	@Override
	public String getIssuer() {
		return config.getString("issuer");
	}

	@Override
	public URI getUserInfoEndpoint() {
		return IuException.unchecked(() -> new URI(config.getString("userinfo_endpoint")));
	}

	private JsonObject getConfig() {
		if (config == null) {
			config = HttpUtils.read(configUri).asJsonObject();
			LOG.info("OIDC Provider configuration:\n" + config.toString());
		}

		return config;
	}

}
