package edu.iu.auth.spi;

import edu.iu.auth.oauth.IuAuthorizationSession;

/**
 * Service provider interface for OAuth.
 */
public interface IuOAuthSpi {

	/**
	 * Creates a new {@link IuAuthorizationSession} for managing OAuth authorization
	 * server interactions.
	 * 
	 * @return {@link IuAuthorizationSession}
	 */
	IuAuthorizationSession createAuthorizationSession();

}
