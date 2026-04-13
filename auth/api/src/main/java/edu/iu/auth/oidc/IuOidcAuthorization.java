package edu.iu.auth.oidc;

import edu.iu.auth.IuRequestAttributes;

/**
 * Authorization resource for interacting with an OIDC authorization server.
 */
public interface IuOidcAuthorization {

	/**
	 * Initialized a new authorization session.
	 * 
	 * @param delegatingPrincipal       required delegating principal name; null to
	 *                                  authorize on behalf of the authenticated
	 *                                  user
	 * @param impersonatedPrincipalName requested impersonated principal name; null
	 *                                  if not requesting impersonation, SHOULD be
	 *                                  null in production environments
	 * 
	 * @return authorization redirect
	 */
	IuAuthorizationRedirect init(String delegatingPrincipal, String impersonatedPrincipalName);

	/**
	 * Resumes an authorization session upon return from the authorization server.
	 * 
	 * @param attributes request attributes
	 * @param code       authorization code
	 * @param state      state parameter value
	 * @return Verified {@link BursarPayAuthorizationResult}; null if an authorized
	 *         session could not be restored using the request cookies
	 */
	IuAuthorizationRedirect authorize(IuRequestAttributes attributes, String code, String state);

	/**
	 * Gets the {@link BursarPayPIuAuthorizedPrincipalrincipal} previously
	 * authorized for a web session.
	 * 
	 * @param attributes request attributes
	 * @return Verified {@link IuAuthorizedPrincipal}; null if an authorized session
	 *         could not be restored using the request cookies
	 */
	IuAuthorizedPrincipal getAuthorizedPrincipal(IuRequestAttributes attributes);

}
