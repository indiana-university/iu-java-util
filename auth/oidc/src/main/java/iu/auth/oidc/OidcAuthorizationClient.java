package iu.auth.oidc;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import jakarta.json.JsonObject;

/**
 * OpenID Connect {@link IuAuthorizationClient} implementation.
 */
class OidcAuthorizationClient implements IuAuthorizationClient {

	private final String realm;
	private final URI authorizationEndpoint;
	private final URI tokenEndpoint;
	private final URI resourceUri;
	private final IuApiCredentials credentials;

	private final Set<String> nonces = new HashSet<>();

	/**
	 * Constructor.
	 * 
	 * @param config                parsed OIDC provider well-known configuration
	 * @param authenticationTimeout Max length of time to allow between initiating
	 *                              authentication (e.g., redirect to OIDC
	 *                              authorization endpoint) and completing
	 *                              authentication (i.e., ID token issued at "iat"
	 *                              claim value).
	 * @param resourceUri           client resource URI
	 * @param credentials           client credentials
	 */
	OidcAuthorizationClient(JsonObject config, URI resourceUri, IuApiCredentials credentials) {
		realm = config.getString("issuer");
		authorizationEndpoint = IuException.unchecked(() -> new URI(config.getString("authorization_endpoint")));
		tokenEndpoint = IuException.unchecked(() -> new URI(config.getString("token_endpoint")));
		this.resourceUri = resourceUri;
		this.credentials = credentials;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public URI getAuthorizationEndpoint() {
		return authorizationEndpoint;
	}

	@Override
	public URI getTokenEndpoint() {
		return tokenEndpoint;
	}

	@Override
	public URI getRedirectUri() {
		return resourceUri;
	}

	@Override
	public Map<String, String> getAuthorizationCodeAttributes() {
		final var nonce = IdGenerator.generateId();
		synchronized (nonces) {
			nonces.add(nonce);
		}
		return Map.of("nonce", nonce);
	}

	@Override
	public Map<String, String> getClientCredentialsAttributes() {
		final var resourceUri = this.resourceUri.toString();
		return Map.of("resource", resourceUri, "audience", resourceUri);
	}

	@Override
	public IuApiCredentials getCredentials() {
		return credentials;
	}

}
