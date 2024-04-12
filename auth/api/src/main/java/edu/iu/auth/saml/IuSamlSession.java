package edu.iu.auth.saml;

import java.net.URI;

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
	 * @param realm      authentication realm, <em>must</em> correspond to an
	 *                   {@link IuSamlClient#getServiceProviderEntityId()
	 *                   initialized client}.
	 * @param entryPoint <em>optional</em> entry point URI for the application
	 *                   requiring authorization, <em>may</em> be sent to the user
	 *                   agent as a redirect when authorization expires or used as a
	 *                   default applicationUri value; null if
	 *                   not defined for the application
	 * @return authorization session
	 */
	static IuSamlSession create(String realm, URI entryPoint) {
		return IuAuthSpiFactory.get(IuSamlSpi.class).createAuthorizationSession(realm, entryPoint);
	}
}
