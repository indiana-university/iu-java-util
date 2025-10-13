package edu.iu.auth.oauth;

import java.net.URI;

/**
 * Encapsulates OAuth 2.0 client identification.
 */
public interface OAuthClient {

	/**
	 * Gets the token URI.
	 * 
	 * @return OAuth 2.0 Token URI
	 */
	URI getTokenUri();

	/**
	 * Gets the JWKS URI.
	 * 
	 * @return JWKS URI for verifying JWT token signature
	 */
	URI getJwksUri();

	/**
	 * Gets the client ID.
	 * 
	 * @return Client ID
	 */
	String getClientId();

}
