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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECParameterSpec;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCryptoHeader.Param;
import iu.crypt.JwkBuilder;
import jakarta.json.JsonString;

/**
 * Unifies algorithm support and maps a cryptographic key from JCE to JSON Web
 * Key.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">JSON Web Key
 *      (JWK) RFC-7517</a>
 */
public interface WebKey extends WebCertificateReference {

	/**
	 * Enumerates key type.
	 * 
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7518#section-6.1">RFC
	 *      7518 JWA Section 6.1</a>
	 */
	enum Type {
		/**
		 * NIST P-256 Elliptic Curve.
		 */
		EC_P256("EC", "P-256", "secp256r1"),

		/**
		 * NIST P-384 Elliptic Curve.
		 */
		EC_P384("EC", "P-384", "secp384r1"),

		/**
		 * NIST P-521 Elliptic Curve.
		 */
		EC_P521("EC", "P-521", "secp521r1"),

		/**
		 * RSA encryption or RSASSA-PKCS1-v1_5 signing, minimum 2048 bit.
		 */
		RSA("RSA", null, null),

		/**
		 * RSASSA-PSS signing, minimum 2048 bit.
		 */
		RSASSA_PSS("RSASSA-PSS", null, null),

		/**
		 * Raw symmetric key data (octet sequence).
		 */
		RAW("oct", null, null);

		/**
		 * Gets the value equivalent to the JWK kty attribute.
		 * 
		 * @param kty JWK kty attribute value
		 * @param crv JWK crv attribute value
		 * @return {@link Type}
		 */
		public static Type from(String kty, String crv) {
			return EnumSet.allOf(Type.class).stream()
					.filter(a -> IuObject.equals(kty, a.kty) && IuObject.equals(crv, a.crv)).findFirst().get();
		}

		/**
		 * JSON kty attribute value.
		 */
		public final String kty;

		/**
		 * JSON crv attribute value.
		 */
		public final String crv;

		/**
		 * {@link ECParameterSpec JCE Elliptic Curve parameter} standard curve name.
		 */
		public final String ecParam;

		private Type(String kty, String crv, String ecParam) {
			this.kty = kty;
			this.crv = crv;
			this.ecParam = ecParam;
		}

		/**
		 * Gets a {@link KeyFactory} suitable for decoding a key by type.
		 * 
		 * @param type key type
		 * @return {@link KeyFactory}
		 */
		static KeyFactory getKeyFactory(Type type) {
			return IuException.unchecked(() -> {
				switch (type) {
				case EC_P256:
				case EC_P384:
				case EC_P521:
					return KeyFactory.getInstance("EC");

				case RSA:
					return KeyFactory.getInstance("RSA");

				case RSASSA_PSS:
					return KeyFactory.getInstance("RSASSA-PSS");

				default:
					throw new IllegalArgumentException("Cannot read PEM encoded key data for " + type);
				}
			});
		}

	}

	/**
	 * Enumerates public key use.
	 */
	enum Use {
		/**
		 * Used for digital signing.
		 */
		SIGN("sig"),

		/**
		 * Used for encryption.
		 */
		ENCRYPT("enc");

		/**
		 * Gets the value equivalent to the JWK use attribute.
		 * 
		 * @param use JWK use attribute value
		 * @return {@link Use}
		 */
		public static Use from(String use) {
			return EnumSet.allOf(Use.class).stream().filter(a -> use.equals(a.use)).findFirst().get();
		}

		/**
		 * JSON type adapter
		 */
		public static IuJsonAdapter<Use> JSON = IuJsonAdapter.from(v -> from(((JsonString) v).getString()),
				u -> IuJson.string(u.use));

		/**
		 * JSON use attribute value.
		 */
		public final String use;

		private Use(String use) {
			this.use = use;
		}
	}

	/**
	 * Enumerates key operations.
	 */
	enum Op {
		/**
		 * Compute digital signature or MAC.
		 */
		SIGN("sign"),

		/**
		 * Verify digital signature or MAC.
		 */
		VERIFY("verify"),

		/**
		 * Encrypt content.
		 */
		ENCRYPT("encrypt"),

		/**
		 * Decrypt content and validate decryption.
		 */
		DECRYPT("decrypt"),

		/**
		 * Encrypt key.
		 */
		WRAP("wrapKey"),

		/**
		 * Decrypt key and validate decryption.
		 */
		UNWRAP("unwrapKey"),

		/**
		 * Derive key.
		 */
		DERIVE_KEY("deriveKey"),

		/**
		 * Derive bits not to be used as a key.
		 */
		DERIVE_BITS("deriveBits");

		/**
		 * JSON type adapter.
		 */
		public static IuJsonAdapter<Op> JSON = IuJsonAdapter.from(a -> from(((JsonString) a).getString()),
				a -> IuJson.string(a.keyOp));

		/**
		 * Gets an item value equivalent to the JWK key_ops attribute.
		 * 
		 * @param keyOp key_ops item value
		 * @return {@link Op}
		 */
		public static Op from(String keyOp) {
			return EnumSet.allOf(Op.class).stream().filter(a -> IuObject.equals(keyOp, a.keyOp)).findFirst().get();
		}

		/**
		 * JSON key_ops item value
		 */
		public final String keyOp;

		private Op(String keyOp) {
			this.keyOp = keyOp;
		}
	}

	/**
	 * Enumerates supported signature and encryption algorithms.
	 */
	enum Algorithm {
		/**
		 * HMAC symmetric key signature w/ SHA-256.
		 */
		HS256("HS256", "HmacSHA256", null, 256, Type.RAW, Use.SIGN, Set.of()),

		/**
		 * HMAC symmetric key signature w/ SHA-384.
		 */
		HS384("HS384", "HmacSHA384", null, 384, Type.RAW, Use.SIGN, Set.of()),

		/**
		 * HMAC symmetric key signature w/ SHA-512.
		 */
		HS512("HS512", "HmacSHA512", null, 512, Type.RAW, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-256.
		 */
		@Deprecated
		RS256("RS256", "SHA256withRSA", null, 256, Type.RSA, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-384.
		 */
		@Deprecated
		RS384("RS384", "SHA384withRSA", null, 384, Type.RSA, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-512.
		 */
		@Deprecated
		RS512("RS512", "SHA512withRSA", null, 512, Type.RSA, Use.SIGN, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-256.
		 */
		ES256("ES256", "SHA256withECDSA", null, 256, Type.EC_P256, Use.SIGN, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-384.
		 */
		ES384("ES384", "SHA384withECDSA", null, 384, Type.EC_P384, Use.SIGN, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-512.
		 */
		ES512("ES512", "SHA512withECDSA", null, 512, Type.EC_P521, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PSS using SHA-256 and MGF1 with SHA-256.
		 */
		PS256("PS256", "RSASSA-PSS", "SHA256withRSAandMGF1", 2048, Type.RSASSA_PSS, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PSS using SHA-384 and MGF1 with SHA-384.
		 */
		PS384("PS384", "RSASSA-PSS", "SHA384withRSAandMGF1", 2048, Type.RSASSA_PSS, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PSS using SHA-512 and MGF1 with SHA-512.
		 */
		PS512("PS512", "RSASSA-PSS", "SHA512withRSAandMGF1", 2048, Type.RSASSA_PSS, Use.SIGN, Set.of()),

		/**
		 * RSAES-PKCS1-v1_5.
		 */
		@Deprecated
		RSA1_5("RSA1_5", "RSA", "RSA/ECB/PKCS1Padding", 2048, Type.RSA, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * RSAES OAEP w/ default parameters.
		 */
		RSA_OAEP("RSA-OAEP", "RSA", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", 2048, Type.RSA, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * RSAES OAEP w/ SHA-256 and MGF-1.
		 */
		RSA_OAEP_256("RSA-OAEP-256", "RSA", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", 2048, Type.RSA, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-128 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A128GCMKW("A128GCMKW", "AES", "AES/GCM/NoPadding", 128, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),

		/**
		 * AES-192 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A192GCMKW("A192GCMKW", "AES", "AES/GCM/NoPadding", 192, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),
		/**
		 * AES-256 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A256GCMKW("A256GCMKW", "AES", "AES/GCM/NoPadding", 256, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),

		/**
		 * AES-128 Key Wrap.
		 */
		A128KW("A128KW", "AES", "AESWrap", 128, Type.RAW, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-192 Key Wrap.
		 */
		A192KW("A192KW", "AES", "AESWrap", 192, Type.RAW, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-256 Key Wrap.
		 */
		A256KW("A256KW", "AES", "AESWrap", 256, Type.RAW, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * Direct use (as CEK).
		 */
		DIRECT("dir", "AES", null, 256, Type.RAW, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES("ECDH-ES", "ECDH", null, 0, Type.EC_P256, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-128 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A128KW("ECDH-ES+A128KW", "ECDH", "AESWrap", 128, Type.EC_P256, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-192 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A192KW("ECDH-ES+A192KW", "ECDH", "AESWrap", 192, Type.EC_P384, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-256 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A256KW("ECDH-ES+A256KW", "ECDH", "AESWrap", 256, Type.EC_P521, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * PBES2 with HMAC SHA-256 and "A128KW" wrapping.
		 */
		PBES2_HS256_A128KW("PBES2-HS256+A128KW", "PBEWithHmacSHA256AndAES_128", null, 128, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP /* , TODO: p2s, p2c */)),

		/**
		 * PBES2 with HMAC SHA-384 and "A192KW" wrapping.
		 */
		PBES2_HS384_A192KW("PBES2-HS384+A192KW", "PBEWithHmacSHA384AndAES_192", null, 192, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP /* , TODO: p2s, p2c */)),

		/**
		 * PBES2 with HMAC SHA-512 and "A256KW" wrapping.
		 */
		PBES2_HS512_A256KW("PBES2-HS512+A256KW", "PBEWithHmacSHA512AndAES_256", null, 256, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP /* , TODO: p2s, p2c */));

		/**
		 * JSON type adapter.
		 */
		public static IuJsonAdapter<Algorithm> JSON = IuJsonAdapter.from(a -> from(((JsonString) a).getString()),
				a -> IuJson.string(a.alg));

		/**
		 * Gets the value equivalent to the JWK alg attribute.
		 * 
		 * @param alg alg attribute value
		 * @return {@link Op}
		 */
		public static Algorithm from(String alg) {
			return EnumSet.allOf(Algorithm.class).stream().filter(a -> IuObject.equals(alg, a.alg)).findFirst().get();
		}

		/**
		 * JSON alg attribute value.
		 */
		public final String alg;

		/**
		 * JCE signature or key agreement algorithm name.
		 */
		public final String algorithm;

		/**
		 * JCE key encryption algorithm name.
		 */
		public final String keyAlgorithm;

		/**
		 * Encryption key or signature hash size.
		 */
		public final int size;

		/**
		 * Key type associated with this algorithm.
		 */
		public final Type type;

		/**
		 * Key usage associated with this algorithm.
		 */
		public final Use use;

		/**
		 * Set of encryption parameters used by this algorithm.
		 */
		public final Set<Param> encryptionParams;

		private Algorithm(String alg, String algorithm, String auxiliaryAlgorithm, int size, Type type, Use use,
				Set<Param> encryptionParams) {
			this.alg = alg;
			this.algorithm = algorithm;
			this.keyAlgorithm = auxiliaryAlgorithm;
			this.size = size;
			this.type = type;
			this.use = use;
			this.encryptionParams = encryptionParams;
		}
	}

	/**
	 * Builder interface for creating {@link WebKey} instances.
	 * 
	 * @param <B> builder type
	 */
	interface Builder<B extends Builder<B>> {
		/**
		 * Sets the Key ID.
		 * 
		 * @param id key ID
		 * @return this;
		 */
		B id(String id);

		/**
		 * Sets the key type.
		 * 
		 * @param type key type
		 * @return this
		 */
		B type(Type type);

		/**
		 * Sets the public key use.
		 *
		 * @param use public key use
		 * @return this
		 */
		B use(Use use);

		/**
		 * Sets the algorithm.
		 * 
		 * @param algorithm algorithm
		 * @return this
		 */
		B algorithm(Algorithm algorithm);

		/**
		 * Sets the key operations.
		 * 
		 * @param ops key operations
		 * @return this
		 */
		B ops(Op... ops);

		/**
		 * Generates a public/private key pair for the algorithm specified by
		 * {@link #algorithm(Algorithm)}.
		 * 
		 * @return this
		 */
		B ephemeral();

		/**
		 * Generates a public/private key pair or secret key without setting
		 * {@link #algorithm}.
		 * 
		 * @param algorithm
		 * @param size      key size in bits
		 * @return this
		 */
		B ephemeral(Algorithm algorithm);

		/**
		 * Sets the raw key data.
		 * 
		 * @param key raw key data
		 * @return this
		 */
		B key(byte[] key);

		/**
		 * Sets public key only.
		 * 
		 * @param publicKey public key
		 * @return this
		 */
		B pub(PublicKey publicKey);

		/**
		 * Sets both public and private keys from a {@link KeyPair}.
		 * 
		 * @param keyPair key pair;
		 * @return this
		 */
		B pair(KeyPair keyPair);

		/**
		 * Sets the URI where X.509 certificate associated with this key can be
		 * retrieved.
		 * 
		 * <p>
		 * The URI will be validated and resolved when this method is invoked. To ensure
		 * dependency on a remote URI won't impact application startup, always store
		 * certificates locally and use {@link #cert(X509Certificate...)} instead of
		 * this method for critical initialization in production environments.
		 * </p>
		 * 
		 * @param uri {@link URI}
		 * @return this
		 */
		B cert(URI uri);

		/**
		 * Sets the URI where X.509 certificate associated with this key can be
		 * retrieved.
		 * 
		 * @param chain one or more {@link X509Certificate}s
		 * @return this
		 */
		B cert(X509Certificate... chain);

		/**
		 * Sets the URI where X.509 certificate associated with this key can be
		 * retrieved.
		 * 
		 * @param privateKey private key associated with the first certificate in the
		 *                   chain
		 * @param chain      one or more {@link X509Certificate}s
		 * @return this
		 */
		B cert(PrivateKey privateKey, X509Certificate... chain);

		/**
		 * Sets the certificate thumbprint.
		 * 
		 * @param certificateThumbprint JSON x5t attribute value
		 * @return this
		 */
		B x5t(byte[] certificateThumbprint);

		/**
		 * Sets the certificate SHA-256 thumbprint.
		 * 
		 * @param certificateSha256Thumbprint JSON x5t attribute value
		 * @return this
		 */
		B x5t256(byte[] certificateSha256Thumbprint);

		/**
		 * Sets key data from potentially concatenated PEM-encoded input.
		 * 
		 * @param pemEncoded {@link InputStream} of PEM encoded key data, potentially
		 *                   concatenated
		 * @return this
		 */
		B pem(InputStream pemEncoded);

		/**
		 * Sets key data from potentially concatenated PEM-encoded input.
		 * 
		 * @param pemEncoded potentially concatenated PEM encoded key data
		 * @return this
		 */
		B pem(String pemEncoded);

		/**
		 * Builds the web key.
		 * 
		 * @return {@link WebKey}
		 */
		WebKey build();
	}

	/**
	 * Creates a new {@link Builder}.
	 * 
	 * @return {@link Builder}
	 */
	static Builder<?> builder() {
		return new JwkBuilder();
	}

	/**
	 * Parses a JSON Web Key (JWK).
	 * 
	 * @param jwk JSON Web Key
	 * @return {@link WebKey}
	 */
	static WebKey parse(String jwk) {
		return JwkBuilder.parse(jwk);
	}

	/**
	 * Parses a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	static Stream<? extends WebKey> parseJwks(String jwks) {
		return JwkBuilder.parseJwks(jwks);
	}

	/**
	 * Reads a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	static Stream<? extends WebKey> readJwks(URI jwks) {
		return JwkBuilder.readJwks(jwks);
	}

	/**
	 * Serializes {@link WebKey}s as a JSON Web Key Set.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @return serialized JWKS
	 */
	static String asJwks(Stream<? extends WebKey> webKeys) {
		return JwkBuilder.asJwks(webKeys);
	}

	/**
	 * Writes {@link WebKey} as a JSON Web Key.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @param out     {@link OutputStream}
	 */
	static void writeJwks(Stream<? extends WebKey> webKeys, OutputStream out) {
		JwkBuilder.writeJwks(webKeys, out);
	}

	/**
	 * Returns a copy of this key for which {@link #getPrivateKey()} and
	 * {@link #getKey()} always return null, and for which the source data backing
	 * these methods is not populated.
	 * 
	 * <p>
	 * If these methods would already return, this key is returned as-is.
	 * </p>
	 * 
	 * @return this key, or a copy that omits secret and private key data
	 */
	WebKey wellKnown();

	/**
	 * Gets the Key ID.
	 * 
	 * @return key ID
	 */
	String getId();

	/**
	 * Gets the key type.
	 * 
	 * @return key type
	 */
	Type getType();

	/**
	 * Gets the public key use.
	 * 
	 * @return public key use.
	 */
	Use getUse();

	/**
	 * Gets the algorithm.
	 * 
	 * @return algorithm
	 */
	Algorithm getAlgorithm();

	/**
	 * Gets the key operations.
	 * 
	 * @return key operations
	 */
	Set<Op> getOps();

	/**
	 * Gets the raw key data for use when {@link Type#RAW}.
	 * 
	 * @return raw key data
	 */
	byte[] getKey();

	/**
	 * Gets the JCE private key implementation.
	 * 
	 * @return {@link PrivateKey}
	 */
	PrivateKey getPrivateKey();

	/**
	 * Gets the JCE public key implementation.
	 * 
	 * @return {@link PublicKey}
	 */
	PublicKey getPublicKey();

}