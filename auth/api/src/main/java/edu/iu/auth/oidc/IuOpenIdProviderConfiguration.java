package edu.iu.auth.oidc;

import java.net.URI;

/**
 * Provides access to OpenID Provider Configuration.
 */
public interface IuOpenIdProviderConfiguration {

	/**
	 * Gets the issuer ID.
	 * 
	 * @return Issuer ID
	 */
	String getIssuer();

	/**
	 * Gets the authorization endpoint.
	 * 
	 * @return authorization endpoint
	 */
	URI getAuthorizationEndpoint();

}
