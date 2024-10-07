package edu.iu.auth.client;

import java.net.URI;

/**
 * Encapsulates attributes commonly submitted with OAuth 2.0 authorization
 * requests.
 */
public interface IuAuthorizationRequest {

	/**
	 * Gets the URI.
	 * 
	 * @return {@link URI}
	 */
	URI getResourceUri();

	/**
	 * Gets the requested OAuth 2.0 scopes.
	 * 
	 * @return scope
	 */
	Iterable<String> getScope();

}
