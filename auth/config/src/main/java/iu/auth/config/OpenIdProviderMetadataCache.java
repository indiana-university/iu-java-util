package iu.auth.config;

import java.net.URI;

import edu.iu.auth.config.IuOpenIdProviderMetadata;

/**
 * Bursting cache of {@link IuOpenIdProviderMetadata} by public URI
 */
public class OpenIdProviderMetadataCache {

	/**
	 * Reads metadata from a public URI.
	 * 
	 * @param publicUri public URI
	 * @return {@link IuOpenIdProviderMetadata}, read from {@code publicUri}.
	 */
	static IuOpenIdProviderMetadata read(URI publicUri) {
		
		return null;
	}

}
