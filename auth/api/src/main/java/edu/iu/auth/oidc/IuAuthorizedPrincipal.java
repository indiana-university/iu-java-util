package edu.iu.auth.oidc;

import edu.iu.auth.IuPrincipalIdentity;

/**
 * Passes post-authorization details to the client.
 */
public interface IuAuthorizedPrincipal {

	/**
	 * Updated session cookie, if a state change was required in determining the
	 * principal identity of the authorized user.
	 * 
	 * @return Set-Cookie header value
	 */
	String getSetCookie();

	/**
	 * Principal identity of the authorized user.
	 * 
	 * @return {@link IuPrincipalIdentity}
	 */
	IuPrincipalIdentity getPrincipal();

}
