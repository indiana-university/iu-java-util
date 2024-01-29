package iu.auth.oidc;

import java.net.URI;

import edu.iu.IuException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import jakarta.json.JsonObject;

/**
 * OpenID Connect {@link IuAuthorizationClient} implementation.
 */
class OidcAuthorizationClient implements IuAuthorizationClient {

	private final JsonObject config;
	private final URI resourceUri;
	private final IuApiCredentials credentials;

	/**
	 * Constructor.
	 * 
	 * @param config      parsed OIDC provider well-known configuration
	 * @param resourceUri client resource URI
	 * @param credentials client credentials
	 */
	OidcAuthorizationClient(JsonObject config, URI resourceUri, IuApiCredentials credentials) {
		this.config = config;
		this.resourceUri = resourceUri;
		this.credentials = credentials;
	}

	@Override
	public URI getAuthorizationEndpoint() {
		return IuException.unchecked(() -> new URI(config.getString("authorization_endpoint")));
	}

	@Override
	public URI getTokenEndpoint() {
		return IuException.unchecked(() -> new URI(config.getString("token_endpoint")));
	}

	@Override
	public URI getRedirectUri() {
		return resourceUri;
	}

	@Override
	public IuApiCredentials getCredentials() {
		return credentials;
	}

}
