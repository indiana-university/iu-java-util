package edu.iu.auth.oidc;

import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.oidc.spi.OpenIDConnectSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Client-side SPI interface for interacting with an OpenID Provider.
 */
public interface OpenIDProvider {

	/**
	 * Gets a client view of the OpenID provider.
	 * 
	 * @param client {@link OpenIDClient}
	 * @return Client view of the OpenID provider
	 */
	static OpenIDProvider forClient(OpenIDClient client) {
		return IuAuthSpiFactory.get(OpenIDConnectSpi.class).getOpenIDProvider(client);
	}

	/**
	 * Creates a new {@link IuAuthorizationSession} for use with the OpenID
	 * provider.
	 * 
	 * @return {@link IuAuthorizationSession}
	 */
	IuAuthorizationSession createAuthorizationSession();

}
