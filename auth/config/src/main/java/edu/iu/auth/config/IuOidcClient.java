package edu.iu.auth.config;

import java.net.URI;
import java.time.Duration;

import edu.iu.crypt.WebKey;

/**
 * Provides OIDC client-side configuration.
 */
public interface IuOidcClient {

	/**
	 * Gets the client ID.
	 * 
	 * @return client ID
	 */
	String getClientId();

	/**
	 * Gets the resource URI.
	 * 
	 * @return {@link URI}
	 */
	URI getResourceUri();

	/**
	 * Gets the redirect URI.
	 * 
	 * @return {@link URI}
	 */
	URI getRedirectUri();

	/**
	 * Gets the provider metadata URI.
	 * 
	 * @return {@link URI}
	 */
	URI getMetadataUri();

	/**
	 * Gets the client assertion signature key.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getAssertionJwk();

	/**
	 * Gets the decryption key.
	 * 
	 * @return {@link WebKey}
	 */
	Iterable<WebKey> getDecryptJwk();

	/**
	 * Gets the client assertion time to live.
	 * 
	 * @return {@link Duration}
	 */
	Duration getAssertionTtl();

	/**
	 * Gets the max allowed token time to live
	 * 
	 * @return {@link Duration}
	 */
	Duration getTokenTtl();

	/**
	 * Gets the max allowed time since user authentication.
	 * 
	 * @return {@link Duration}
	 */
	Duration getMaxAge();

	/**
	 * Gets the provider metadata refresh interval.
	 * 
	 * @return {@link Duration}
	 */
	Duration getMetadataRefreshInterval();

}
