package edu.iu.auth.oauth;

import java.net.URI;

/**
 * Caller client identification and authentication details for authorizing
 * remote invocation.
 */
public interface IuCallerAttributes extends IuAuthorizationDetails {

	/**
	 * Authorization details type value to match.
	 */
	static String TYPE = "iu:caller_attributes";

	@Override
	default String getType() {
		return TYPE;
	}

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
	 * Gets the principal name of the authenticated user.
	 * 
	 * @return Principal name
	 */
	String getAuthnPrincipal();

}
