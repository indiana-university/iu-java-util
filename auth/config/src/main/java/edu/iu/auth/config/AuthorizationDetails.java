package edu.iu.auth.config;

/**
 * Base interface for supporting authorization_details claims.
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
