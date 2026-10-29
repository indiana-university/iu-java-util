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

import java.security.cert.X509CRL;
import java.time.Duration;
import java.time.Instant;

import edu.iu.crypt.WebKey;

/**
 * Establishes how one client endpoint authenticates itself.
 *
 * <p>
 * A registration supplies key material and, when it represents a certificate
 * authority, its revocation lists. The provider adapts those values to its PKI
 * verifier when it verifies a client assertion; configuration does not expose a
 * PKI contract itself.
 * </p>
 */
public interface IuOidcClientAuthorization {

	/**
	 * Gets a description of this client authorization.
	 * 
	 * @return description
	 */
	String getDescr();

	/**
	 * Returns the client's JSON Web Key.
	 *
	 * @return client JSON Web Key
	 */
	WebKey getJwk();

	/**
	 * Gets the revocation lists registered with the client signing key.
	 *
	 * <p>
	 * A non-empty result makes this registration a certificate authority. The
	 * provider then verifies an assertion's certificate chain and checks these
	 * lists; a registration without a list verifies directly with its key.
	 * </p>
	 *
	 * @return CRLs, or {@code null} if this registration has none
	 */
	Iterable<X509CRL> getCrl();

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
