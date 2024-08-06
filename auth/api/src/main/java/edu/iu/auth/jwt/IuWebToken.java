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
package edu.iu.auth.jwt;

import java.net.URI;
import java.time.Instant;

/**
 * Represents JSON Web Token (JWT) claims.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JSON
 *      Web Token (JWT)</a>
 */
public interface IuWebToken {

	/**
	 * Gets the token identifier.
	 * 
	 * @return token identifier (jti claim);
	 */
	String getTokenId();

	/**
	 * Gets the token issuer URI.
	 * 
	 * @return {@link URI}
	 */
	URI getIssuer();

	/**
	 * Gets the token audience URIs.
	 * 
	 * @return at least one {@link URI}
	 */
	Iterable<URI> getAudience();

	/**
	 * Gets the subject of the JWT.
	 * 
	 * @return subject (sub claim)
	 */
	String getSubject();

	/**
	 * Gets the time the JWT was issued.
	 * 
	 * @return issued time (iat claim)
	 */
	Instant getIssuedAt();

	/**
	 * Gets the time before which the JWT should not be accepted.
	 * 
	 * @return not before time (nbf claim)
	 */
	Instant getNotBefore();

	/**
	 * Gets the time after which the JWT should not be accepted.
	 * 
	 * @return token expiration time (exp claim)
	 */
	Instant getExpires();

	/**
	 * Gets the nonce claim.
	 * 
	 * @return nonce claim value
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes">OpenID
	 *      Connection Core 1.0 Section 15.5.2</a>
	 */
	String getNonce();

}
