package edu.iu.auth.config;

import java.net.URI;

/**
 * Authentication and authorization configuration management interface.
 * 
 * <p>
 * Describes the properties of an authentication realm.
 * </p>
 */
public interface IuAuthConfig {

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	String getRealm();

	/**
	 * Gets the authorization scheme.
	 * 
	 * @return authorization scheme; null if the realm doesn't define authorization
	 *         logic
	 */
	String getAuthScheme();

	/**
	 * Gets the authentication endpoint.
	 * 
	 * <p>
	 * This endpoint is responsible with authentication server interactions for an
	 * application module. This endpoint sets an authenticated session cookie and
	 * redirects the user to an application-specific entry point.
	 * </p>
	 * 
	 * @return authentication endpoint
	 */
	URI getAuthenticationEndpoint();

}
