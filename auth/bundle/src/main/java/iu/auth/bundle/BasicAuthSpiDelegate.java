package iu.auth.bundle;

import edu.iu.auth.basic.IuBasicAuthCredentials;
import edu.iu.auth.spi.IuBasicAuthSpi;

/**
 * Delegates to a bootstrapped implementation module.
 */
public class BasicAuthSpiDelegate implements IuBasicAuthSpi {

	private static final IuBasicAuthSpi DELEGATE = Bootstrap.load(IuBasicAuthSpi.class);

	/**
	 * Default constructor.
	 */
	public BasicAuthSpiDelegate() {
	}

	@Override
	public IuBasicAuthCredentials createCredentials(String username, String password) {
		return DELEGATE.createCredentials(username, password);
	}

}
