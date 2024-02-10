package edu.iu.auth.oauth;

import java.util.Set;

import edu.iu.auth.IuAuthenticationChallengeException;
import edu.iu.auth.IuAuthenticationRedirectException;

/**
 * Represents an authorization grant, as described by the
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4">OAuth 2.0
 * Authorization Framework</a>.
 */
public interface IuAuthorizationGrant {

	/**
	 * Gets the client ID.
	 * 
	 * @return client ID.
	 */
	String getClientId();

	/**
	 * Gets the requested scope.
	 * 
	 * @return requested scope, may be null to request the default scope as defined
	 *         by the authorization server
	 */
	Set<String> getScope();

	/**
	 * Authorizes the grant using previously established authentication attributes.
	 * 
	 * @return response details following successful authorization.
	 * 
	 * @throws IuAuthenticationChallengeException If authorization could not be
	 *                                            granted and the client
	 *                                            <em>must</em> complete
	 *                                            authentication before attempting
	 *                                            authorization.
	 * @throws IuAuthenticationRedirectException  If authorization could not be
	 *                                            granted and user agent interaction
	 *                                            is <em>required</em> in order to
	 *                                            complete authentication.
	 */
	IuAuthorizationResponse authorize() throws IuAuthenticationChallengeException;

	/**
	 * Discards all established credentials, forcing direct interaction with the
	 * authorization server on the next use.
	 * 
	 * <p>
	 * Implementations <em>should</em> delegate credentials revocation to the
	 * authorization server, if support.
	 * </p>
	 */
	void revoke();

}
