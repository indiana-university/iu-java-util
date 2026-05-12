package iu.oidc.client.config;

import java.net.URI;
import java.time.Duration;

import edu.iu.crypt.WebKey;

/**
 * Client view of an OIDC registration.
 */
public interface IuOidcClient {
	
	/**
	 * Gets the resource URI.
	 * 
	 * @return resource URI
	 */
	URI getResourceUri();

	/**
	 * Gets the client ID.
	 * 
	 * @return client ID
	 */
	String getClientId();

	/**
	 * Gets the client secret.
	 * 
	 * @return client secret
	 */
	String getClientSecret();

	/**
	 * Gets the resource owner username.
	 * 
	 * @return resource owner username
	 */
	String getUsername();

	/**
	 * Gets the resource owner password.
	 * 
	 * @return resource owner password
	 */
	String getPassword();

	/**
	 * Determines if client secret should sent via the Authorization Basic header.
	 * 
	 * @return true to send client secret as Authorization Basic
	 *         (client_secret_basic); false (default) to send as POST param
	 *         (client_secret_post). Ignored if {@link #getAssertionJwk()} is
	 *         non-null.
	 */
	default boolean isUseBasicAuth() {
		return false;
	}

	/**
	 * Gets {@link WebKey} to use for signing assertions issued on behalf of this
	 * client.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getAssertionJwk();

	/**
	 * Gets the length of time to issue client assertions for.
	 * 
	 * @return {@link Duration}; default is PT2M
	 */
	default Duration getAssertionTtl() {
		return Duration.ofMinutes(2L);
	}

	/**
	 * Gets the available keys for decrypting tokens for this client.
	 * 
	 * @return {@link WebKey}s for decryption
	 */
	Iterable<WebKey> getDecryptJwk();

	/**
	 * Gets the maximum length of time to allow an ID token issued to this client to
	 * be valid.
	 * 
	 * @return {@link Duration}; default is PT15M
	 */
	default Duration getTokenTtl() {
		return Duration.ofMinutes(15L);
	}

	/**
	 * Gets the maximum length of time to allow since user authentication.
	 * 
	 * @return {@link Duration}; default is PT12H
	 */
	default Duration getMaxAge() {
		return Duration.ofHours(12L);
	}

}
