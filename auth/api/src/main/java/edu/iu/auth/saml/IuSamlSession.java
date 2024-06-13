package edu.iu.auth.saml;

import java.net.URI;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Manages client-side session state.
 */
public interface IuSamlSession {
	
	/**
	 * Creates a new {@link IuAuthorizationSession} for managing interactions with
	 * an authorization server.
	 * 
	 * @param serviceProviderEntityId unique service provider entity id,
	 *                                <em>must</em> correspond to an
	 *                                {@link IuSamlClient#getServiceProviderEntityId()
	 *                                initialized client}.
	 * @param entryPoint              <em>optional</em> entry point URI for the
	 *                                application requiring authorization,
	 *                                <em>may</em> be sent to the user agent as a
	 *                                redirect when authorization expires or used as
	 *                                a default applicationUri value; null if not
	 *                                defined for the application
	 * @return authorization session
	 */
	static IuSamlSession create(String serviceProviderEntityId, URI entryPoint) {
		return IuAuthSpiFactory.get(IuSamlSpi.class).createAuthorizationSession(serviceProviderEntityId, entryPoint);
	}

	/**
	 * Gets SAML authentication Request to request application resource access by
	 * entity Id and post URI.
	 * 
	 * @param entityId    identity provider root URI to authorize access to
	 * @param acsPostUri  Post back URI
	 * @param resourceUri resource to authorize access to
	 * @return redirect URI
	 * TODO: consider removing params, pass by config
	 */
	URI getAuthenticationRequest(URI entityId, URI acsPostUri, URI resourceUri);

	/**
	 * Authorize SAML response received back from identity provider
	 * 
	 * @param remoteAddr   IP address to validate against allowed list
	 * @param acsPostUri   Post Uri to validate against response
	 * @param samlResponse SAML response that received back from identity provider
	 *                     after user has been authenticate
	 * @param relayState   state value that received back from identity provider
	 *                     after successful authentication.
	 * @return {@link IuSamlPrincipal} authorized SAML principal
	 * @throws IuAuthenticationException when relay state is invalid or
	 *                                   authorization failed
	 * 
	 */
	IuSamlPrincipal authorize(String remoteAddr, URI acsPostUri, String samlResponse, String relayState)
			throws IuAuthenticationException;

	/**
	 * Gets authorized SAML principal
	 * 
	 * @return authorized SAML principal
	 * @throws IuAuthenticationException If authorization session is expired
	 */
	IuSamlPrincipal getPrincipalIdentity() throws IuAuthenticationException;

}
