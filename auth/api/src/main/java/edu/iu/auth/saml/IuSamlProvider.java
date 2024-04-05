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
	 * Authenticate access to an application resource by URI.
	 * 
	 * @param samlEntityId base identity provider URI to authorize access to
	 * @return redirect URI
	 * 
	 * @throws IuAuthenticationException If authorization could not be granted and
	 *                                   the client <em>must</em> complete
	 *                                   authentication before attempting
	 *                                   authorization.
	 */
	URI authorize(URI samlEntityId, URI postURI);
	
	
}
