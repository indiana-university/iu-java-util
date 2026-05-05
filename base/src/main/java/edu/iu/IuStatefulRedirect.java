package edu.iu;

import java.net.URI;

/**
 * Passes redirect metadata supporting interactive flows involving a stateful
 * session cookie.
 */
public interface IuStatefulRedirect {

	/**
	 * Gets the Set-Cookie header value.
	 * 
	 * @return Set-Cookie header value
	 */
	String getSetCookie();

	/**
	 * Gets the Location header value.
	 * 
	 * @return Location header value
	 */
	URI getLocation();

}
