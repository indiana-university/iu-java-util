package edu.iu.auth.oauth;

import java.net.URI;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.spi.IuOAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Provides client configuration metadata for interacting with an authorization
 * server.
 */
public interface IuAuthorizationClient {

	/**
	 * Initializes client metadata for interacting with an OAuth authorization
	 * server.
	 * 
	 * <p>
	 * This method <em>must</em> be invoked exactly once per authorization realm,
	 * typically at resource container initialization time. Once the realm has been
	 * initialized, it <em>cannot</em> be modified. If client metadata needs to
	 * change, the authorization module <em>must</em> be reinitialized in order for
	 * the change can take effect.
	 * </p>
	 * 
	 * @param realm  authorization realm
	 * @param client client metadata
	 */
	static void initialize(String realm, IuAuthorizationClient client) {
		IuAuthSpiFactory.get(IuOAuthSpi.class).initialize(realm, client);
	}

	/**
	 * Gets the endpoint {@link URI} for the authorization server.
	 * 
	 * @return Authorization endpoint {@link URI}
	 */
	URI getAuthorizationEndpoint();

	/**
	 * Gets the endpoint {@link URI} for the token server.
	 * 
	 * @return Token endpoint {@link URI}
	 */
	URI getTokenEndpoint();

	/**
	 * Gets the redirect URI.
	 * 
	 * @return redirect URI
	 */
	URI getRedirectUri();

	/**
	 * Gets the client's API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getCredentials();

}
