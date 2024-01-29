package edu.iu.auth.spi;

import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationSession;

/**
 * Service provider interface for OAuth.
 */
public interface IuOAuthSpi {

	/**
	 * Initializes client metadata.
	 * 
	 * @param realm  authorization realm
	 * @param client client metadata
	 * @see IuAuthorizationClient#initialize(String, IuAuthorizationClient)
	 */
	void initialize(String realm, IuAuthorizationClient client);

	/**
	 * Creates a new {@link IuAuthorizationSession} for managing OAuth authorization
	 * server interactions.
	 * 
	 * @return {@link IuAuthorizationSession}
	 */
	IuAuthorizationSession createAuthorizationSession();

}
