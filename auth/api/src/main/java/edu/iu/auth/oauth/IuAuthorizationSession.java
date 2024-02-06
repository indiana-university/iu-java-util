package edu.iu.auth.oauth;

import edu.iu.auth.spi.IuOAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Manages client-side authorization session state.
 */
public interface IuAuthorizationSession {

	/**
	 * Creates a new {@link IuAuthorizationSession} for managing interactions with
	 * an authorization server.
	 * 
	 * @param realm authorization realm
	 * @return authorization server
	 */
	static IuAuthorizationSession create(String realm) {
		return IuAuthSpiFactory.get(IuOAuthSpi.class).createAuthorizationSession(realm);
	}

	/**
	 * Gets an authorization grant based on the client credentials configuration for
	 * the application.
	 * 
	 * @param scope authorization scope
	 * @return client credentials grant
	 */
	IuAuthorizationGrant getClientCredentialsGrant(String scope);

	/**
	 * Creates a new authorization code grant.
	 * 
	 * @param scope authorization scope
	 * @return new authorization code grant
	 */
	IuAuthorizationCodeGrant createAuthorizationCodeGrant(String scope);

	/**
	 * Gets a previously created authorization code grant by state.
	 * 
	 * @param state state value generated via
	 *              {@link #createAuthorizationCodeGrant(String)}
	 * @return authorization code grant
	 * @throws IuAuthorizationFailedException if the state value cannot be tied to
	 *                                        an existing grant
	 */
	IuAuthorizationCodeGrant getAuthorizationCodeGrant(String state) throws IuAuthorizationFailedException;

}
