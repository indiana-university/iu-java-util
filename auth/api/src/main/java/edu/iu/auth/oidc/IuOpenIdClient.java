package edu.iu.auth.oidc;

import java.net.URI;

import edu.iu.auth.IuApiCredentials;

/**
 * Provides client configuration metadata for interacting with an
 * {@link IuOpenIdProvider OpenID Provider}.
 */
@Deprecated(forRemoval = true)
public interface IuOpenIdClient {

	/**
	 * Gets the well known configuration URL for the OpenID Provider.
	 * 
	 * @return {@link URI}
	 */
	URI getConfigUri();

	/**
	 * Gets the client's API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getApiCredentials();

}
