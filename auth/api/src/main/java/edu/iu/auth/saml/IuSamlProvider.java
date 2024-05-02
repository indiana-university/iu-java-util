package edu.iu.auth.saml;

import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;


/**
 * Client-side SPI interface for interacting with an SAML Provider.
 */
public interface IuSamlProvider {

	/**
	 * Gets an {@link IuSamlProvider} implementation.
	 * 
	 * @param client configuration metadata
	 * @return {@link IuSamlProvider}
	 */
	static IuSamlProvider from(IuSamlClient client) {
		return IuAuthSpiFactory.get(IuSamlSpi.class).getSamlProvider(client);
	}

	
	
	/**
	 * Get service provider metadata for registered client
	 * @return service provider metadata
	 */
	String getServiceProviderMetaData();

	
}