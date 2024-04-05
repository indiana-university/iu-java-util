package edu.iu.auth.saml;

import java.net.URI;

import edu.iu.auth.IuAuthenticationException;
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
	 * Get SAML authentication Request to request application resource access by entity Id and post URI.
	 * 
	 * @param entityId base identity provider URI to authorize access to
	 * @param postURI Post back URI
	 * @return redirect URI
	 * 
	 */
	URI authRequest(URI entityId, URI postURI);
	
	
}
