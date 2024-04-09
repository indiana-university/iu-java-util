package edu.iu.auth.saml;

import java.net.InetAddress;
import java.net.URI;

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
	 * @param sessionId  sessionId maintain by client that set in the RelayState parameter in the SAML request. 
	 * @return redirect URI
	 * 
	 */
	URI authRequest(URI entityId, URI postURI, String sessionId);
	
	/**
	 * Get service provider metadata for registered client
	 * @return service provider metadata
	 */
	String getServiceProviderMetaData();

	/**
	 * A support method to validate SAML response received back from identity provider
	 * @param address IP address to validate against allowed list 
	 * @param acsUrl assertion consumption service post url
	 * @param samlResponse SAML response that received back from identity provider after user has been authenticate
	 */
	void validate(InetAddress address, String acsUrl, String samlResponse);

	
}
