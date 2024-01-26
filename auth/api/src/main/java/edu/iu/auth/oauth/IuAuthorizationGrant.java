package edu.iu.auth.oauth;

/**
 * Represents an authorization grant, as described by the
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4">OAuth 2.0
 * Authorization Framework</a>.
 */
public interface IuAuthorizationGrant {

	/**
	 * Authorizes the grant using a code provided by the authorization server.
	 * 
	 * @return response details following successful authorization
	 * @throws IuAuthorizationFailedException If an authorization failure should be
	 *                                        reported to the client
	 */
	IuAuthorizationResponse authorize() throws IuAuthorizationFailedException;

}
