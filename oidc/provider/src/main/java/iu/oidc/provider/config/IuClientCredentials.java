package iu.oidc.provider.config;

import java.time.Instant;

import edu.iu.crypt.WebKey;

/**
 * Defines credentials issued to a client.
 */
public interface IuClientCredentials {

	/**
	 * Gets the point in time the credentials expire.
	 * 
	 * @return {@link Instant}
	 */
	Instant getExpires();

	/**
	 * Gets the JWK private or secret key.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getJwk();

}
