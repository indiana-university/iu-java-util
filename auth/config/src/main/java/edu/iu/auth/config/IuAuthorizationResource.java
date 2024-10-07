package edu.iu.auth.config;

import java.util.Map;

/**
 * Configures the client-side view of an API resource.
 */
public interface IuAuthorizationResource {

	/**
	 * Gets the client ID.
	 * 
	 * @return client ID
	 */
	String getClientId();

	/**
	 * Gets the provider metadata URI.
	 * 
	 * @return provider metadata URI
	 */
	IuOAuthProviderMetadata getProviderMetadataUri();

	/**
	 * Gets the provider metadata.
	 * 
	 * @return provider metadata
	 */
	IuOAuthProviderMetadata getProviderMetadata();

	/**
	 * Gets the issuer key ID.
	 * 
	 * @return issuer key ID
	 */
	String getIssuerKeyId();

	/**
	 * Gets the scope.
	 * 
	 * @return scope
	 */
	Iterable<String> getScope();

	/**
	 * Indicates if an unauthenticated challenge request should be sent to the API
	 * endpoint in order to determine the nonce value to send with an authenticated
	 * requests to the token endpoint.
	 * 
	 * @return true if a nonce challenge should be sent
	 */
	boolean isNonceChallenge();

	/**
	 * Gets additional parameters to include with token endpoint requests.
	 * 
	 * @return additional token endpoint parameters
	 */
	Map<String, String> getAdditionalParameters();

	/**
	 * Gets the credentials.
	 * 
	 * @return {@link IuAuthorizationCredentials}
	 */
	IuAuthorizationCredentials getCredentials();

}
