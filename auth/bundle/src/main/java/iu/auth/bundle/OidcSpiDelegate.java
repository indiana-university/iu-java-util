package iu.auth.bundle;

import java.net.URI;

import edu.iu.auth.oidc.IuOpenIdClient;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.auth.spi.IuOpenIdConnectSpi;

/**
 * Delegating SPI implementation.
 */
public class OidcSpiDelegate implements IuOpenIdConnectSpi {

	private static final IuOpenIdConnectSpi DELEGATE = Bootstrap.load(IuOpenIdConnectSpi.class);

	/**
	 * Default constructor.
	 */
	public OidcSpiDelegate() {
	}

	@Override
	public IuOpenIdProvider getOpenIdProvider(URI configUri, IuOpenIdClient client) {
		return DELEGATE.getOpenIdProvider(configUri, client);
	}

}
