package edu.iu.web;

import javax.security.auth.Subject;

import com.sun.net.httpserver.HttpExchange;

/**
 * Authenticates incoming web requests.
 * <p>
 * Only one resource implementing this interface MAY be present in the container
 * environment.
 * </p>
 */
public interface IuWebAuthenticator {

	/**
	 * Authenticates a remote user based on request attributes from
	 * {@link HttpExchange}.
	 * 
	 * @param exchange {@link HttpExchange}
	 * @return {@link Subject} containing at least one valid principal. If more than
	 *         one principal is included in {@link Subject#getPrincipals()}, the
	 *         first is the identity of the caller, and subsequent principals
	 *         represent impersonated identities.
	 */
	Subject authenticate(HttpExchange exchange);

}
