package iu.auth.saml;

import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import edu.iu.auth.spi.IuSamlSpi;

/**
 *  SAML connect SPI implementation
 */
public class SamlConnectSpi implements IuSamlSpi {

	/**
	 * Default constructor.
	 */
	public SamlConnectSpi() {
	}

	@Override
	public IuSamlProvider getSamlProvider(IuSamlClient client) {
		return new SamlProvider(client);
	}

}
