package edu.iu.auth.oauth;

import java.net.URI;
import java.util.Set;

import edu.iu.auth.IuAuthenticationChallengeException;
import edu.iu.auth.IuAuthenticationRedirectException;
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
	 * @param realm      authentication realm, <em>must</em> correspond to an
	 *                   {@link IuAuthorizationClient#initialize(IuAuthorizationClient)
	 *                   initialized client}.
	 * @param entryPoint <em>optional</em> entry point URI for the application
	 *                   requiring authorization, <em>may</em> be sent to the user
	 *                   agent as a redirect when authorization expires or used as a
	 *                   default {@link #grant(Set, URI) applicationUri} value;
	 *                   null if not defined for the application
	 * @return authorization session
	 */
	static IuAuthorizationSession create(String realm, URI entryPoint) {
		return IuAuthSpiFactory.get(IuOAuthSpi.class).createAuthorizationSession(realm, entryPoint);
	}

	/**
	 * Gets a authorization code grant with default scope and entry point.
	 * 
	 * @return new authorization code grant
	 * @throws UnsupportedOperationException if an entry point was not defined
	 */
	IuAuthorizationCodeGrant grant() throws UnsupportedOperationException;

	/**
	 * Gets a authorization code grant using the session default entry point.
	 * 
	 * @param scope requested authorization scope; may be null or empty if a
	 *              specific scope is not requested, or to accept the default scope
	 *              as defined by the authorization server
	 * @return new authorization code grant
	 * @throws UnsupportedOperationException if an entry point was not defined
	 */
	IuAuthorizationCodeGrant grant(Set<String> scope) throws UnsupportedOperationException;

	/**
	 * Gets a authorization code grant by scope and application URI.
	 * 
	 * @param scope          requested authorization scope; may be null or empty if
	 *                       a specific scope is not requested, or to accept the
	 *                       default scope as defined by the authorization server
	 * @param applicationUri URI for the application requiring authorization,
	 *                       <em>may</em> be null to use the entry point;
	 *                       <em>required</em> if an entry point was not defined for
	 *                       the session.
	 * @return authorization code grant
	 */
	IuAuthorizationCodeGrant grant(Set<String> scope, URI applicationUri);

	/**
	 * Gets a pending grant by state.
	 * 
	 * @param state state value generated from
	 *              {@link IuAuthorizationCodeGrant#getState()}
	 * @return authorization code grant
	 * @throws IuAuthenticationChallengeException if the state value cannot be tied
	 *                                            to a valid authorization grant
	 * @throws IuAuthenticationRedirectException  if the state value is tied to an
	 *                                            expired grant
	 */
	IuAuthorizationCodeGrant resume(String state)
			throws IuAuthenticationChallengeException, IuAuthenticationRedirectException;

}
