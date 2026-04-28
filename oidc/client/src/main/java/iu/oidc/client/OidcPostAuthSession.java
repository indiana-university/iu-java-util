package iu.oidc.client;

import java.time.Instant;

import edu.iu.oidc.IuOidcTokenResponse;

/**
 * Holds tokens after successful OIDC authorization
 */
public interface OidcPostAuthSession {

	/**
	 * Gets the token response.
	 * 
	 * @return access token response
	 */
	IuOidcTokenResponse getTokenResponse();

	/**
	 * Sets the token response
	 * 
	 * @param tokenResponse token response
	 */
	void setTokenResponse(IuOidcTokenResponse tokenResponse);

	/**
	 * Gets the point in time token expiration date, from expires_in;
	 * 
	 * @return notAfter time
	 */
	Instant getNotAfter();

	/**
	 * Sets the notAfter time.
	 * 
	 * @param notAfter time.
	 */
	void setNotAfter(Instant notAfter);

}
