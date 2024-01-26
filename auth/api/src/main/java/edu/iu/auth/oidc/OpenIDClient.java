package edu.iu.auth.oidc;

import java.net.URI;

import edu.iu.auth.IuApiCredentials;

/**
 * Provides client configuration metadata for interacting with an
 * {@link OpenIDProvider OpenID Provider}.
 */
public interface OpenIDClient {

	/**
	 * Gets the well known configuration URL for the OpenID Provider.
	 * 
	 * @return {@link URI}
	 */
	URI getConfigurationUrl();

	/**
	 * Gets the client's API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getApiCredentials();

}
