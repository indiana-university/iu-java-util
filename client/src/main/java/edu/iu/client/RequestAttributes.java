package edu.iu.client;

import java.net.HttpCookie;
import java.net.URI;

/**
 * Encapsulates attributes commonly associated with an HTTP request.
 */
public interface RequestAttributes {

	/**
	 * Gets the full request URI to the resource that issued the token.
	 * 
	 * @return Request URI
	 */
	URI getRequestUri();

	/**
	 * Gets the remote client IP address.
	 * 
	 * @return IP address
	 */
	String getRemoteAddr();

	/**
	 * Gets the caller's user agent.
	 * 
	 * @return User-Agent header value
	 */
	String getUserAgent();

	/**
	 * Gets incoming request cookies.
	 * 
	 * @return request cookies
	 */
	Iterable<HttpCookie> getCookies();

}
