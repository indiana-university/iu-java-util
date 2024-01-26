package edu.iu.auth.oauth;

/**
 * Manages client-side authorization session state.
 */
public interface IuAuthorizationSession {

	/**
	 * Gets an authorization grant based on the client credentials configuration for
	 * the application.
	 * 
	 * @return client credentials grant
	 */
	IuAuthorizationGrant getClientCredentialsGrant();

	/**
	 * Creates a new authorization code grant.
	 * 
	 * @param realm authorization realm
	 * @return new authorization code grant
	 */
	IuAuthorizationGrant createAuthorizationCodeGrant(String realm);

	/**
	 * Gets a previously created authorization code grant by state.
	 * 
	 * @param state state value generated via
	 *              {@link #createAuthorizationCodeGrant(String)}
	 * @return authorization code grant
	 * @throws IuAuthorizationFailedException if the state value cannot be tied to
	 *                                        an existing grant
	 */
	IuAuthorizationGrant getAuthorizationCodeGrant(String state) throws IuAuthorizationFailedException;

}
