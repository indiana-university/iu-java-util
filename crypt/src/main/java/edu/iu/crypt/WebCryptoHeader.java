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
import java.util.Set;
import java.util.function.Function;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import iu.crypt.JoseBuilder;

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
		 * @see {@link Algorithm#ECDH_ES}
		 * @see {@link Algorithm#ECDH_ES_A128KW}
		 * @see {@link Algorithm#ECDH_ES_A192KW}
		 * @see {@link Algorithm#ECDH_ES_A256KW}
		 */
		EPHEMERAL_PUBLIC_KEY("epk", EnumSet.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("epk")),

		/**
		 * Public originator identifier (PartyUInfo) for key derivation.
		 * 
		 * @see {@link Algorithm#ECDH_ES}
		 * @see {@link Algorithm#ECDH_ES_A128KW}
		 * @see {@link Algorithm#ECDH_ES_A192KW}
		 * @see {@link Algorithm#ECDH_ES_A256KW}
		 */
		PARTY_UINFO("apu", EnumSet.of(Use.ENCRYPT), false, a -> a.getExtendedParameter("apu")),

		/**
		 * Public recipient identifier (PartyVInfo) for key derivation.
		 * 
		 * @see {@link Algorithm#ECDH_ES}
		 * @see {@link Algorithm#ECDH_ES_A128KW}
		 * @see {@link Algorithm#ECDH_ES_A192KW}
		 * @see {@link Algorithm#ECDH_ES_A256KW}
		 */
		PARTY_VINFO("apv", EnumSet.of(Use.ENCRYPT), false, a -> a.getExtendedParameter("apv")),

		/**
		 * Initialization vector for GCM key wrap.
		 * 
		 * @see {@link Algorithm#A128GCMKW}
		 * @see {@link Algorithm#A192GCMKW}
		 * @see {@link Algorithm#A256GCMKW}
		 */
		INITIALIZATION_VECTOR("iv", EnumSet.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("iv")),

		/**
		 * Authentication tag for GCM key wrap.
		 * 
		 * @see {@link Algorithm#A128GCMKW}
		 * @see {@link Algorithm#A192GCMKW}
		 * @see {@link Algorithm#A256GCMKW}
		 */
		TAG("tag", Set.of(Use.ENCRYPT), true, a -> a.getExtendedParameter("tag"));

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
			return get.apply(header) != null;
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
		 * Sets the cryptographic algorithm.
		 * 
		 * @param algorithm {@link Algorithm}
		 * @return this
		 */
		B algorithm(Algorithm algorithm);

		/**
		 * Sets the key ID relative to {@link #getKeySetUri()} corresponding to a JWKS
		 * key entry.
		 *
		 * @param keyId key ID
		 * @return this
		 */
		B id(String keyId);

		/**
		 * Sets the URI where JWKS well-known key data can be retrieved.
		 * 
		 * @param uri JWKS {@link URI}
		 * @return this
		 */
		B jwks(URI uri);

		/**
		 * Sets a key to use for creating the signature or encryption.
		 * 
		 * <p>
		 * This key will not be included in the serialized output. This is the same as
		 * calling {@link #jwk(WebKey, boolean) jwk(key, false)}.
		 * </p>
		 * 
		 * @param key key to use for creating the signature or encryption
		 * @return this
		 */
		default B jwk(WebKey key) {
			return jwk(key, false);
		}

		/**
		 * Sets the key data for this header.
		 * 
		 * <p>
		 * This key may contain private and/or symmetric key data to be used for
		 * creating digital signatures or facilitating key agreement for encryption.
		 * However, only public keys and certificate data will be included in the
		 * serialized headers.
		 * </p>
		 * 
		 * @param key    {@link WebKey}, provided by the same module as this builder.
		 * @param silent true to omit all key data from the serialized header; false to
		 *               include public keys and/or certificates only.
		 * @return this
		 */
		B jwk(WebKey key, boolean silent);

		/**
		 * Sets the header type parameter value.
		 * 
		 * @param type header type parameter value.
		 * @return this
		 */
		B type(String type);

		/**
		 * Sets the header type parameter value.
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
		 * Sets the agreement PartyUInfo parameter for key derivation.
		 * 
		 * @param apu PartyUInfo value
		 * @return this
		 * @see {@link Param#PARTY_UINFO}
		 */
		B apu(byte[] apu);

		/**
		 * Sets the agreement PartyVInfo parameter for key derivation.
		 * 
		 * @param apv PartyVInfo value
		 * @return this
		 * @see {@link Param#PARTY_VINFO}
		 */
		B apv(byte[] apv);

		/**
		 * Sets an optional extended parameter.
		 * 
		 * @param name  parameter name
		 * @param value parameter value
		 * @return this
		 */
		<T> B ext(String name, T value);
	}

	/**
	 * Extension provider interface.
	 * 
	 * @param <T> value type, <em>must</em> be valid for use with
	 *            {@link IuJson#toJson(Object)}
	 */
	interface Extension<T> {

		/**
		 * Validates an incoming parameter value.
		 * 
		 * @param value   value
		 * @param builder {@link WebSignature.Builder} or
		 *                {@link WebEncryptionRecipient.Builder}
		 * @throws IllegalArgumentException if the value is invalid
		 */
		default void validate(T value, Builder<?> builder) throws IllegalArgumentException {
		}

		/**
		 * Applies extended verification logic for processing {@link WebCryptoHeader}.
		 * 
		 * @param header JOSE header
		 * @throws IllegalStateException if the header verification fails
		 */
		default void verify(WebCryptoHeader header) throws IllegalStateException {
		}

		/**
		 * Applies extended verification logic for processing {@link WebSignature}.
		 * 
		 * @param signature JWS signature
		 * @throws IllegalStateException if the verification fails
		 */
		default void verify(WebSignature signature) throws IllegalStateException {
		}

		/**
		 * Applies extended verification logic for processing {@link WebEncryption}.
		 * 
		 * @param encryption JWE encrypted message
		 * @param recipient  JWE recipient, available
		 *                   {@link WebEncryption#getRecipients()}
		 * @throws IllegalStateException if the verification fails
		 */
		default void verify(WebEncryption encryption, WebEncryptionRecipient recipient) throws IllegalStateException {
		}
	}

	/**
	 * Registers an extension.
	 * 
	 * @param parameterName parameter name; <em>must not</em> be a registered
	 *                      parameter name enumerated by {@link Param},
	 *                      <em>should</em> be collision-resistant. Take care when
	 *                      using {@link Extension} to implement an <a href=
	 *                      "https://www.iana.org/assignments/jose/jose.xhtml#web-signature-encryption-header-parameters">IANA
	 *                      Registered Parameter</a> not enumerated by
	 *                      {@link Param}, since these may be implemented internally
	 *                      in a future release.
	 * @param extension     provider implementation
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7515#section-4.2">RFC-7516 JWS
	 *      Section 4.2</a>
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7516#section-4.2">RFC-7516 JWE
	 *      Section 4.2</a>
	 */
	static <T> void register(String parameterName, Extension<T> extension) {
		JoseBuilder.register(parameterName, extension);
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
