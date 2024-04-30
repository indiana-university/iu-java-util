package edu.iu.auth.oauth;

import edu.iu.auth.IuPrincipalIdentity;

/**
 * Returned by {@link IuAuthorizationClient#verify(IuTokenResponse)} and
 * {@link IuAuthorizationClient#verify(IuTokenResponse, IuTokenResponse)} to
 * pass the verified principal identity and delegated authentication realm as a
 * final response to authorization code and/or refresh token flow.
 */
public interface IuAuthorizedPrincipal {

	/**
	 * Gets the authentication realm.
	 * 
	 * @return realm
	 */
	String getRealm();

	/**
	 * Gets the principal identity.
	 * 
	 * @return {@link IuPrincipalIdentity}
	 */
	IuPrincipalIdentity getPrincipal();

}
