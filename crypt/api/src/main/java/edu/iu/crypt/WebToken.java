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
package edu.iu.crypt;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;

/**
 * Represents a JSON Web Token (JWT).
 */
public interface WebToken {

	/**
	 * Gets a mutable {@link WebTokenBuilder} instance.
	 * 
	 * @return {@link WebTokenBuilder}
	 */
	static WebTokenBuilder builder() {
		return Init.SPI.getJwtBuilder();
	}

	/**
	 * Convenience method to determine if a JWT is encrypted.
	 * 
	 * @param jwt JWT
	 * @return true if the JWT is encrypted; else false
	 */
	static boolean isEncrypted(String jwt) {
		return WebCryptoHeader.getProtectedHeader(jwt).getAlgorithm().use.equals(Use.ENCRYPT);
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
	 * @param type      token type; e.g., "JWT"
	 * @param algorithm signature algorithm
	 * @param issuerKey issuer key
	 * @return Signed JWT
	 */
	String sign(String type, Algorithm algorithm, WebKey issuerKey);

	/**
	 * Encodes all claims as a signed and encrypted JSON Web Token.
	 * 
	 * @param type             token type; e.g., "JWT"
	 * @param signAlgorithm    signature algorithm
	 * @param issuerKey        issuer key
	 * @param encryptAlgorithm key protection algorithm
	 * @param encryption       content encryption algorithm
	 * @param audienceKey      audience key
	 * @return Signed and encrypted JWT
	 */
	String signAndEncrypt(String type, Algorithm signAlgorithm, WebKey issuerKey, Algorithm encryptAlgorithm,
			Encryption encryption, WebKey audienceKey);

}