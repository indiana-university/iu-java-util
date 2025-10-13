package edu.iu.auth.oauth;

import java.net.URI;

/**
 * Encapsulates OAuth 2.0 authorization client details.
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749.html">RFC-6749 OAuth 2.0</a>
 */
public interface OAuthAuthorizationClient extends OAuthClient {

	/**
	 * Gets the authorization endpoint URI.
	 * 
	 * @return OAuth 2.0 authorization endpoint URI
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
