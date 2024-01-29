package edu.iu.auth.oauth;

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
	 * @return scope
	 */
	String getScope();

	/**
	 * Authorizes the grant using previously established authentication attributes.
	 * 
	 * @return response details following successful authorization; null if the
	 *         grant could not be authorized without first initiating an
	 *         authorization flow.
	 * @throws IuAuthorizationFailedException If an authorization failure should be
	 *                                        reported to the client.
	 */
	IuAuthorizationResponse authorize() throws IuAuthorizationFailedException;

}
