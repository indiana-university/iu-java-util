package edu.iu.auth.oidc.spi;

import edu.iu.auth.oidc.OpenIDClient;
import edu.iu.auth.oidc.OpenIDProvider;

/**
 * Service provider interface for Open ID Connect.
 */
public interface OpenIDConnectSpi {

	/**
	 * Gets an {@link OpenIDProvider} implementation suitable for interactions on
	 * behalf of an {@link OpenIDClient}.
	 * 
	 * @param client {@link OpenIDClient}
	 * @return {@link OpenIDProvider}
	 */
	OpenIDProvider getOpenIDProvider(OpenIDClient client);

}
