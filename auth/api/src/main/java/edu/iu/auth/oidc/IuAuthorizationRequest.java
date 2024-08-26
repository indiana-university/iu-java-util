package edu.iu.auth.oidc;

import java.net.URI;
import java.util.Map;

/**
 * Encapsulates incoming authorization request attributes.
 */
public interface IuAuthorizationRequest {

	/**
	 * Gets the incoming request URI.
	 * 
	 * @return request URI
	 */
	URI getUri();

	/**
	 * Gets the remote client IP address.
	 * 
	 * @return remote Client IP
	 */
	String getRemoteAddr();

	/**
	 * Gets the User-Agent header value.
	 * 
	 * @return User-Agent header value
	 */
	String getUserAgent();

	/**
	 * Gets the Authorizaton header value.
	 * 
	 * @return Authorizaton header value
	 */
	String getAuthorizaton();

	/**
	 * Gets the incoming request parameters.
	 * 
	 * @return Map of request parameters
	 */
	Map<String, String[]> getParams();

}
