package edu.iu.auth.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Provides client-facing remote resource authorization metadata.
 */
public interface IuAuthorizationResource {

	/**
	 * Gets the self-issued {@link IuOpenIdProviderMetadata} {@link URI} trusted for
	 * this resource.
	 * 
	 * @return {@link URI}
	 */
	URI getProviderMetadataUri();

	/**
	 * Gets the JWK {@code kid} property value for the key relative to
	 * {@link IuOpenIdProviderMetadata}
	 * 
	 * @return JWK {@code kid} property value
	 */
	String getIdTokenKeyId();

	/**
	 * Gets the maximum time to live to allow for access tokens.
	 * 
	 * @return {@link Duration}
	 */
	Duration getTokenTtl();

	/**
	 * Gets the API endpoint root resource {@link URI}s.
	 * 
	 * @return {@link List} of {@link URI}
	 */
	Iterable<URI> getEndpointUris();

	/**
	 * Gets allowed scopes.
	 * 
	 * @return {@link Iterable} of authorization scopes the client MAY request
	 */
	Iterable<String> getScope();

	/**
	 * Gets client credentials to use for authorizing access
	 * 
	 * @return {@link IuAuthorizationCredentials}
	 */
	IuAuthorizationCredentials getCredentials();

}
