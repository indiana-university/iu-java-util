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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.security.auth.Subject;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuJwtSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Provides JWT issue metadata.
 */
public interface IuWebTokenIssuer {

	/**
	 * Defines additional claim types not covered by RFC-7519.
	 * 
	 * @param <T> claim type
	 */
	interface ClaimDefinition<T> {
		/**
		 * Gets the claim name.
		 * 
		 * @return claim name
		 */
		String name();

		/**
		 * Converts from serialized form.
		 * 
		 * @param serialized serialized claim value; instance of or toString() parseable
		 *                   as jakarta.json/jakarta.json.JsonValue
		 * @return deserialized claim value
		 */
		T from(Object serialized);

		/**
		 * Converts to serialized form
		 * 
		 * @param value claim value
		 * @return serialized form of claim value
		 */
		Object to(T value);

		/**
		 * Verifies a JWT relative to the claim.
		 * 
		 * @param token JWT that includes the claim
		 * @throws IllegalArgumentException if the token is invalid according to its
		 *                                  claim values
		 */
		void verify(IuWebToken token);
	}

	/**
	 * Defines an extended JWT claim with RFC-7519 compliant StringOrURI type
	 * conversion semantics.
	 * 
	 * @param verifier token verifier
	 * @return materialized claim definition
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JWT</a>
	 */
	static ClaimDefinition<String> stringOrUri(Consumer<IuWebToken> verifier) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).stringOrUri(verifier);
	}

	/**
	 * Defines an extended JWT claim with RFC-7519 compliant NumericDate type
	 * conversion semantics.
	 * 
	 * @param verifier token verifier
	 * @return materialized claim definition
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JWT</a>
	 */
	static ClaimDefinition<Instant> numericDate(Consumer<IuWebToken> verifier) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).numericDate(verifier);
	}

	/**
	 * Defines an extended JWT claim using an SPI-defined type adapter,
	 * <em>optional</em> value definition for programmatic equivalence to
	 * {@link ParameterizedType}, and custom verification logic.
	 * 
	 * @param <T>      value type
	 * @param type     value type
	 * @param verifier receives an adapted value and parsed JWT reference;
	 *                 <em>may</em> throw {@link IllegalArgumentException} to
	 *                 indicate verification has failed.
	 * @oaramn valueDefinition
	 * @return materialized claim definition
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JWT</a>
	 */
	static <T> ClaimDefinition<T> extend(Class<T> type, BiConsumer<T, IuWebToken> verifier) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).claimDefinition(type, null, verifier);
	}

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
	 * @return materialized claim definition
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC-7519 JWT</a>
	 */
	static <T> ClaimDefinition<T> extend(Type type, ClaimDefinition<?> valueDefinition,
			BiConsumer<T, IuWebToken> verifier) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).claimDefinition(type, valueDefinition, verifier);
	}

	/**
	 * Registers a JWT issuer.
	 * 
	 * <p>
	 * Only one JWT issuer may be registered per issuer principal.
	 * </p>
	 * 
	 * @param issuer token issuer metadata
	 */
	static void register(IuWebTokenIssuer issuer) {
		IuAuthSpiFactory.get(IuJwtSpi.class).register(issuer);
	}

	/**
	 * Gets issuer principal name and credentials.
	 * 
	 * @return {@link Subject}
	 */
	IuPrincipalIdentity getIssuer();

	/**
	 * Gets extended JWT claim definitions.
	 * 
	 * @return claim definitions
	 */
	Iterable<? extends ClaimDefinition<?>> getClaimDefinitions();

	/**
	 * Gets the maximum time to live for tokens.
	 * 
	 * @return {@link Duration}
	 */
	Duration getMaxExpiresIn();

}
