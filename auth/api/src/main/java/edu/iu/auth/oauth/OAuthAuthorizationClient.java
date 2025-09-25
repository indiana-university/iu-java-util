package edu.iu.auth.oauth;

import java.net.URI;

/**
 * Encapsulates OAuth 2.0 authorization client details.
 */
public interface OAuthAuthorizationClient extends OAuthClient {

	/**
	 * Gets the token URI.
	 * 
	 * @return OAuth 2.0 Token URI
	 */
	URI getAuthorizeUri();

	/**
	 * Gets the redirect_uri attribute for returning the use to the local client
	 * application.
	 * 
	 * @return {@link URI}
	 */
	URI getRedirectUri();

}
