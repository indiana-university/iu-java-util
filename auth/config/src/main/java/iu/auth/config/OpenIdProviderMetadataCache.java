package iu.auth.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;

/**
 * Bursting cache of {@link IuOpenIdProviderMetadata} by public URI
 */
public final class OpenIdProviderMetadataCache {
	static {
		IuObject.assertNotOpen(OpenIdProviderMetadataCache.class);
	}

	private static final Map<URI, IuOpenIdProviderMetadata> CACHE = new IuCacheMap<>(
			Optional.ofNullable(IuRuntimeEnvironment.envOptional("iu.auth.config.oidc.cacheTtl", Duration::parse))
					.orElse(Duration.ofMinutes(15L)));

	/**
	 * Simple {@link IuAuthorizationProvider} that metadata from public HTTP URIs.
	 * 
	 * @see AuthConfig#register(IuAuthConfig)
	 */
	public IuAuthorizationProvider HTTP = OpenIdProviderMetadataCache::get;

	private OpenIdProviderMetadataCache() {
	}

	/**
	 * Reads metadata from a public URI.
	 * 
	 * @param publicUri public URI
	 * @return {@link IuOpenIdProviderMetadata}, read from {@code publicUri}.
	 */
	static IuOpenIdProviderMetadata read(URI publicUri) {
		return IuJson.wrap(IuException.unchecked(() -> IuHttp.get(publicUri, IuHttp.READ_JSON_OBJECT)),
				IuOpenIdProviderMetadata.class);
	}

	/**
	 * Gets metadata for a public URI, cached for up to 15 minutes, configurable
	 * using the {@code iu.auth.config.oidc.cacheTtl} {@link IuRuntimeEnvironment
	 * environment property}.
	 * 
	 * @param publicUri public URI
	 * @return {@link IuOpenIdProviderMetadata}, from {@code publicUri}.
	 */
	public static IuOpenIdProviderMetadata get(URI publicUri) {
		return CACHE.computeIfAbsent(publicUri, OpenIdProviderMetadataCache::read);
	}

}
