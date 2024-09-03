package iu.auth.client;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.client.IuHttp;
import iu.auth.config.AuthConfig;

/**
 * Maintaines cached references to OpenID connect metadata.
 */
class OpenIdProviderMetadataCache {
	static {
		IuObject.assertNotOpen(OpenIdProviderMetadataCache.class);
	}

	private static final Map<URI, IuOpenIdProviderMetadata> CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	private OpenIdProviderMetadataCache() {
	}

	/**
	 * Gets {@link IuOpenIdProviderMetadata} by {@link URI}.
	 * 
	 * @param uri {@link URI}
	 * @return {@link IuOpenIdProviderMetadata}
	 */
	static IuOpenIdProviderMetadata get(URI uri) {
		var metadata = CACHE.get(uri);
		if (metadata == null)
			CACHE.put(uri, metadata = AuthConfig.adaptJson(IuOpenIdProviderMetadata.class)
					.fromJson(IuException.unchecked(() -> IuHttp.get(uri, IuHttp.READ_JSON))));
		return metadata;
	}

}
