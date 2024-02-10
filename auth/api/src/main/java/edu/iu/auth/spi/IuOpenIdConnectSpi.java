package edu.iu.auth.spi;

import java.net.URI;
import java.time.Duration;

import edu.iu.auth.oidc.IuOpenIdProvider;

/**
 * Service provider interface for Open ID Connect.
 */
public interface IuOpenIdConnectSpi {

	/**
	 * Gets an {@link IuOpenIdProvider} implementation.
	 * 
	 * @param configUri             Well-known configuration URI
	 * @param trustRefreshInterval  Maximum length of time to keep trusted signing
	 *                              keys in cache
	 * @param authenticationTimeout Maximum length of time to allow for user
	 *                              authentication.
	 * @return {@link IuOpenIdProvider}
	 */
	IuOpenIdProvider getOpenIdProvider(URI configUri, Duration trustRefreshInterval, Duration authenticationTimeout);

}
