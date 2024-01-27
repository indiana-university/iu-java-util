package edu.iu.auth.spi;

import edu.iu.auth.basic.IuBasicAuthCredentials;

/**
 * Service provider interface for HTTP basic auth.
 */
public interface IuBasicAuthSpi {

	/**
	 * Creates basic auth credentials.
	 * 
	 * @param username username
	 * @param password password
	 * @return {@link IuBasicAuthCredentials}
	 */
	IuBasicAuthCredentials createCredentials(String username, String password);

}
