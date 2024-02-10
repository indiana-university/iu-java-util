package edu.iu.auth.spi;

import java.net.URI;

import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;

/**
 * Service provider interface for OAuth.
 */
public interface IuOAuthSpi {

	/**
	 * Initializes client metadata.
	 * 
	 * @param client client metadata
	 * @return <em>optional</em> client credentials grant
	 * @see IuAuthorizationClient#initialize(IuAuthorizationClient)
	 */
	IuAuthorizationGrant initialize(IuAuthorizationClient client);

	/**
	 * Creates a new {@link IuAuthorizationSession} for managing OAuth authorization
	 * server interactions.
	 * 
	 * @param realm      authentication realm
	 * @param entryPoint entry point URI
	 * @return {@link IuAuthorizationSession}
	 */
	IuAuthorizationSession createAuthorizationSession(String realm, URI entryPoint);

}
