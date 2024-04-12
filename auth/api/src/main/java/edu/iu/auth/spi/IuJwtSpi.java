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
package edu.iu.auth.spi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.jwt.IuWebToken.Builder;
import edu.iu.auth.jwt.IuWebTokenIssuer;
import edu.iu.auth.jwt.IuWebTokenIssuer.ClaimDefinition;

/**
 * Service provider interface supporting {@link IuWebToken}.
 */
public interface IuJwtSpi {

//	   The contents of the JOSE Header describe the cryptographic operations
//	   applied to the JWT Claims Set.  If the JOSE Header is for a JWS, the
//	   JWT is represented as a JWS and the claims are digitally signed or
//	   MACed, with the JWT Claims Set being the JWS Payload.  If the JOSE
//	   Header is for a JWE, the JWT is represented as a JWE and the claims
//	   are encrypted, with the JWT Claims Set being the plaintext encrypted
//	   by the JWE.  A JWT may be enclosed in another JWE or JWS structure to
//	   create a Nested JWT, enabling nested signing and encryption to be
//	   performed.
//
//	   A JWT is represented as a sequence of URL-safe parts separated by
//	   period ('.') characters.  Each part contains a base64url-encoded
//	   value.  The number of parts in the JWT is dependent upon the
//	   representation of the resulting JWS using the JWS Compact
//	   Serialization or JWE using the JWE Compact Serialization.
	/**
	 * Parses a JSON Web Token.
	 * 
	 * <p>
	 * NOTICE: This method does not verify the JWT.
	 * <p>
	 * 
	 * @param jwt serialized JWT
	 * @return parsed JWT
	 */
	IuWebToken parse(String jwt);

	/**
	 * Registers a JWT issuer.
	 * 
	 * <p>
	 * Only one JWT issuer may be registered per issuer principal.
	 * </p>
	 * 
	 * @param issuer token issuer metadata
	 */
	void register(IuWebTokenIssuer issuer);

	/**
	 * Defines an extended JWT claim with RFC-7519 compliant StringOrURI type
	 * conversion semantics.
	 * 
	 * @param verifier token verifier
	 * @return materialized claim definition for use with
	 *         {@link #defineClaim(String, ClaimDefinition)}
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JWT</a>
	 */
	ClaimDefinition<String> stringOrUri(Consumer<IuWebToken> verifier);

	/**
	 * Defines an extended JWT claim with RFC-7519 compliant NumericDate type
	 * conversion semantics.
	 * 
	 * @param verifier token verifier
	 * @return materialized claim definition for use with
	 *         {@link #defineClaim(String, ClaimDefinition)}
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JWT</a>
	 */
	ClaimDefinition<Instant> numericDate(Consumer<IuWebToken> verifier);

	/**
	 * Defines an extended JWT claim using an SPI-defined type adapter,
	 * <em>optional</em> value definition for programmatic equivalence to
	 * {@link ParameterizedType}, and custom verification logic.
	 * 
	 * @param type            generic value type
	 * @param valueDefinition value definition for use when type is a class with
	 *                        type variables and {@link ParameterizedType} is not
	 *                        known or does't carry sufficient metadata to adapt
	 *                        related values {e.g. elements in a
	 *                        {@link Collection}); may be null
	 * @param verifier        receives an adapted value and parsed JWT reference;
	 *                        <em>may</em> throw {@link IllegalArgumentException} to
	 *                        indicate verification has failed.
	 * @oaramn valueDefinition
	 * @return materialized claim definition for use with
	 *         {@link #defineClaim(String, ClaimDefinition)}
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JWT</a>
	 */
	<T> ClaimDefinition<T> claimDefinition(Type type, ClaimDefinition<?> valueDefinition,
			BiConsumer<T, IuWebToken> verifier);

	/**
	 * Creates a JWT builder for a registered issuer.
	 * 
	 * @param issuer issuer; <em>must</em> be {@link #register(IuWebTokenIssuer)
	 *               registered}
	 * @return {@link Builder}
	 */
	Builder issue(String issuer);

}
