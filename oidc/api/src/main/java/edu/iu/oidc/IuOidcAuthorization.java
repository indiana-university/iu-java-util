package edu.iu.oidc;

import edu.iu.IuRequestAttributes;
import edu.iu.IuStatefulRedirect;

/**
 * Client application resource interface for OIDC authorization code flow.
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
	IuStatefulRedirect init(String delegatingPrincipal, String impersonatedPrincipalName);

	/**
	 * Resumes an authorization session upon return from the authorization server.
	 * 
	 * @param attributes request attributes
	 * @param code       authorization code
	 * @param state      state parameter value
	 * @return Verified {@link IuAuthorizationRedirect}
	 */
	IuStatefulRedirect authorize(IuRequestAttributes attributes, String code, String state);

	/**
	 * Gets the {@link IuOidcPrincipal} previously authorized for a web session.
	 * 
	 * @param attributes request attributes
	 * @return Verified {@link IuOidcPrincipal}
	 */
	IuOidcPrincipal getAuthorizedPrincipal(IuRequestAttributes attributes);

}
