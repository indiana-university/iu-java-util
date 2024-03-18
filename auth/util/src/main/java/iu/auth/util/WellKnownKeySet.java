/*
 * Copyright © 2024 Indiana University
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
package iu.auth.util;

import static iu.auth.util.JwksUtils.toECPublicKey;
import static iu.auth.util.JwksUtils.toRSAPublicKey;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.auth0.jwt.algorithms.Algorithm;

import jakarta.json.JsonObject;

/**
 * Provides cached algorithm configurations from a well-known JWKS key set.
 */
public class WellKnownKeySet implements AlgorithmFactory {

	private static final Logger LOG = Logger.getLogger(WellKnownKeySet.class.getName());

	private static class CachedAlgorithm {
		private final Algorithm algorithm;
		private final Instant lastUpdated;

		private CachedAlgorithm(Algorithm algorithm, Instant lastUpdated) {
			this.algorithm = algorithm;
			this.lastUpdated = lastUpdated;
		}
	}

	private final URI keysetUri;
	private final Supplier<Duration> refreshInterval;
	private final Map<AlgorithmKey, CachedAlgorithm> algorithmCache = new HashMap<>();

	/**
	 * Constructor.
	 * 
	 * @param keysetUri       well-known keyset URI
	 * @param refreshInterval cache time to live
	 */
	public WellKnownKeySet(URI keysetUri, Supplier<Duration> refreshInterval) {
		this.keysetUri = keysetUri;
		this.refreshInterval = refreshInterval;
	}

	@Override
	@SuppressWarnings("exports")
	public Algorithm getAlgorithm(String kid, String alg) {
		final var now = Instant.now();
		final var cacheKey = new AlgorithmKey(kid, alg);

		var cachedAlgorithm = algorithmCache.get(cacheKey);
		if (cachedAlgorithm == null || cachedAlgorithm.lastUpdated.isBefore(now.minus(refreshInterval.get()))) {
			JsonObject jwk = null;
			Algorithm jwtAlgorithm;
			try {
				jwk = readJwk(kid);
				switch (alg) {
				case "ES256":
					jwtAlgorithm = Algorithm.ECDSA256(toECPublicKey(jwk));
					break;
				case "ES384":
					jwtAlgorithm = Algorithm.ECDSA384(toECPublicKey(jwk));
					break;
				case "ES512":
					jwtAlgorithm = Algorithm.ECDSA512(toECPublicKey(jwk));
					break;
				case "RS256":
					jwtAlgorithm = Algorithm.RSA256(toRSAPublicKey(jwk), null);
					break;
				case "RS384":
					jwtAlgorithm = Algorithm.RSA384(toRSAPublicKey(jwk), null);
					break;
				case "RS512":
					jwtAlgorithm = Algorithm.RSA512(toRSAPublicKey(jwk), null);
					break;
				default:
					throw new UnsupportedOperationException("Unsupported JWT algorithm " + alg);
				}

				cachedAlgorithm = new CachedAlgorithm(jwtAlgorithm, now);
				synchronized (algorithmCache) {
					algorithmCache.put(cacheKey, cachedAlgorithm);
				}

			} catch (Throwable e) {
				final var message = "JWT Algorithm initialization failure; keysetUri=" + keysetUri + " " + cacheKey
						+ " jwk=" + jwk;
				if (cachedAlgorithm == null)
					throw new IllegalStateException(message, e);
				else
					LOG.log(Level.INFO, message, e);
			}
		}
		return cachedAlgorithm.algorithm;
	}

	private JsonObject readJwk(String keyId) {
		final var jwks = HttpUtils.read(keysetUri).asJsonObject();
		try {
			for (final var key : jwks.getJsonArray("keys")) {
				final var keyAsJsonObject = key.asJsonObject();
				if (keyId.equals(keyAsJsonObject.getString("kid")))
					return keyAsJsonObject;
			}
		} catch (Throwable e) {
			throw new IllegalStateException("Invalid JWKS format: " + jwks, e);
		}

		throw new IllegalStateException("Key " + keyId + " not in JWKS: " + jwks);
	}

}
