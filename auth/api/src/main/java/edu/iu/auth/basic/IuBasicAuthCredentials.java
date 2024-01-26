package edu.iu.auth.basic;

import edu.iu.auth.IuApiCredentials;

/**
 * Represents credentials for use with
 * <a href="https://datatracker.ietf.org/doc/html/rfc7617">HTTP Basic
 * Authentication</a>.
 */
public interface IuBasicAuthCredentials extends IuApiCredentials {

	/**
	 * Gets the password.
	 * 
	 * @return password
	 */
	String getPassword();

}
