package edu.iu.auth.oauth;

import java.net.URI;
import java.util.Map;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.spi.IuOAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Provides client configuration metadata for interacting with an authorization
 * server.
 * 
 * <p>
 * The interface <em>should</em> be implemented by the application client module
 * requiring authorization on behalf of an upstream authentication provider.
 * </p>
 */
public interface IuAuthorizationClient {

	/**
	 * Initializes client metadata for interacting with an OAuth authorization
	 * server.
	 * 
	 * <p>
	 * This method <em>must</em> be invoked exactly once per authentication realm,
	 * typically at resource container initialization time. Once the realm has been
	 * initialized, it <em>cannot</em> be modified. If client metadata needs to
	 * change, the authorization module <em>must</em> be reinitialized in order for
	 * the change can take effect.
	 * </p>
	 * 
	 * @param client client metadata
	 * @return client credentials grant for authorizing direct use of a downstream
	 *         service by the client
	 */
	static IuAuthorizationGrant initialize(IuAuthorizationClient client) {
		return IuAuthSpiFactory.get(IuOAuthSpi.class).initialize(client);
	}

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	String getRealm();

	/**
	 * Gets the client's API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getCredentials();

	/**
	 * Gets the endpoint {@link URI} for the token server.
	 * 
	 * @return Token endpoint {@link URI}
	 */
	URI getTokenEndpoint();

	/**
	 * Gets the endpoint {@link URI} for the authorization server.
	 * 
	 * @return Authorization endpoint {@link URI}
	 */
	default URI getAuthorizationEndpoint() {
		return null;
	}

	/**
	 * Gets the redirect URI for use with authorization code flow.
	 * 
	 * @return redirect URI; null if authorization code is not supported for the
	 *         authentication realm.
	 */
	default URI getRedirectUri() {
		return null;
	}

	/**
	 * Get additional attributes to pass to the authorization endpoint when
	 * initiating authorization code flow.
	 * 
	 * <p>
	 * It is <em>not required</em> for repeat invocation to return the same
	 * attributes. An authorization session will invoke this method once when
	 * initiating authorization code flow, and retain those attributes throughout
	 * the flow. To refer to those attributes for post-authorization credentials
	 * verification, use {@link IuAuthorizationResponse#getRequestAttributes()}.
	 * </p>
	 * 
	 * @return {@link Map} of additional authorization code attributes; <em>may</em>
	 *         be null or empty if authorization code flow is not supported or
	 *         additional attributes are not required for token verification.
	 */
	default Map<String, String> getAuthorizationCodeAttributes() {
		return null;
	}

	/**
	 * Get additional attributes to pass to the token endpoint when initiating
	 * client credentials code flow.
	 * 
	 * @return {@link Map} of additional client credentials attributes; <em>may</em>
	 *         be null or empty if not required for establishing client credentials.
	 */
	default Map<String, String> getClientCredentialsAttributes() {
		return null;
	}

	/**
	 * Revokes credentials established for use with the client's authentication
	 * realm, if supported.
	 * 
	 * @param credentials credentials to revoke
	 */
	default void revoke(IuApiCredentials credentials) {
	}

}
