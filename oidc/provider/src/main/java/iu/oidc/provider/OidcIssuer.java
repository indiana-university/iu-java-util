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
package iu.oidc.provider;

import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;

import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.oidc.config.IuOidcProviderConfiguration;

/**
 * This provider's own identity: what it publishes as its issuer, and the keys
 * it signs with.
 *
 * <p>
 * Every endpoint reads configuration through one of these rather than holding a
 * configuration of its own, so they cannot disagree about who this provider is.
 * The configuration arrives as a {@link Supplier} and is read on every call, so
 * a change takes effect on the next request rather than the next deployment
 * &mdash; a supplier over a caching configuration source is what bounds how
 * often that read actually costs anything.
 * </p>
 *
 * <h2>Selecting a key</h2>
 *
 * <p>
 * A signing algorithm selects the key, and the key then determines how what it
 * signs is signed. The two are not the same step: a client endpoint registers
 * the algorithm an ID token issued to it must be signed with, which is what
 * {@link #issuerKey(Algorithm)} answers a key for, while
 * {@link OidcJose#sign(String, String, WebKey) signing} reads the algorithm
 * back off the key it was handed. Keeping selection here means nothing is ever
 * signed with an algorithm no published key could verify.
 * </p>
 */
public class OidcIssuer {

	private final Supplier<IuOidcProviderConfiguration> configuration;

	/**
	 * Binds a provider to its configuration.
	 *
	 * @param configuration supplies this provider's configuration, read afresh on
	 *                      each use
	 */
	public OidcIssuer(Supplier<IuOidcProviderConfiguration> configuration) {
		this.configuration = Objects.requireNonNull(configuration, "Missing provider configuration");
	}

	/**
	 * Reads this provider's configuration.
	 *
	 * @return {@link IuOidcProviderConfiguration}
	 * @throws NullPointerException if the supplier answers nothing
	 */
	public IuOidcProviderConfiguration configuration() {
		return Objects.requireNonNull(configuration.get(), "Missing provider configuration");
	}

	/**
	 * Reads this provider's discovery metadata, with the endpoints and signing
	 * algorithms it determines for itself in place of anything configured for them.
	 *
	 * @return {@link OidcProviderMetadata}
	 */
	public OidcProviderMetadata metadata() {
		return new OidcProviderMetadata(configuration());
	}

	/**
	 * Names this deployment's issuer identifier.
	 *
	 * @return issuer URI
	 * @throws NullPointerException if the configured metadata declares no issuer
	 */
	public URI issuer() {
		return Objects.requireNonNull(metadata().getIssuer(), "Missing issuer");
	}

	/**
	 * Names one of this provider's own endpoints.
	 *
	 * <p>
	 * Built from the issuer by the same rule the discovery document advertises
	 * endpoints with, so the URI a relying party discovers and the URI this
	 * provider acts on are the same.
	 * </p>
	 *
	 * @param path endpoint path, or the empty string for the issuer itself
	 * @return endpoint URI
	 * @throws NullPointerException if the configured metadata declares no issuer
	 */
	public URI endpointUri(String path) {
		return OidcProviderMetadata.endpointUri(metadata(), path);
	}

	/**
	 * Gets the key this provider signs with by default, which is the first signing
	 * key configured.
	 *
	 * @return issuer signing key
	 * @throws IllegalStateException if no configured key can sign
	 */
	public WebKey issuerKey() {
		return issuerKey(null);
	}

	/**
	 * Gets a key this provider signs with.
	 *
	 * <p>
	 * A key with no {@code alg}, or one whose algorithm is for encryption, is never
	 * selected: it could not sign, and the discovery document doesn't advertise it.
	 * That makes the algorithms a relying party is told about and the keys
	 * available to sign with the same set.
	 * </p>
	 *
	 * @param algorithm required signature algorithm, or {@code null} for the first
	 *                  signing key configured
	 * @return issuer signing key
	 * @throws IllegalStateException if no configured key can sign with
	 *                               {@code algorithm}
	 */
	public WebKey issuerKey(Algorithm algorithm) {
		final var jwks = configuration().getJwks();
		if (jwks != null)
			for (final var jwk : jwks) {
				if (jwk == null)
					continue;

				final var keyAlgorithm = jwk.getAlgorithm();
				if (keyAlgorithm != null //
						&& Use.SIGN.equals(keyAlgorithm.use) //
						&& (algorithm == null || algorithm.equals(keyAlgorithm)))
					return jwk;
			}

		throw new IllegalStateException("No issuer signing key" + (algorithm == null ? "" : " for " + algorithm.alg));
	}

}
