package edu.iu.auth.oidc;

import java.net.URI;

/**
 * Holds redirect metadata supporting interactive flows between authentication
 * and authorization endpoints.
 */
public interface IuAuthorizationRedirect {

	/**
	 * Pre-authorization session cookie.
	 * 
	 * @return Set-Cookie header value
	 */
	String getSetCookie();

	/**
	 * Authorization endpoint redirect URI.
	 * 
	 * @return {@link URI}
	 */
	URI getLocation();

}
