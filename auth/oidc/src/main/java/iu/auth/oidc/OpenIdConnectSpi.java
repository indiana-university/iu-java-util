package iu.auth.oidc;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.auth.spi.IuOpenIdConnectSpi;

/**
 * OpenID connect SPI implementation.
 */
public class OpenIdConnectSpi implements IuOpenIdConnectSpi {

	private static Map<URI, OpenIdProvider> PROVIDERS = new HashMap<>();

	/**
	 * Default constructor.
	 */
	public OpenIdConnectSpi() {
	}

	@Override
	public synchronized IuOpenIdProvider getOpenIdProvider(URI configUri) {
		var provider = PROVIDERS.get(configUri);
		if (provider == null)
			PROVIDERS.put(configUri, provider = new OpenIdProvider(configUri));
		return provider;
	}

}
