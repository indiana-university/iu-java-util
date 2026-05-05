package edu.iu.oidc;

import java.net.URI;
import java.security.Principal;

import edu.iu.jwt.WebToken;

/**
 * Client application view of principal user identity established via
 * interaction with an OIDC provider.
 */
public interface IuOidcPrincipal extends Principal {

	/**
	 * Updated session cookie, populated if a state change was required while
	 * resolving the principal.
	 * 
	 * @return Set-Cookie header value
	 */
	String getSetCookie();

	/**
	 * Gets the verified ID token for this principal.
	 * 
	 * @return {@link WebToken} ID token
	 */
	WebToken getIdToken();

	/**
	 * Gets a claim value.
	 * 
	 * @param <T>  claim value type
	 * @param name claim name
	 * @param type claim type
	 * @return claim value, from userinfo if available; else from ID token
	 */
	<T> T getClaim(String name, Class<T> type);

	/**
	 * Gets an access token issued to this principal for use with a given remote
	 * resource.
	 * 
	 * @param resourceUri root resource URI for the API to get an access token for
	 * @return access token for use at the indicated resource URI
	 */
	String getAccessToken(URI resourceUri);

}
