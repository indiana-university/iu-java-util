package edu.iu.auth.config;

import iu.auth.config.RemoteAccessTokenBuilder;

/**
 * Base interface for supporting authorization_details claims.
 * 
 * @see {@link RemoteAccessTokenBuilder}
 */
public interface AuthorizationDetails {

	/**
	 * Authorization details type.
	 * 
	 * @return type string
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9396.html#name-authorization-details-types">RFC
	 *      9396 OAuth 2.0 Rich Authorization Requests</a>
	 */
	String getType();

}
