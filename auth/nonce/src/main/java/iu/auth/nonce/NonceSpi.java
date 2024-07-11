package iu.auth.nonce;

import edu.iu.IuObject;
import edu.iu.auth.IuOneTimeNumber;
import edu.iu.auth.IuOneTimeNumberConfig;
import edu.iu.auth.spi.IuNonceSpi;

/**
 * {@link IuNonceSpi} implementation.
 */
public class NonceSpi implements IuNonceSpi {
	static {
		IuObject.assertNotOpen(NonceSpi.class);
	}

	/**
	 * Default constructor.
	 */
	public NonceSpi() {
	}

	@Override
	public IuOneTimeNumber initialize(IuOneTimeNumberConfig config) {
		return new OneTimeNumber(config);
	}

}
