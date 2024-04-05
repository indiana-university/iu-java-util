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
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption.Encryption;
import iu.crypt.Jwk;
import iu.crypt.JwkBuilder;
import jakarta.json.JsonString;

/**
 * Unifies algorithm support and maps a cryptographic key from JCE to JSON Web
 * Key.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">JSON Web Key
 *      (JWK) RFC-7517</a>
 */
public interface WebKey extends WebKeyReference {

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
			return Stream.of(Type.values()).filter(a -> IuObject.equals(kty, a.kty) //
					&& IuObject.equals(crv, a.crv)).findFirst().get();
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
	enum Operation {
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
		public static IuJsonAdapter<Operation> JSON = IuJsonAdapter.from(a -> from(((JsonString) a).getString()),
				a -> IuJson.string(a.keyOp));

		/**
		 * Gets an item value equivalent to the JWK key_ops attribute.
		 * 
		 * @param keyOp key_ops item value
		 * @return {@link Operation}
		 */
		public static Operation from(String keyOp) {
			return EnumSet.allOf(Operation.class).stream().filter(a -> IuObject.equals(keyOp, a.keyOp)).findFirst()
					.get();
		}

		/**
		 * JSON key_ops item value
		 */
		public final String keyOp;

		private Operation(String keyOp) {
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
		HS256("HS256", "HmacSHA256", 256, Type.RAW, Use.SIGN, Set.of()),

		/**
		 * HMAC symmetric key signature w/ SHA-384.
		 */
		HS384("HS384", "HmacSHA384", 384, Type.RAW, Use.SIGN, Set.of()),

		/**
		 * HMAC symmetric key signature w/ SHA-512.
		 */
		HS512("HS512", "HmacSHA512", 512, Type.RAW, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-256.
		 */
		@Deprecated
		RS256("RS256", "SHA256withRSA", 256, Type.RSA, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-384.
		 */
		@Deprecated
		RS384("RS384", "SHA384withRSA", 384, Type.RSA, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-512.
		 */
		@Deprecated
		RS512("RS512", "SHA512withRSA", 512, Type.RSA, Use.SIGN, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-256.
		 */
		ES256("ES256", "SHA256withECDSA", 256, Type.EC_P256, Use.SIGN, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-384.
		 */
		ES384("ES384", "SHA384withECDSA", 384, Type.EC_P384, Use.SIGN, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-512.
		 */
		ES512("ES512", "SHA512withECDSA", 512, Type.EC_P521, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PSS using SHA-256 and MGF1 with SHA-256.
		 */
		PS256("PS256", "SHA256withRSAandMGF1", 256, Type.RSASSA_PSS, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PSS using SHA-384 and MGF1 with SHA-384.
		 */
		PS384("PS384", "SHA384withRSAandMGF1", 384, Type.RSASSA_PSS, Use.SIGN, Set.of()),

		/**
		 * RSASSA-PSS using SHA-512 and MGF1 with SHA-512.
		 */
		PS512("PS512", "SHA512withRSAandMGF1", 512, Type.RSASSA_PSS, Use.SIGN, Set.of()),

		/**
		 * RSAES-PKCS1-v1_5.
		 */
		@Deprecated
		RSA1_5("RSA1_5", "RSA/ECB/PKCS1Padding", 2048, Type.RSA, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * RSAES OAEP w/ default parameters.
		 */
		RSA_OAEP("RSA-OAEP", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", 2048, Type.RSA, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * RSAES OAEP w/ SHA-256 and MGF-1.
		 */
		RSA_OAEP_256("RSA-OAEP-256", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", 2048, Type.RSA, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-128 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A128GCMKW("A128GCMKW", "AES/GCM/NoPadding", 128, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),

		/**
		 * AES-192 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A192GCMKW("A192GCMKW", "AES/GCM/NoPadding", 192, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),
		/**
		 * AES-256 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A256GCMKW("A256GCMKW", "AES/GCM/NoPadding", 256, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),

		/**
		 * AES-128 Key Wrap.
		 */
		A128KW("A128KW", "AESWrap", 128, Type.RAW, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-192 Key Wrap.
		 */
		A192KW("A192KW", "AESWrap", 192, Type.RAW, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-256 Key Wrap.
		 */
		A256KW("A256KW", "AESWrap", 256, Type.RAW, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * Direct use (as CEK).
		 */
		DIRECT("dir", null, 256, Type.RAW, Use.ENCRYPT, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES("ECDH-ES", "ECDH", 0, Type.EC_P256, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-128 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A128KW("ECDH-ES+A128KW", "ECDH", 128, Type.EC_P256, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-192 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A192KW("ECDH-ES+A192KW", "ECDH", 192, Type.EC_P384, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-256 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A256KW("ECDH-ES+A256KW", "ECDH", 256, Type.EC_P521, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * PBKDF2 with HMAC SHA-256 and AES128 key wrap.
		 */
		PBES2_HS256_A128KW("PBES2-HS256+A128KW", "PBKDF2WithHmacSHA256", 128, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.PASSWORD_SALT, Param.PASSWORD_COUNT)),

		/**
		 * PBKDF2 with HMAC SHA-384 and AES192 key wrap.
		 */
		PBES2_HS384_A192KW("PBES2-HS384+A192KW", "PBKDF2WithHmacSHA384", 192, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.PASSWORD_SALT, Param.PASSWORD_COUNT)),

		/**
		 * PBKDF2 with HMAC SHA-512 and AES192 key wrap.
		 */
		PBES2_HS512_A256KW("PBES2-HS512+A256KW", "PBKDF2WithHmacSHA512", 256, Type.RAW, Use.ENCRYPT,
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.PASSWORD_SALT, Param.PASSWORD_COUNT));

		/**
		 * JSON type adapter.
		 */
		public static IuJsonAdapter<Algorithm> JSON = IuJsonAdapter.from(a -> from(((JsonString) a).getString()),
				a -> IuJson.string(a.alg));

		/**
		 * Gets the value equivalent to the JWK alg attribute.
		 * 
		 * @param alg alg attribute value
		 * @return {@link Operation}
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

		private Algorithm(String alg, String algorithm, int size, Type type, Use use, Set<Param> encryptionParams) {
			this.alg = alg;
			this.algorithm = algorithm;
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
	interface Builder<B extends Builder<B>> extends WebKeyReference.Builder<B> {
		/**
		 * Sets the public key use.
		 *
		 * @param use public key use
		 * @return this
		 */
		B use(Use use);

		/**
		 * Sets the key operations.
		 * 
		 * @param ops key operations
		 * @return this
		 */
		B ops(Operation... ops);

		/**
		 * Generates a public/private key pair for the algorithm specified by
		 * {@link #algorithm(Algorithm)} using the default size.
		 * 
		 * @return this
		 */
		B ephemeral();

		/**
		 * Generates a public/private key pair or secret key without setting
		 * {@link #algorithm}.
		 * 
		 * @param algorithm algorithm the key will be used with
		 * @return this
		 */
		B ephemeral(Algorithm algorithm);

		/**
		 * Generates an ephemeral content encryption key.
		 * 
		 * @param encryption content encryption algorithm
		 * @return this
		 */
		B ephemeral(Encryption encryption);

		/**
		 * Adds raw key data.
		 * 
		 * @param key raw key data
		 * @return this
		 */
		B key(byte[] key);

		/**
		 * Adds public key parameters.
		 * 
		 * @param publicKey public key
		 * @return this
		 */
		B key(PublicKey publicKey);

		/**
		 * Adds private key parameters.
		 * 
		 * @param privateKey private key
		 * @return this
		 */
		B key(PrivateKey privateKey);

		/**
		 * Sets both public and private keys from a {@link KeyPair}.
		 * 
		 * @param keyPair key pair;
		 * @return this
		 */
		B key(KeyPair keyPair);

		/**
		 * Builds the web key.
		 * 
		 * @return {@link WebKey}
		 */
		WebKey build();
	}

	/**
	 * Verifies encoded key data is correct for the key type, use, algorithm, and
	 * X.509 certificate chain.
	 * 
	 * @param webKey {@link WebKey}
	 * @return {@link PublicKey} resolved from the web key, or null if no public key
	 *         was resolved; private and raw key values will be verified as valid
	 *         for the key type and/or public key, and <em>may</em> continue to be
	 *         accessed from the original web key as needed.
	 * @throws IllegalArgumentException if the key is invalid
	 */
	static PublicKey verify(WebKey webKey) {
		final var key = webKey.getKey();
		final var type = Objects.requireNonNull(webKey.getType(), "Key type is required");

		final var algorithm = webKey.getAlgorithm();
		if (algorithm != null //
				&& !type.equals(algorithm.type))
			throw new IllegalArgumentException("Incorrect type " + type + " for algorithm " + algorithm);

		final var cert = IuObject.convert(WebCertificateReference.verify(webKey), a -> a[0]);

		if (type.equals(Type.RAW)) {
			IuObject.require(webKey.getPrivateKey(), Objects::isNull, () -> "Unexpected private key");
			IuObject.require(webKey.getPublicKey(), Objects::isNull, () -> "Unexpected public key");
			IuObject.require(cert, Objects::isNull, () -> "Unexpected certificate");
			return null;
		}

		var publicKey = IuObject.once(webKey.getPublicKey(), //
				IuObject.convert(cert, X509Certificate::getPublicKey), //
				() -> "public key doesn't match X.509 certificate");

		final var privateKey = webKey.getPrivateKey();
		if (type.kty.equals("EC")) {
			if (key != null)
				throw new IllegalArgumentException("Unexpected raw key data for " + type);

			final Predicate<ECKey> checkECParam = IuException.unchecked(() -> {
				final var algorithmParamters = AlgorithmParameters.getInstance("EC");
				algorithmParamters.init(new ECGenParameterSpec(type.ecParam));
				final var spec = algorithmParamters.getParameterSpec(ECParameterSpec.class);
				return a -> spec.equals(a.getParams());
			});

			IuObject.require(IuObject.requireType(ECPrivateKey.class, privateKey), checkECParam,
					() -> "Unexpected private EC key parameters for " + type);
			IuObject.require(IuObject.requireType(ECPublicKey.class, publicKey), checkECParam,
					() -> "Unexpected public EC key parameters for " + type);
		} else {
			if (key != null)
				throw new IllegalArgumentException("Unexpected raw key data for " + type);

			final var rsaPrivate = IuObject.requireType(RSAPrivateKey.class, privateKey);
			final var rsaPublic = IuObject.requireType(RSAPublicKey.class, publicKey);
			if (rsaPrivate != null)
				if (rsaPublic != null) {
					if (!rsaPrivate.getModulus().equals(rsaPublic.getModulus()))
						throw new IllegalArgumentException("RSA public key modulus doesn't match private key");
					else if ((rsaPrivate instanceof RSAPrivateCrtKey) //
							&& !((RSAPrivateCrtKey) rsaPrivate).getPublicExponent()
									.equals(rsaPublic.getPublicExponent()))
						throw new IllegalArgumentException("RSA public key exponent doesn't match private key");
				} else if (rsaPrivate instanceof RSAPrivateCrtKey)
					publicKey = IuException.unchecked(
							() -> (RSAPublicKey) KeyFactory.getInstance(type.kty).generatePublic(new RSAPublicKeySpec(
									rsaPrivate.getModulus(), ((RSAPrivateCrtKey) rsaPrivate).getPublicExponent())));
		}

		return publicKey;
	}

	/**
	 * Creates a new {@link Builder}.
	 * 
	 * @param type key type
	 * @return {@link Builder}
	 */
	static Builder<?> builder(Type type) {
		return new JwkBuilder(type);
	}

	/**
	 * Creates an ephemeral content encryption key, for use with
	 * {@link Algorithm#DIRECT}.
	 * 
	 * <p>
	 * Ephemeral keys are generated using JDK 11 compliant <a href=
	 * "https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html">
	 * standard algorithms</a> with {@link Security#getProviders() registered JCE
	 * providers}
	 * </p>
	 * 
	 * @param encryption encryption algorithm
	 * @return content encryption key
	 */
	static WebKey ephemeral(Encryption encryption) {
		return builder(Type.RAW).ephemeral(encryption).build();
	}

	/**
	 * Creates an ephemeral key for use as JWE recipient or JWS issuer.
	 * 
	 * <p>
	 * Ephemeral keys are generated using JDK 11 compliant <a href=
	 * "https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html">
	 * standard algorithms</a> with {@link Security#getProviders() registered JCE
	 * providers}
	 * </p>
	 * 
	 * @param algorithm key algorithm
	 * @return JWE recipient or JWS issuer key
	 */
	static WebKey ephemeral(Algorithm algorithm) {
		return builder(algorithm.type).ephemeral(algorithm).build();
	}

	/**
	 * Parses a JSON Web Key (JWK).
	 * 
	 * @param jwk JSON Web Key
	 * @return {@link WebKey}
	 */
	static WebKey parse(String jwk) {
		return new Jwk(IuJson.parse(jwk).asJsonObject());
	}

	/**
	 * Parses a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	static Iterable<? extends WebKey> parseJwks(String jwks) {
		return Jwk.parseJwks(IuJson.parse(jwks).asJsonObject());
	}

	/**
	 * Reads a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	static Iterable<? extends WebKey> readJwks(URI jwks) {
		return Jwk.readJwks(jwks);
	}

	/**
	 * Reads a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	static Iterable<? extends WebKey> readJwks(InputStream jwks) {
		return Jwk.readJwks(jwks);
	}

	/**
	 * Serializes {@link WebKey}s as a JSON Web Key Set.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @return serialized JWKS
	 */
	static String asJwks(Iterable<? extends WebKey> webKeys) {
		return Jwk.asJwks(webKeys).toString();
	}

	/**
	 * Writes {@link WebKey} as a JSON Web Key.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @param out     {@link OutputStream}
	 */
	static void writeJwks(Iterable<? extends WebKey> webKeys, OutputStream out) {
		Jwk.writeJwks(webKeys, out);
	}

	/**
	 * Returns a copy of this key for which {@link #getPrivateKey()} and
	 * {@link #getKey()} always return null, and for which the source data backing
	 * these methods is not populated.
	 * 
	 * <p>
	 * If these methods would already return null, this key is returned as-is.
	 * </p>
	 * 
	 * @return this key, or a copy that omits secret and private key data
	 */
	WebKey wellKnown();

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
	 * Gets the key operations.
	 * 
	 * @return key operations
	 */
	Set<Operation> getOps();

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
