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
package edu.iu.oidc.config;

import java.time.Duration;

import edu.iu.crypt.WebKey;
import edu.iu.oidc.IuOidcProviderMetadata;

/**
 * Describes the OpenID Connect provider itself.
 *
 * <p>
 * Only what is true of the provider as a whole lives here. Each relying party
 * registered with it is described separately, by
 * {@link OidcClientConfiguration}.
 * </p>
 *
 * <p>
 * Property names use lower case with underscores, so
 * {@link #getAuthorizationCodeTimeToLive()} reads
 * {@code authorization_code_time_to_live}.
 * </p>
 */
public interface OidcProviderConfiguration {

	/**
	 * Gets the discovery metadata this provider publishes, and reads its own
	 * endpoint URIs back out of.
	 *
	 * <p>
	 * The document is published verbatim, so it is both what a relying party
	 * discovers and what the provider honors: the
	 * {@link IuOidcProviderMetadata#getIssuer() issuer} it advertises is the base
	 * URI every one of this provider's own endpoints is built from. Advertising an
	 * issuer the deployment isn't reachable at breaks authentication, not just
	 * discovery.
	 * </p>
	 *
	 * @return {@link IuOidcProviderMetadata}
	 */
	IuOidcProviderMetadata getMetadata();

	/**
	 * Gets the provider's own keys, which sign what it issues.
	 *
	 * <p>
	 * A key's {@code alg} is what selects it: the provider picks a signing key by
	 * algorithm, and the signature algorithms the discovery document advertises are
	 * derived from these keys, so nothing is published that no key can sign with.
	 * Keys are listed in preference order &mdash; the first signing key is the one
	 * used when no particular algorithm is called for.
	 * </p>
	 *
	 * @return issuer keys
	 */
	Iterable<WebKey> getJwks();

	/**
	 * Gets how long an issued authorization code remains redeemable.
	 *
	 * @return {@link Duration}; one minute by default, the longest OAuth 2.0
	 *         recommends
	 */
	default Duration getAuthorizationCodeTimeToLive() {
		return Duration.ofMinutes(1L);
	}

	/**
	 * Gets how long an issued access token remains valid.
	 *
	 * <p>
	 * A resource server bounds this independently &mdash; it may refuse a bearer
	 * token whose {@code iat} to {@code exp} span exceeds what the resource allows
	 * &mdash; so a value longer than the resource permits yields a token that
	 * verifies here and is refused there.
	 * </p>
	 *
	 * @return {@link Duration}; fifteen minutes by default
	 */
	default Duration getAccessTokenTimeToLive() {
		return Duration.ofMinutes(15L);
	}

	/**
	 * Gets how long an issued refresh token remains redeemable.
	 *
	 * <p>
	 * This bounds how long a client can keep obtaining access tokens without the end
	 * user returning to the identity provider, so it should not outlast the
	 * authentication it descends from.
	 * </p>
	 *
	 * @return {@link Duration}; twelve hours by default
	 */
	default Duration getRefreshTokenTimeToLive() {
		return Duration.ofHours(12L);
	}

}
