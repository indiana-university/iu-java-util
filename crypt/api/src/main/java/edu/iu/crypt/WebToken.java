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
import java.time.Duration;

import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Represents a JSON Web Token (JWT).
 */
public interface WebToken extends WebTokenClaims {

	/**
	 * Gets a mutable {@link WebTokenBuilder} instance.
	 * 
	 * @return {@link WebTokenBuilder}
	 */
	static WebTokenBuilder builder() {
		return Init.SPI.getJwtBuilder();
	}

	/**
	 * Verifies a signed JSON Web Token (JWT).
	 * 
	 * @param jwt       Signed JWT
	 * @param issuerKey Public key of the token issuer
	 * @return {@link WebToken}
	 */
	static WebToken verify(String jwt, WebKey issuerKey) {
		return Init.SPI.verifyJwt(jwt, issuerKey);
	}

	/**
	 * Decrypts and verifies a signed JSON Web Token (JWT).
	 * 
	 * @param jwt         Signed JWT
	 * @param issuerKey   Public key of the token issuer
	 * @param audienceKey Public key of the token audience
	 * @return {@link WebToken}
	 */
	static WebToken decryptAndVerify(String jwt, WebKey issuerKey, WebKey audienceKey) {
		return Init.SPI.decryptAndVerifyJwt(jwt, issuerKey, audienceKey);
	}

	/**
	 * Determines if the token has expired.
	 * 
	 * @return true if {@link #getExpires()} is in the past
	 */
	boolean isExpired();

	/**
	 * Verify JWT registered claims are well-formed and within the allowed time
	 * window.
	 * 
	 * <p>
	 * In addition to the rules outlined in
	 * <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1">RFC-7519
	 * JWT Section 4.1</a>, REQUIRES the following claim values to be present and
	 * not empty:
	 * </p>
	 * <ul>
	 * <li>{@link #getIssuer()}</li>
	 * <li>{@link #getAudience()}</li>
	 * <li>{@link #getSubject()}</li>
	 * <li>{@link #getIssuedAt()}</li>
	 * <li>{@link #getExpires()}</li>
	 * </ul>
	 * 
	 * @param audience Expected audience {@link URI}
	 * @param ttl      Maximum assertion time to live allowed by configuration
	 */
	void validateClaims(URI audience, Duration ttl);

	/**
	 * Encodes all claims as a signed JSON Web Token
	 * 
	 * @param algorithm signature algorithm
	 * @param issuerKey issuer key
	 * @return Signed JWT
	 */
	String sign(Algorithm algorithm, WebKey issuerKey);

	/**
	 * Encodes all claims as a signed and encrypted JSON Web Token.
	 * 
	 * @param signAlgorithm    signature algorithm
	 * @param issuerKey        issuer key
	 * @param encryptAlgorithm key protection algorithm
	 * @param encryption       content encryption algorithm
	 * @param audienceKey      audience key
	 * @return Signed and encrypted JWT
	 */
	String signAndEncrypt(Algorithm signAlgorithm, WebKey issuerKey, Algorithm encryptAlgorithm, Encryption encryption,
			WebKey audienceKey);

}