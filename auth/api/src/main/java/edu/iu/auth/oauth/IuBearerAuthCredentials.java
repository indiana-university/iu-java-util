package edu.iu.auth.oauth;

import edu.iu.auth.IuApiCredentials;

/**
 * Represents credentials for use with
 * <a href="https://datatracker.ietf.org/doc/html/rfc6750">OAuth 2.0 Bearer
 * Token Authorization</a>.
 */
public interface IuBearerAuthCredentials extends IuApiCredentials {

	/**
	 * Gets the access token.
	 * 
	 * @return access token
	 */
	String getAccessToken();

}
