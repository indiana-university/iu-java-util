package iu.auth.oidc;

import java.net.URI;
import java.time.Duration;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import edu.iu.auth.oidc.IuOpenIdAuthenticationAttributes;
import edu.iu.auth.oidc.IuOpenIdProvider;
import iu.auth.util.AccessTokenVerifier;
import iu.auth.util.HttpUtils;
import jakarta.json.JsonObject;

/**
 * {@link IuOpenIdProvider} implementation.
 */
public class OpenIdProvider implements IuOpenIdProvider {

	private final Logger LOG = Logger.getLogger(OpenIdProvider.class.getName());

	private final String issuer;
	private final Duration authenticationTimeout;
	private final AccessTokenVerifier accessTokenVerifier;
	private JsonObject config;

	/**
	 * Constructor.
	 * 
	 * @param configUri             Well-known configuration URI
	 * @param trustRefreshInterval  Maximum length of time to keep trusted signing
	 *                              keys in cache
	 * @param authenticationTimeout Maximum length of time to allow for user
	 *                              authentication.
	 */
	public OpenIdProvider(URI configUri, Duration trustRefreshInterval, Duration authenticationTimeout) {
		config = HttpUtils.read(configUri).asJsonObject();
		LOG.info("OIDC Provider configuration:\n" + config.toString());

		this.issuer = config.getString("issuer");

		this.accessTokenVerifier = new AccessTokenVerifier(
				IuException.unchecked(() -> new URI(config.getString("jwks_uri"))), issuer, trustRefreshInterval);

		this.authenticationTimeout = authenticationTimeout;
	}

	@Override
	public IuAuthorizationClient createAuthorizationClient(URI resourceUri, IuApiCredentials clientCredentials) {
		return new OidcAuthorizationClient(config, resourceUri, clientCredentials);
	}

	@Override
	public String getIssuer() {
		return issuer;
	}

	@Override
	public URI getUserInfoEndpoint() {
		return IuException.unchecked(() -> new URI(config.getString("userinfo_endpoint")));
	}

	@Override
	public IuOpenIdAuthenticationAttributes verifyAuthentication(IuAuthorizationResponse authResponse) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
