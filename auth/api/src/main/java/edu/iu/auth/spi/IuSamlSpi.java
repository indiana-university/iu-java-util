package edu.iu.auth.spi;

import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;


/**
 * Service provider interface for SAML.
 */
public interface IuSamlSpi {
	
	/**
	 * Gets an {@link IuSamlProvider} implementation.
	 * @param client client configuration
	 * @return {@link IuSamlProvider}
	 */
	IuSamlProvider getSamlProvider(IuSamlClient client);

}
