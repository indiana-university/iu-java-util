package edu.iu.auth;

import java.net.http.HttpRequest;
import java.security.Principal;

import edu.iu.auth.basic.IuBasicAuthCredentials;
import edu.iu.auth.spi.IuBasicAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Parent interface for representing a client's API credentials.
 */
public interface IuApiCredentials extends Principal {

	/**
	 * Gets credentials for use with
	 * <a href="https://datatracker.ietf.org/doc/html/rfc7617">HTTP Basic
	 * Authentication</a>.
	 * 
	 * @param username username
	 * @param password password
	 * @return credentials for use with HTTP basic auth
	 */
	static IuBasicAuthCredentials basic(String username, String password) {
		return IuAuthSpiFactory.get(IuBasicAuthSpi.class).createCredentials(username, password);
	}

	/**
	 * Applies the client's API credentials to an HTTP request.
	 * 
	 * @param httpRequestBuilder {@link HttpRequest.Builder}
	 */
	void applyTo(HttpRequest.Builder httpRequestBuilder);

}
