package edu.iu.auth.spi;

import java.net.URI;

import edu.iu.auth.oidc.IuOpenIdProvider;

/**
 * Service provider interface for Open ID Connect.
 */
public interface IuOpenIdConnectSpi {

	/**
	 * Gets an {@link IuOpenIdProvider} implementation.
	 * 
	 * @param configUri Well-known configuration URI
	 * @return {@link IuOpenIdProvider}
	 */
	IuOpenIdProvider getOpenIdProvider(URI configUri);

}
