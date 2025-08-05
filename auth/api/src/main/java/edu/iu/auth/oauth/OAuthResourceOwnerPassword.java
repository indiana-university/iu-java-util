package edu.iu.auth.oauth;

/**
 * Encapsulates OAuth 2.0 Resource Owner Password grant authentication details.
 */
public interface OAuthResourceOwnerPassword extends OAuthClient, OAuthClientSecret {

	/**
	 * Gets the username.
	 * 
	 * @return username
	 */
	String getUsername();

	/**
	 * Gets the password.
	 * 
	 * @return password
	 */
	String getPassword();

}
