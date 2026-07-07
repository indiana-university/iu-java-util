/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
	 * @return {@link IuOidcProviderMetadata}
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
