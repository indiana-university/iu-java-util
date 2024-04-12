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

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuJwtSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Represents a JWT access token.
 */
public interface IuWebToken extends IuPrincipalIdentity {

	/**
	 * Creates new JWTs.
	 */
	interface Builder {

		/**
		 * Selects a token key by ID.
		 * 
		 * @param keyId claim value; <em>must</em> correlate to a private key configured
		 *              for a service provider
		 * @return this
		 */
		Builder keyId(String keyId);

		/**
		 * Imports principal claims and signature/encryption credentials from a
		 * {@link Subject}.
		 * 
		 * <p>
		 * The {@link Principal} types below are supported by implementation-defined
		 * claim values and recoverable by {@link IuWebToken#asSubject()}.
		 * </p>
		 * <ul>
		 * <li>{@link IuPrincipalIdentity}</li>
		 * <li>{@link IuApiCredentials}</li>
		 * <li>{@link X500Principal}</li>
		 * </ul>
		 * 
		 * @param subject subject
		 * @return this
		 */
		Builder subject(Subject subject);

		/**
		 * Sets the audience.
		 * 
		 * @param audience Audience principals to append; the common name element of the
		 *                 principal name <em>should</em> be a username or application
		 *                 URI
		 * @return this
		 */
		Builder audience(Principal... audience);

		/**
		 * Sets the session time-to-live for the JWT.
		 * 
		 * @param ttl session time to live
		 * @return this
		 */
		Builder expiresIn(Duration ttl);

		/**
		 * Sets an opaque identifier received via authorization redirect or
		 * authentication challenge attribute.
		 * 
		 * @param nonce opaque challenge id
		 * @return this
		 */
		Builder nonce(String nonce);

		/**
		 * Sets an extended claim value.
		 * 
		 * @param <T>   value type
		 * @param name  claim name
		 * @param value value
		 * @return this
		 * @see <a href="https://www.iana.org/assignments/jwt/jwt.xhtml">IANA JWT Public
		 *      Claim Registry</a>
		 */
		<T> Builder claim(String name, T value);

		/**
		 * Creates JWT.
		 * 
		 * @return verified, signed, and/or encrypted JWT
		 */
		IuWebToken build();
	}

	/**
	 * Parses a JWT
	 * 
	 * @param jwt compact JWS, JWE, or JWS+JWE representation of the token
	 * @return parsed token
	 */
	static IuWebToken parse(String jwt) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).parse(jwt);
	}

	/**
	 * Issues a new JWT
	 * 
	 * @param issuer token issuer common or full principal name
	 * @return {@link Builder}
	 */
	static Builder issue(String issuer) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).issue(issuer);
	}
	
	/**
	 * Get a unique identifier for the JWT.
	 * 
	 * @return jti claim value
	 */
	String getTokenId();

	/**
	 * Gets the common name of the principal that issued the JWT.
	 * 
	 * @return iss claim value; <em>should</em> be a username or application URI
	 */
	Principal getIssuer();

	/**
	 * Gets the common name of the principal that is the subject of the JWT.
	 * 
	 * @return sub claim value; <em>should</em> be a username or application URI
	 */
	Subject getSubject();

	/**
	 * Gets the common names of each principals intended as a recipient of the JWT.
	 * 
	 * @return sub claim value; <em>should</em> be a username or application URI
	 */
	Iterable<Principal> getAudience();

	/**
	 * Gets the time at which the JWT was issued.
	 *
	 * @return iat claim value
	 */
	Instant getIssuedAt();

	/**
	 * Gets the time before which the JWT <em>must not</em> be accepted for
	 * processing.
	 * 
	 * @return nbf claim value
	 */
	Instant getNotBefore();

	/**
	 * Gets the time after which the JWT <em>must not</em> be accepted for
	 * processing.
	 * 
	 * @return exp claim value
	 */
	Instant getExpires();

	/**
	 * Gets a unique identifier as generated when initiating authorization redirect
	 * or provided by authentication challenge attribute .
	 * 
	 * @return nonce claim value
	 */
	String getNonce();

	/**
	 * Gets an extended claim value by name.
	 * 
	 * @param <T>  value type
	 * @param name claim name
	 * @return claim value
	 */
	<T> T getClaim(String name);

}
