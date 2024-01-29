package iu.auth.basic;

import edu.iu.auth.basic.IuBasicAuthCredentials;
import edu.iu.auth.spi.IuBasicAuthSpi;

/**
 * {@link IuBasicAuthSpi} service provider implementation.
 */
public class BasicAuthSpi implements IuBasicAuthSpi {

	/**
	 * Default constructor.
	 */
	public BasicAuthSpi() {
	}

	@Override
	public IuBasicAuthCredentials createCredentials(String username, String password) {
		return new BasicAuthCredentials(username, password);
	}

}
