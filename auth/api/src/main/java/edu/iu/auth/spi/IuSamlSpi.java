package edu.iu.auth.spi;

import java.net.URI;

import edu.iu.auth.saml.IuSamlSession;


/**
 * Service provider interface for SAML.
 */
public interface IuSamlSpi {
	
	/**
	 * Creates a new {@link IuSamlSession} for managing SAML authorization
	 * server interactions.
	 * 
	 * @param realm      authentication realm
	 * @param entryPoint entry point URI
	 * @return {@link IuSamlSession}
	 */
	IuSamlSession createAuthorizationSession(String realm, URI entryPoint);

}
