package edu.iu.auth.oauth;

import java.net.URI;

/**
 * Represents an authorization grant, as described by the
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4">OAuth 2.0
 * Authorization Framework</a>.
 */
public interface IuAuthorizationCodeGrant extends IuAuthorizationGrant {

	/**
	 * Gets the state identifier, used to locate a grant within an
	 * {@link IuAuthorizationSession authorization session}.
	 * 
	 * @return state identifier
	 */
	String getState();

	/**
	 * Gets the redirect URI.
	 * 
	 * @return {@link URI}
	 */
	URI getRedirectUri();

	/**
	 * Authorizes the grant using a code provided by the authorization server.
	 * 
	 * @param code authorization code
	 * @return response details following successful authorization
	 * @throws IuAuthorizationFailedException If an authorization failure should be
	 *                                        reported to the client
	 */
	IuAuthorizationResponse authorize(String code) throws IuAuthorizationFailedException;

}