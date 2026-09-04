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

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

import edu.iu.crypt.WebKey;
import edu.iu.pki.IuCertificateAuthority;

/**
 * Establishes how one client endpoint authenticates itself.
 *
 * <p>
 * Extends {@link IuCertificateAuthority} so the client's own key can be
 * verified against a certificate chain and revocation list: a client that
 * authenticates with a signed assertion is trusted because its certificate is,
 * not because a shared secret matched.
 * </p>
 *
 * <p>
 * Registration is dated rather than merely present. {@link #getCreated()} and
 * {@link #getUpdated()} record when the record was written,
 * {@link #getExpires()} when it stops being honored, and
 * {@link #getAssertionTtl()} bounds how long any one assertion the client
 * presents may live &mdash; so a leaked assertion is useful for minutes rather
 * than indefinitely.
 * </p>
 *
 * <p>
 * Property names use lower case with underscores, so {@link #getAssertionTtl()}
 * reads {@code assertion_ttl}.
 * </p>
 */
public interface OidcClientAuthorization extends IuCertificateAuthority {

	/**
	 * Returns the client's signing certificate, from the first entry in the
	 * {@link #getJwk() client key}'s certificate chain.
	 *
	 * @return signing certificate; null if jwk is null or doesn't include at least
	 *         one certificate. MUST be non-null when crl is non-null
	 */
	@Override
	default X509Certificate getCertificate() {
		final var jwk = getJwk();
		if (jwk == null)
			return null;

		final var certificateChain = jwk.getCertificateChain();
		if (certificateChain == null || certificateChain.length == 0)
			return null;

		return certificateChain[0];
	}

	/**
	 * Returns the client's JSON Web Key.
	 *
	 * @return client JSON Web Key
	 */
	WebKey getJwk();

	/**
	 * Returns when this client record was created.
	 *
	 * @return creation time
	 */
	Instant getCreated();

	/**
	 * Returns when this client record was last modified.
	 *
	 * @return last modification time
	 */
	Instant getUpdated();

	/**
	 * Returns when this client record expires.
	 *
	 * @return expiration time, or {@code null} when the record does not expire
	 */
	Instant getExpires();

	/**
	 * Returns the maximum permitted lifetime of a client assertion token, measured
	 * from its {@code iat} claim to its {@code exp} claim.
	 *
	 * @return maximum client assertion lifetime; defaults to 15 minutes
	 */
	default Duration getAssertionTtl() {
		return Duration.ofMinutes(15L);
	}

}
