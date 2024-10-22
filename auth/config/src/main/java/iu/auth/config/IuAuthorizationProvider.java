package iu.auth.config;

import java.net.URI;

import edu.iu.auth.config.IuOAuthProviderMetadata;

/**
 * Configures the client-side view of an authorization provider.
 */
public interface IuAuthorizationProvider {

	/**
	 * Gets authorization metadata for a URI.
	 * 
	 * @param publicUri public URI
	 * @return {@link IuOAuthProviderMetadata}, from {@code publicUri}.
	 */
	IuOAuthProviderMetadata get(URI publicUri);

}
