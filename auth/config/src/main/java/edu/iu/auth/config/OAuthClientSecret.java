package edu.iu.auth.config;

/**
 * Encapsulates OAuth 2.0 confidential client secret.
 */
public interface OAuthClientSecret {

	/**
	 * Gets the client secret.
	 * 
	 * @return client secret
	 */
	String getClientSecret();

}
