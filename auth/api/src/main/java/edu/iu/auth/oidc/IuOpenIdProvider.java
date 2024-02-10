package edu.iu.auth.oidc;

import java.net.URI;
import java.time.Duration;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationResponse;
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
	 * @param configUri             Well-known configuration URI
	 * @param trustRefreshInterval  Maximum length of time to keep trusted signing
	 *                              keys in cache
	 * @param authenticationTimeout Maximum length of time to allow for user
	 *                              authentication.
	 * @return Client view of the OpenID provider
	 */
	static IuOpenIdProvider from(URI configUri, Duration trustRefreshInterval, Duration authenticationTimeout) {
		return IuAuthSpiFactory.get(IuOpenIdConnectSpi.class).getOpenIdProvider(configUri, trustRefreshInterval,
				authenticationTimeout);
	}

	/**
	 * Gets the issue ID for this provider.
	 * 
	 * @return OpenID Provider issuer ID
	 */
	String getIssuer();

	/**
	 * Gets the OIDC User Info Endpoint URI
	 * 
	 * @return User Info Endpoint {@link URI}
	 */
	URI getUserInfoEndpoint();

	/**
	 * Creates an authorization client for interacting with the OpenID provider.
	 * 
	 * @param resourceUri       client resource URI
	 * @param clientCredentials client credentials
	 * @return authorization client
	 */
	IuAuthorizationClient createAuthorizationClient(URI resourceUri, IuApiCredentials clientCredentials);

	/**
	 * Verifies authentication attributes received via OAuth token response from an
	 * OIDC provider.
	 * 
	 * @param authResponse authorization response
	 * @return verified authentication attributes
	 */
	IuOpenIdAuthenticationAttributes verifyAuthentication(IuAuthorizationResponse authResponse);

}
