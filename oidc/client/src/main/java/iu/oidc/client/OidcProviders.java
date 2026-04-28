package iu.oidc.client;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.oidc.IuOidcProviderMetadata;
import iu.oidc.client.config.IuOidcProvider;

/**
 * Loads {@link IuOidcProviderMetadata} instances.
 */
public final class OidcProviders {

	private static final Logger LOG = Logger.getLogger(OidcProviders.class.getName());

	private static class CachedOidcProviderMetadata {
		private IuOidcProviderMetadata instance;
		private Instant lastUpdate;
	}

	private static Map<URI, CachedOidcProviderMetadata> OIDC_PROVIDER_CACHE = new HashMap<>();

	/**
	 * Gets OIDC provider metadata.
	 * 
	 * @param config {@link IuOidcProvider} provider configuration
	 * @return {@link IuOpenIdProviderMetadata}
	 */
	public static IuOidcProviderMetadata getMetadata(IuOidcProvider config) {
		final var metadata = config.getMetadata();
		if (metadata != null)
			return metadata;

		final var issuer = Objects.requireNonNull(config.getIssuer(), "missing issuer in config");

		final CachedOidcProviderMetadata cached;
		synchronized (OIDC_PROVIDER_CACHE) {
			final var c = OIDC_PROVIDER_CACHE.get(issuer);
			if (c == null)
				OIDC_PROVIDER_CACHE.put(issuer, cached = new CachedOidcProviderMetadata());
			else
				cached = c;
		}

		if (cached.lastUpdate == null //
				|| Duration.between(cached.lastUpdate, Instant.now()).compareTo(config.getMetadataTtl()) > 0)
			try {
				final var adapter = IuJsonAdapter.adapt(IuOidcProviderMetadata.class,
						IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES);

				cached.instance = adapter.fromJson(IuHttp.get(config.getMetadataUri(), IuHttp.READ_JSON_OBJECT));

				cached.lastUpdate = Instant.now();
			} catch (Throwable e) {
				if (cached.instance == null)
					throw IuException.unchecked(e);
				else
					LOG.log(Level.INFO, e, () -> "OIDC provider metadata lookup failure " + config.getMetadataUri()
							+ "; using last good version");
			}

		return cached.instance;
	}

	private OidcProviders() {
	}

}
