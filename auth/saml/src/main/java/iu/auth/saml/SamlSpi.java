package iu.auth.saml;

import java.net.URI;
import java.util.function.Supplier;

import edu.iu.IuObject;
import edu.iu.auth.saml.IuSamlSession;
import edu.iu.auth.spi.IuSamlSpi;

/**
 * {@link IuSamlSpi} implementation.
 */
public class SamlSpi implements IuSamlSpi {
	static {
		IuObject.assertNotOpen(SamlSpi.class);
	}

	/**
	 * Default constructor.
	 */
	public SamlSpi() {
	}

	@Override
	public IuSamlSession createSession(URI postUri, Supplier<byte[]> secretKey) {
		return new SamlSession(postUri, secretKey);
	}

	@Override
	public IuSamlSession activateSession(String sessionToken, Supplier<byte[]> secretKey) {
		return new SamlSession(sessionToken, secretKey);
	}

}
