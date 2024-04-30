package edu.iu.auth.saml;

import java.net.InetAddress;
import java.net.URI;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Manages client-side session state.
 * TODO implement session support mechanism for SAML 
 */
public interface IuSamlSession {
	/**
	 * Creates a new {@link IuAuthorizationSession} for managing interactions with
	 * an authorization server.
	 * 
	 * @param serviceProviderEntityId   authentication service provider entity id, <em>must</em> correspond to an
	 *                   {@link IuSamlClient#getServiceProviderEntityId()
	 *                   initialized client}.
	 * @param entryPoint <em>optional</em> entry point URI for the application
	 *                   requiring authorization, <em>may</em> be sent to the user
	 *                   agent as a redirect when authorization expires or used as a
	 *                   default applicationUri value; null if
	 *                   not defined for the application
	 * @return authorization session
	 */
	static IuSamlSession create(String serviceProviderEntityId, URI entryPoint) {
		return IuAuthSpiFactory.get(IuSamlSpi.class).createAuthorizationSession(serviceProviderEntityId, entryPoint);
	}
	
	
	/**
	 * Get SAML authentication Request to request application resource access by entity Id and post URI.
	 * 
	 * @param entityId base identity provider URI to authorize access to
	 * @param postUri Post back URI
	 * @param resourceUri resource to authorize access to 
	 * @return redirect URI
	 * 
	 */
	URI authRequest(URI samlEntityId, URI postUri, URI resourceUri);


	/**
	 * Authorize SAML response received back from identity provider
	 * @param address IP address to validate against allowed list 
	 * @param acsUrl assertion consumption service post url
	 * @param samlResponse SAML response that received back from identity provider after user has been authenticate
	 * @throws IuAuthenticationException 
	 *  
	 */
	void authorize(InetAddress address, String acsUrl, String samlResponse, String relayState) throws IuAuthenticationException;
	
}
