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
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;

/**
 * Unifies algorithm support and maps cryptographic header data from JCE to JSON
 * Object Signing and Encryption (JOSE).
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">JSON Web Key
 *      (JWK) RFC-7517</a>
 */
public interface WebCryptoHeader extends WebCertificateReference {

	/**
	 * Enumerates standard header parameters.
	 */
	enum Param {
		/**
		 * Encryption/signature algorithm.
		 */
		ALGORITHM("alg", EnumSet.allOf(Use.class), true, WebCryptoHeader::getAlgorithm),

		/**
		 * Well-known key identifier.
		 */
		KEY_ID("kid", EnumSet.allOf(Use.class), false, WebCryptoHeader::getKeyId),

		/**
		 * Well-known key set URI.
		 */
		KEY_SET_URI("jku", EnumSet.allOf(Use.class), false, WebCryptoHeader::getKeySetUri),

		/**
		 * Well-known public key.
		 */
		KEY("jwk", EnumSet.allOf(Use.class), false, WebCryptoHeader::getKey),

		/**
		 * Certificate chain URI.
		 */
		CERTIFICATE_URI("x5u", EnumSet.allOf(Use.class), false, WebCryptoHeader::getCertificateUri),

		/**
		 * Certificate chain.
		 */
		CERTIFICATE_CHAIN("x5c", EnumSet.allOf(Use.class), false, WebCryptoHeader::getCertificateChain),

		/**
		 * Certificate SHA-1 thumb print.
		 */
		CERTIFICATE_THUMBPRINT("x5t", EnumSet.allOf(Use.class), false, WebCryptoHeader::getCertificateThumbprint),

		/**
		 * Certificate SHA-1 thumb print.
		 */
		CERTIFICATE_SHA256_THUMBPRINT("x5t#S256", EnumSet.allOf(Use.class), false,
				WebCryptoHeader::getCertificateSha256Thumbprint),

		/**
		 * Signature/encryption media type.
		 */
		TYPE("typ", EnumSet.allOf(Use.class), false, WebCryptoHeader::getType),

		/**
		 * Content media type.
		 */
		CONTENT_TYPE("cty", EnumSet.allOf(Use.class), false, WebCryptoHeader::getContentType),

		/**
		 * Extended parameter names that <em>must</em> be included in the protected
		 * header.
		 */
		CRITICAL_PARAMS("crit", EnumSet.allOf(Use.class), false, WebCryptoHeader::getCriticalParameters),

		/**
		 * Content encryption algorithm.
		 */
		ENCRYPTION("enc", EnumSet.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("enc")),

		/**
		 * Plain-text compression algorithm for encryption.
		 */
		ZIP("zip", EnumSet.of(Use.ENCRYPT), false, a -> a.getExtendedParameter("zip")),

		/**
		 * Ephemeral public key for key agreement algorithms.
		 * 
		 * @see Algorithm#ECDH_ES
		 * @see Algorithm#ECDH_ES_A128KW
		 * @see Algorithm#ECDH_ES_A192KW
		 * @see Algorithm#ECDH_ES_A256KW
		 */
		EPHEMERAL_PUBLIC_KEY("epk", EnumSet.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("epk")),

		/**
		 * Public originator identifier (PartyUInfo) for key derivation.
		 * 
		 * @see Algorithm#ECDH_ES
		 * @see Algorithm#ECDH_ES_A128KW
		 * @see Algorithm#ECDH_ES_A192KW
		 * @see Algorithm#ECDH_ES_A256KW
		 */
		PARTY_UINFO("apu", EnumSet.of(Use.ENCRYPT), false, a -> a.getExtendedParameter("apu")),

		/**
		 * Public recipient identifier (PartyVInfo) for key derivation.
		 * 
		 * @see Algorithm#ECDH_ES
		 * @see Algorithm#ECDH_ES_A128KW
		 * @see Algorithm#ECDH_ES_A192KW
		 * @see Algorithm#ECDH_ES_A256KW
		 */
		PARTY_VINFO("apv", EnumSet.of(Use.ENCRYPT), false, a -> a.getExtendedParameter("apv")),

		/**
		 * Initialization vector for GCM key wrap.
		 * 
		 * @see Algorithm#A128GCMKW
		 * @see Algorithm#A192GCMKW
		 * @see Algorithm#A256GCMKW
		 */
		INITIALIZATION_VECTOR("iv", EnumSet.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("iv")),

		/**
		 * Authentication tag for GCM key wrap.
		 * 
		 * @see Algorithm#A128GCMKW
		 * @see Algorithm#A192GCMKW
		 * @see Algorithm#A256GCMKW
		 */
		TAG("tag", Set.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("tag")),

		/**
		 * Password salt for use with PBES2.
		 * 
		 * @see Algorithm#PBES2_HS256_A128KW
		 * @see Algorithm#PBES2_HS384_A192KW
		 * @see Algorithm#PBES2_HS512_A256KW
		 */
		PASSWORD_SALT("p2s", Set.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("p2s")),

		/**
		 * PBKDF2 iteration count for use with PBES2.
		 * 
		 * @see Algorithm#PBES2_HS256_A128KW
		 * @see Algorithm#PBES2_HS384_A192KW
		 * @see Algorithm#PBES2_HS512_A256KW
		 */
		PASSWORD_COUNT("p2c", Set.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("p2c"));

		/**
		 * Gets a parameter by JOSE standard parameter name.
		 * 
		 * @param name JOSE standard name
		 * @return {@link Param}
		 */
		public static Param from(String name) {
			return EnumSet.allOf(Param.class).stream().filter(a -> IuObject.equals(name, a.name)).findFirst()
					.orElse(null);
		}

		/**
		 * JOSE standard parameter name.
		 */
		public final String name;

		/**
		 * Indicates if the parameter name is registered for use with <a href=
		 * "https://datatracker.ietf.org/doc/html/rfc7515#section-4.1">signature</a>
		 * and/or <a href=
		 * "https://datatracker.ietf.org/doc/html/rfc7515#section-4.1">encryption</a>.
		 */
		private final Set<Use> use;

		/**
		 * Indicates if the parameter is required for this algorithm.
		 */
		public final boolean required;

		private final Function<WebCryptoHeader, ?> get;

		private Param(String name, Set<Use> use, boolean required, Function<WebCryptoHeader, ?> get) {
			this.name = name;
			this.use = Collections.unmodifiableSet(use);
			this.required = required;
			this.get = get;
		}

		/**
		 * Verifies that the header contains a non-null value.
		 * 
		 * @param header header
		 * @return true if the value is present; else false
		 */
		public boolean isPresent(WebCryptoHeader header) {
			return get(header) != null;
		}

		/**
		 * Determines if a header applies to a public JWK use case.
		 * 
		 * @param use public key use
		 * @return true if the header parameter is registered for the public JWK use
		 *         case.
		 */
		public boolean isUsedFor(Use use) {
			return this.use.contains(use);
		}

		/**
		 * Gets the header value.
		 * 
		 * @param header header
		 * @return header value
		 */
		public Object get(WebCryptoHeader header) {
			return get.apply(header);
		}
	}

	/**
	 * Builder interface for creating {@link WebCryptoHeader} instances.
	 * 
	 * @param <B> builder type
	 */
	interface Builder<B extends Builder<B>> {
		/**
		 * Sets the key ID relative to {@link #getKeySetUri()} corresponding to a JWKS
		 * key entry.
		 *
		 * @param keyId key ID
		 * @return this
		 */
		B keyId(String keyId);

		/**
		 * Sets the URI where JWKS well-known key data can be retrieved.
		 * 
		 * @param uri JWKS {@link URI}
		 * @return this
		 */
		B wellKnown(URI uri);

		/**
		 * Sets the key to include with the header.
		 * 
		 * @param key <em>may</em> include private/secret key data to use for
		 *            encryption/signing; only {@link WebKey#wellKnown()} will be
		 *            included in the header.
		 * @return this
		 */
		B wellKnown(WebKey key);

		/**
		 * The key to use for encrypting or signing.
		 * 
		 * @param key key to use for encryption or signing; will not be included in the
		 *            header. Use {@link #wellKnown(WebKey)} to set the key and include
		 *            well-known component in the header.
		 * @return this
		 */
		B key(WebKey key);

		/**
		 * Sets the header type parameter value.
		 * 
		 * @param type header type parameter value.
		 * @return this
		 */
		B type(String type);

		/**
		 * Sets the header content type parameter value.
		 * 
		 * @param contentType header type parameter value.
		 * @return this
		 */
		B contentType(String contentType);

		/**
		 * Sets critical parameter names.
		 * 
		 * @param parameterNames critical parameter names
		 * @return this
		 */
		B crit(String... parameterNames);

		/**
		 * Sets a registered parameter value
		 * 
		 * @param <T>   value type
		 * @param param parameter
		 * @param value parameter value
		 * @return this
		 */
		<T> B param(Param param, T value);

		/**
		 * Sets an extended parameter value
		 * 
		 * @param <T>   value type
		 * @param name  parameter name
		 * @param value parameter value
		 * @return this
		 */
		<T> B param(String name, T value);
	}

	/**
	 * Verifies all parameters in a {@link WebCryptoHeader}.
	 * 
	 * @param header {@link WebCryptoHeader}
	 * @return Well-known key referred to by the header; null if not known
	 */
	static WebKey verify(WebCryptoHeader header) {
		final var algorithm = Objects.requireNonNull(header.getAlgorithm(),
				() -> "Signature or key protection algorithm is required");

		if (algorithm.use.equals(Use.ENCRYPT)) {
			Objects.requireNonNull(header.getExtendedParameter(Param.ENCRYPTION.name),
					() -> "Content encryption algorithm is required");
			for (final var param : algorithm.encryptionParams)
				if (param.required //
						&& !param.isPresent(header))
					throw new NullPointerException("Missing required encryption parameter " + param.name);
		}

		final var criticalParameters = header.getCriticalParameters();
		if (criticalParameters != null)
			for (final var paramName : criticalParameters) {
				final var param = Param.from(paramName);
				if (param == null) {
					if (header.getExtendedParameter(paramName) == null)
						throw new NullPointerException("Missing critical extended parameter " + paramName);
				} else if (!param.isPresent(header))
					throw new NullPointerException("Missing critical registered parameter " + paramName);
			}

		final var key = header.getKey();
		final var keyId = IuObject.first(header.getKeyId(), IuObject.convert(key, WebKey::getKeyId));
		final var certChain = WebCertificateReference.verify(header);

		var wellKnown = IuObject.convert(key, WebKey::wellKnown);
		if (wellKnown == null //
				&& keyId != null)
			wellKnown = IuObject.convert(header.getKeySetUri(), //
					uri -> IuIterable.filter(WebKey.readJwks(uri), //
							k -> keyId.equals(k.getKeyId())).iterator().next());
		if (wellKnown == null //
				&& certChain != null)
			wellKnown = WebKey.builder(algorithm.type[0]).cert(certChain).build().wellKnown();

		IuObject.first( //
				IuObject.convert(wellKnown, WebKey::getPublicKey), //
				IuObject.convert(certChain, c -> c[0].getPublicKey()));

		return wellKnown;
	}

	/**
	 * Gets the cryptographic algorithm.
	 * 
	 * @return {@link Algorithm}
	 */
	Algorithm getAlgorithm();

	/**
	 * Gets the key ID relative to {@link #getKeySetUri()} corresponding to a JWKS
	 * key entry.
	 * 
	 * @return key ID
	 */
	String getKeyId();

	/**
	 * Gets the URI where JWKS well-known key data can be retrieved.
	 * 
	 * @return {@link URI}
	 */
	URI getKeySetUri();

	/**
	 * Gets the well-known key data.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getKey();

	/**
	 * Gets the header type parameter value.
	 * 
	 * @return header type parameter value.
	 */
	String getType();

	/**
	 * Gets the header type parameter value.
	 * 
	 * @return header type parameter value.
	 */
	String getContentType();

	/**
	 * Gets the set of critical parameter names.
	 * 
	 * @return critical parameter names
	 */
	Set<String> getCriticalParameters();

	/**
	 * Gets extended parameters.
	 * 
	 * @param <T>  parameter type
	 * @param name parameter name
	 * @return extended parameters
	 */
	<T> T getExtendedParameter(String name);

}
