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
package edu.iu.crypt;

import java.net.URI;
import java.time.Instant;

/**
 * Represents JSON Web Token (JWT) claims.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JSON
 *      Web Token (JWT)</a>
 */
public interface WebTokenBuilder {

	/**
	 * Generates a unique token identifier.
	 * 
	 * @return this
	 */
	WebTokenBuilder jti();

	/**
	 * Provides a specific token identifier.
	 * 
	 * @param tokenId token identifier
	 * @return this
	 */
	WebTokenBuilder jti(String tokenId);

	/**
	 * Sets the token issuer URI.
	 * 
	 * @param issuer {@link URI}
	 * @return this
	 */
	WebTokenBuilder iss(URI issuer);

	/**
	 * Sets the token audience URIs.
	 * 
	 * @param audience at least one {@link URI}
	 * @return this
	 */
	WebTokenBuilder aud(URI... audience);

	/**
	 * Sets the subject of the JWT.
	 * 
	 * @param subject (sub claim)
	 * @return this
	 */
	WebTokenBuilder sub(String subject);

	/**
	 * Indicates that {@link #build()} should set the issued at time to the current
	 * time.
	 * 
	 * @return this
	 */
	WebTokenBuilder iat();

	/**
	 * Sets the time before which the JWT should not be accepted.
	 * 
	 * @param notBefore not before time (nbf claim)
	 * @return this
	 */
	WebTokenBuilder nbf(Instant notBefore);

	/**
	 * Sets the time after which the JWT should not be accepted.
	 * 
	 * @param expires token expiration time (exp claim)
	 * @return this
	 */
	WebTokenBuilder exp(Instant expires);

	/**
	 * Sets the nonce claim.
	 * 
	 * @param nonce nonce claim value
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes">OpenID
	 *      Connection Core 1.0 Section 15.5.2</a>
	 * @return this
	 */
	WebTokenBuilder nonce(String nonce);

	/**
	 * Builds a {@link WebToken} instance based on provided claim values.
	 * 
	 * @return {@link WebToken}
	 */
	WebToken build();

}