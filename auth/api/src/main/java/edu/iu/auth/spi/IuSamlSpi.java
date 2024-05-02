package edu.iu.auth.spi;

import java.net.URI;

import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import edu.iu.auth.saml.IuSamlSession;


/**
 * Service provider interface for SAML.
 */
public interface IuSamlSpi {
	
	/**
	 * Gets an {@link IuSamlProvider} implementation.
	 * @param client client configuration
	 * @return {@link IuSamlProvider}
	 */
	IuSamlProvider getSamlProvider(IuSamlClient client);
	
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
