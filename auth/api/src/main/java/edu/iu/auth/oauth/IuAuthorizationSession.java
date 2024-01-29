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
	 * @return authorization server
	 */
	static IuAuthorizationSession create() {
		return IuAuthSpiFactory.get(IuOAuthSpi.class).createAuthorizationSession();
	}

	/**
	 * Gets an authorization grant based on the client credentials configuration for
	 * the application.
	 * 
	 * @param realm authorization realm
	 * @param scope authorization scope
	 * @return client credentials grant
	 */
	IuAuthorizationGrant getClientCredentialsGrant(String realm, String scope);

	/**
	 * Creates a new authorization code grant.
	 * 
	 * @param realm authorization realm
	 * @param scope authorization scope
	 * @return new authorization code grant
	 */
	IuAuthorizationCodeGrant createAuthorizationCodeGrant(String realm, String scope);

	/**
	 * Gets a previously created authorization code grant by state.
	 * 
	 * @param state state value generated via
	 *              {@link #createAuthorizationCodeGrant(String, String)}
	 * @return authorization code grant
	 * @throws IuAuthorizationFailedException if the state value cannot be tied to
	 *                                        an existing grant
	 */
	IuAuthorizationCodeGrant getAuthorizationCodeGrant(String state) throws IuAuthorizationFailedException;

}
