package edu.iu.auth.client;

import edu.iu.auth.IuApiCredentials;
import edu.iu.crypt.WebToken;

/**
 * Represents a bearer token used with OAuth 2.0.
 * 
 * @see <A href="https://tools.ietf.org/html/rfc6750">OAuth 2.0 Bearer Token
 *      Usage</A>
 */
public interface IuBearerToken extends IuApiCredentials {

	/**
	 * Gets the raw access token, as it would be supplied as a bearer token to an
	 * API.
	 * 
	 * <p>
	 * Depending on requirements, a client application MAY inspect a bearer token
	 * after successfully retrieving it from the token endpoint, to verify intended
	 * use before {@link #applyTo(java.net.http.HttpRequest.Builder) applying} to an
	 * API request. For example, the client application might verify the token is a
	 * valid {@link WebToken#validateClaims(java.net.URI, java.time.Duration) JWT
	 * from a trusted issuer that lists the target API as audience} and claims an
	 * authorized client application user as the {@link WebToken#getSubject()
	 * subject}.
	 * </p>
	 * 
	 * @return Access token
	 */
	String getAccessToken();

}
