package edu.iu.auth.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Defines credentials issued to this client.
 */
public interface IuAuthorizationCredentials extends IuPrivateKeyPrincipal {

	/**
	 * Gets the grant types the credentials are authorized for use with.
	 * 
	 * @return {@link Set} of {@link GrantType}
	 */
	Set<GrantType> getGrantTypes();

	/**
	 * Gets allowed authentication method.
	 * 
	 * @return {@link AuthMethod}
	 */
	AuthMethod getTokenEndpointAuthMethod();

	/**
	 * Gets the point in time the credentials expire.
	 * 
	 * @return {@link Instant}
	 */
	Instant getExpires();

	/**
	 * Gets the TTL for client assertions generated using these credentials.
	 * 
	 * @return {@link Duration}
	 */
	Duration getAssertionTtl();

}