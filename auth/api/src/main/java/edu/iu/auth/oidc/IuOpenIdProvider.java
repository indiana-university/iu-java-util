package edu.iu.auth.oidc;

import java.net.URI;

import edu.iu.auth.spi.IuOpenIdConnectSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Client-side SPI interface for interacting with an OpenID Provider.
 */
public interface IuOpenIdProvider {

	/**
	 * Configures the client view of an OpenID provider from a well-known
	 * configuration URI.
	 * 
	 * @param configUri Well-known configuration URI
	 * @return Client view of the OpenID provider
	 */
	static IuOpenIdProvider from(URI configUri) {
		return IuAuthSpiFactory.get(IuOpenIdConnectSpi.class).getOpenIdProvider(configUri);
	}

	/**
	 * Gets the OpenID provider configuration.
	 * 
	 * @return {@link IuOpenIdProviderConfiguration}
	 */
	IuOpenIdProviderConfiguration getConfiguration();

}
