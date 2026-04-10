package iu.auth.config;

import java.time.Instant;

/**
 * Holds tokens after successful OIDC authorization
 */
public interface OidcPostAuthSession {

	/**
	 * Gets the access token.
	 * 
	 * @return access token
	 */
	String getAccessToken();

	/**
	 * Sets the access token.
	 * 
	 * @param accessToken access token
	 */
	void setAccessToken(String accessToken);

	/**
	 * Gets the id token.
	 * 
	 * @return id token
	 */
	String getIdToken();

	/**
	 * Sets the id token.
	 * 
	 * @param idToken id token
	 */
	void setIdToken(String idToken);

	/**
	 * Gets the refresh token.
	 * 
	 * @return refresh token
	 */
	String getRefreshToken();

	/**
	 * Sets the refresh token.
	 * 
	 * @param refreshToken refresh token
	 */
	void setRefreshToken(String refreshToken);

	/**
	 * Gets the point in time after which the access token SHOULD not be used.
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
