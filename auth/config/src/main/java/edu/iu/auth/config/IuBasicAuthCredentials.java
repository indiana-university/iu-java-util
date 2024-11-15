package edu.iu.auth.config;

import java.net.http.HttpRequest.Builder;

import edu.iu.IuText;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;

/**
 * Provides client-side support for the use of HTTP Basic authentication as API
 * credentials.
 */
public interface IuBasicAuthCredentials extends IuApiCredentials {

	/**
	 * Returns the username to be used for HTTP Basic authentication.
	 *
	 * @return the username
	 */
	String getUsername();

	/**
	 * Returns the password to be used for HTTP Basic authentication.
	 *
	 * @return the password
	 */
	String getPassword();

	@Override
	default void applyTo(Builder httpRequestBuilder) throws IuAuthenticationException {
		httpRequestBuilder.header("Authorization",
				"Basic " + IuText.base64(IuText.utf8(getUsername() + ":" + getPassword())));
	}

}
