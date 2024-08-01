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
package edu.iu.auth.config;

import java.net.URI;
import java.time.Instant;

import edu.iu.auth.jwt.IuWebToken;

/**
 * Adds setters to {@link IuWebToken}
 */
public interface IuWebTokenBuilder extends IuWebToken {

	/**
	 * Sets the token identifier.
	 * 
	 * @param jti token identifier (jti claim);
	 */
	void setTokenId(String jti);

	/**
	 * Sets the token issuer URI.
	 * 
	 * @param iss {@link URI}
	 */
	void setIssuer(URI iss);

	/**
	 * Sets the token audience URIs.
	 * 
	 * @param aud at least one {@link URI}
	 */
	void setAudience(Iterable<URI> aud);

	/**
	 * Sets the subject of the JWT.
	 * 
	 * @param sub subject (sub claim)
	 */
	void setSubject(String sub);

	/**
	 * Sets the time the JWT was issued.
	 * 
	 * @param iat issued time (iat claim)
	 */
	void setIssuedAt(Instant iat);

	/**
	 * Sets the time before which the JWT should not be accepted.
	 * 
	 * @param nbf not before time (nbf claim)
	 */
	void setNotBefore(Instant nbf);

	/**
	 * Sets the time after which the JWT should not be accepted.
	 * 
	 * @param exp token expiration time (exp claim)
	 */
	void setExpires(Instant exp);

	/**
	 * Sets the nonce claim.
	 * 
	 * @param nonce nonce claim value
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes">OpenID
	 *      Connection Core 1.0 Section 15.5.2</a>
	 */
	void setNonce(String nonce);

}
