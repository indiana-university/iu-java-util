package iu.auth.bundle;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuPrincipalSpi;

/**
 * Delegating SPI implementation.
 */
public class PrincipalSpiDelegate implements IuPrincipalSpi {

	private static final IuPrincipalSpi DELEGATE = Bootstrap.load(IuPrincipalSpi.class);

	/**
	 * Default constructor.
	 */
	public PrincipalSpiDelegate() {
	}

	@Override
	public <T extends IuPrincipalIdentity> void verify(T id, String realm) {
		DELEGATE.verify(id, realm);
	}

}
