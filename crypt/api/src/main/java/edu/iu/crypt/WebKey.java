/*
 * Copyright © 2025 Indiana University
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
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.XECKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.crypto.SecretKey;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption.Encryption;

/**
 * Unifies algorithm support and maps a cryptographic key from JCE to JSON Web
 * Key.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">JSON Web Key
 *      (JWK) RFC-7517</a>
 */
public interface WebKey extends WebKeyReference {

	/**
	 * Gets the {@link ECParameterSpec} for a standard parameter name.
	 * 
	 * @param name standard parameter name
	 * @return Elliptic Curve parameters
	 */
	static AlgorithmParameterSpec algorithmParams(String name) {
		return IuObject.convert(name, a -> IuException.unchecked(() -> {
			if (Set.of("secp256r1", "secp384r1", "secp521r1").contains(a)) {
				final var algorithmParamters = AlgorithmParameters.getInstance("EC");
				algorithmParamters.init(new ECGenParameterSpec(a));
				return algorithmParamters.getParameterSpec(ECParameterSpec.class);
			} else if (Set.of("Ed25519", "Ed448", "X25519", "X448").contains(a))
				return (AlgorithmParameterSpec) IuException
						.unchecked(() -> NamedParameterSpec.class.getField(a.toUpperCase()).get(null));
			else
				return null;
		}));
	}

	/**
	 * Gets the {@link AlgorithmParameterSpec} from a key.
	 * 
	 * @param key key
	 * @return {@link AlgorithmParameterSpec}
	 */
	static AlgorithmParameterSpec algorithmParams(Key key) {
		if (key == null //
				|| key.getAlgorithm() == null //
				|| key.getAlgorithm().startsWith("RSA"))
			return null;

		if (key instanceof ECKey)
			return ((ECKey) key).getParams();
		if (key instanceof XECKey)
			return ((XECKey) key).getParams();
		else // EdEC is the last supported type; throws IllegalStateException on JDK 11
				// TODO switch from reflection to compiled cast for source level to 17+
			return (NamedParameterSpec) IuException.uncheckedInvocation(() -> ClassLoader.getPlatformClassLoader()
					.loadClass("java.security.interfaces.EdECKey").getMethod("getParams").invoke(key));
	}

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
		 * Edwards 25519 Elliptic Curve, for {@link Use#SIGN}.
		 * 
		 * @see <a href="https://www.rfc-editor.org/rfc/rfc8037.html">RFC-8037</a>
		 */
		ED25519("OKP", "Ed25519", "Ed25519"),

		/**
		 * Edwards 448 Elliptic Curve, for {@link Use#SIGN}.
		 * 
		 * @see <a href="https://www.rfc-editor.org/rfc/rfc8037.html">RFC-8037</a>
		 */
		ED448("OKP", "Ed448", "Ed448"),

		/**
		 * ECDH X25519 Elliptic Curve, for {@link Use#ENCRYPT}.
		 * 
		 * @see <a href="https://www.rfc-editor.org/rfc/rfc8037.html">RFC-8037</a>
		 */
		X25519("OKP", "X25519", "X25519"),

		/**
		 * ECDH X448 Elliptic Curve, for {@link Use#ENCRYPT}.
		 * 
		 * @see <a href="https://www.rfc-editor.org/rfc/rfc8037.html">RFC-8037</a>
		 */
		X448("OKP", "X448", "X448"),

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
					&& IuObject.equals(crv, a.crv)).findFirst().orElse(null);
		}

		/**
		 * Gets the value equivalent to the JWK kty attribute.
		 * 
		 * @param algorithmParams Standard algorithm parameters name
		 * @return {@link Type}
		 */
		public static Type from(AlgorithmParameterSpec algorithmParams) {
			if (algorithmParams == null)
				return null;

			final Predicate<Type> specMatch;
			if (algorithmParams instanceof NamedParameterSpec) {
				final var namedSpec = (NamedParameterSpec) algorithmParams;
				specMatch = type -> {
					final var typeSpec = algorithmParams(type.algorithmParams);
					return (typeSpec instanceof NamedParameterSpec)
							&& ((NamedParameterSpec) typeSpec).getName().equals(namedSpec.getName());
				};
			} else
				specMatch = type -> algorithmParams.equals(algorithmParams(type.algorithmParams));

			return Stream.of(Type.values()).filter(specMatch).findFirst().orElse(null);
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
		 * Standard algorithm parameter specification name.
		 */
		public final String algorithmParams;

		private Type(String kty, String crv, String algorithmParams) {
			this.kty = kty;
			this.crv = crv;
			this.algorithmParams = algorithmParams;
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
		HS256("HS256", "HmacSHA256", 256, new Type[] { Type.RAW }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * HMAC symmetric key signature w/ SHA-384.
		 */
		HS384("HS384", "HmacSHA384", 384, new Type[] { Type.RAW }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * HMAC symmetric key signature w/ SHA-512.
		 */
		HS512("HS512", "HmacSHA512", 512, new Type[] { Type.RAW }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-256.
		 */
		@Deprecated
		RS256("RS256", "SHA256withRSA", 256, new Type[] { Type.RSA }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-384.
		 */
		@Deprecated
		RS384("RS384", "SHA384withRSA", 384, new Type[] { Type.RSA }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-512.
		 */
		@Deprecated
		RS512("RS512", "SHA512withRSA", 512, new Type[] { Type.RSA }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-256.
		 */
		ES256("ES256", "SHA256withECDSA", 256, new Type[] { Type.EC_P256, Type.EC_P384, Type.EC_P521 }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-384.
		 */
		ES384("ES384", "SHA384withECDSA", 384, new Type[] { Type.EC_P384, Type.EC_P521, Type.EC_P256 }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * Elliptic Curve signature w/ SHA-512.
		 */
		ES512("ES512", "SHA512withECDSA", 512, new Type[] { Type.EC_P521, Type.EC_P256, Type.EC_P384 }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * Edwards Elliptic Curve Digital Signature Algorithm.
		 */
		EDDSA("EdDSA", "EdDSA", 0, new Type[] { Type.ED25519, Type.ED448 }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * RSASSA-PSS using SHA-256 and MGF1 with SHA-256.
		 */
		PS256("PS256", "RSASSA-PSS", 256, new Type[] { Type.RSASSA_PSS }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * RSASSA-PSS using SHA-384 and MGF1 with SHA-384.
		 */
		PS384("PS384", "RSASSA-PSS", 384, new Type[] { Type.RSASSA_PSS }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * RSASSA-PSS using SHA-512 and MGF1 with SHA-512.
		 */
		PS512("PS512", "RSASSA-PSS", 512, new Type[] { Type.RSASSA_PSS }, Use.SIGN,
				new Operation[] { Operation.SIGN, Operation.VERIFY }, Set.of()),

		/**
		 * RSAES-PKCS1-v1_5.
		 */
		@Deprecated
		RSA1_5("RSA1_5", "RSA/ECB/PKCS1Padding", 2048, new Type[] { Type.RSA }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP }, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * RSAES OAEP w/ default parameters.
		 */
		RSA_OAEP("RSA-OAEP", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", 2048, new Type[] { Type.RSA }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP }, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * RSAES OAEP w/ SHA-256 and MGF-1.
		 */
		RSA_OAEP_256("RSA-OAEP-256", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", 2048, new Type[] { Type.RSA },
				Use.ENCRYPT, new Operation[] { Operation.WRAP, Operation.UNWRAP }, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-128 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A128GCMKW("A128GCMKW", "AES/GCM/NoPadding", 128, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),

		/**
		 * AES-192 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A192GCMKW("A192GCMKW", "AES/GCM/NoPadding", 192, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),
		/**
		 * AES-256 GCM Key Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		A256GCMKW("A256GCMKW", "AES/GCM/NoPadding", 256, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.INITIALIZATION_VECTOR, Param.TAG)),

		/**
		 * AES-128 Key Wrap.
		 */
		A128KW("A128KW", "AESWrap", 128, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP }, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-192 Key Wrap.
		 */
		A192KW("A192KW", "AESWrap", 192, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP }, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * AES-256 Key Wrap.
		 */
		A256KW("A256KW", "AESWrap", 256, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP }, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * Direct use (as CEK).
		 */
		DIRECT("dir", null, 256, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.ENCRYPT, Operation.DECRYPT }, Set.of(Param.ENCRYPTION, Param.ZIP)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES("ECDH-ES", "ECDH", 0, new Type[] { Type.X25519, Type.X448, Type.EC_P256, Type.EC_P384, Type.EC_P521 },
				Use.ENCRYPT, new Operation[] { Operation.DERIVE_KEY },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-128 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A128KW("ECDH-ES+A128KW", "ECDH", 128,
				new Type[] { Type.X25519, Type.X448, Type.EC_P256, Type.EC_P384, Type.EC_P521 }, Use.ENCRYPT,
				new Operation[] { Operation.DERIVE_KEY },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-192 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A192KW("ECDH-ES+A192KW", "ECDH", 192,
				new Type[] { Type.X25519, Type.X448, Type.EC_P256, Type.EC_P384, Type.EC_P521 }, Use.ENCRYPT,
				new Operation[] { Operation.DERIVE_KEY },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-256 Key
		 * Wrap.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 */
		ECDH_ES_A256KW("ECDH-ES+A256KW", "ECDH", 256, new Type[] { Type.EC_P521, Type.EC_P256, Type.EC_P384 },
				Use.ENCRYPT, new Operation[] { Operation.DERIVE_KEY },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.EPHEMERAL_PUBLIC_KEY, Param.PARTY_UINFO, Param.PARTY_VINFO)),

		/**
		 * PBKDF2 with HMAC SHA-256 and AES128 key wrap.
		 */
		PBES2_HS256_A128KW("PBES2-HS256+A128KW", "PBKDF2WithHmacSHA256", 128, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.PASSWORD_SALT, Param.PASSWORD_COUNT)),

		/**
		 * PBKDF2 with HMAC SHA-384 and AES192 key wrap.
		 */
		PBES2_HS384_A192KW("PBES2-HS384+A192KW", "PBKDF2WithHmacSHA384", 192, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.PASSWORD_SALT, Param.PASSWORD_COUNT)),

		/**
		 * PBKDF2 with HMAC SHA-512 and AES192 key wrap.
		 */
		PBES2_HS512_A256KW("PBES2-HS512+A256KW", "PBKDF2WithHmacSHA512", 256, new Type[] { Type.RAW }, Use.ENCRYPT,
				new Operation[] { Operation.WRAP, Operation.UNWRAP },
				Set.of(Param.ENCRYPTION, Param.ZIP, Param.PASSWORD_SALT, Param.PASSWORD_COUNT));

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
		public final Type[] type;

		/**
		 * Key usage associated with this algorithm.
		 */
		public final Use use;

		/**
		 * Key usage associated with this algorithm.
		 */
		public final Operation[] keyOps;

		/**
		 * Set of encryption parameters used by this algorithm.
		 */
		public final Set<Param> encryptionParams;

		private Algorithm(String alg, String algorithm, int size, Type[] type, Use use, Operation[] keyOps,
				Set<Param> encryptionParams) {
			this.alg = alg;
			this.algorithm = algorithm;
			this.size = size;
			this.type = type;
			this.use = use;
			this.keyOps = keyOps;
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
				&& !Stream.of(algorithm.type).anyMatch(type::equals))
			throw new IllegalArgumentException("Illegal type " + type + " for algorithm " + algorithm);

		final var use = webKey.getUse();
		if (use != null //
				&& algorithm != null //
				&& !use.equals(algorithm.use))
			throw new IllegalArgumentException("Illegal use " + use + " for algorithm " + algorithm);

		final var ops = webKey.getOps();
		if (ops != null) {
			if (ops.size() > 2)
				throw new IllegalArgumentException("Illegal ops " + ops);
			else if (ops.size() == 2) {
				BiConsumer<Operation, Operation> checkPair = (a, b) -> {
					if (ops.contains(a) != (b != null && ops.contains(b)))
						throw new IllegalArgumentException("Illegal ops " + ops);
				};
				checkPair.accept(Operation.SIGN, Operation.VERIFY);
				checkPair.accept(Operation.ENCRYPT, Operation.DECRYPT);
				checkPair.accept(Operation.WRAP, Operation.UNWRAP);
				checkPair.accept(Operation.DERIVE_BITS, null);
				checkPair.accept(Operation.DERIVE_KEY, null);
			}

			if (algorithm != null //
					&& !Set.of(algorithm.keyOps).containsAll(ops))
				throw new IllegalArgumentException("Illegal ops " + ops + " for algorithm " + algorithm);

			if (use != null)
				if (ops.contains(Operation.SIGN) //
						|| ops.contains(Operation.VERIFY)) {
					if (use.equals(Use.ENCRYPT))
						throw new IllegalArgumentException("Illegal ops " + ops + " for use " + use);
				} else if (use.equals(Use.SIGN))
					throw new IllegalArgumentException("Illegal ops " + ops + " for use " + use);
		}

		final var cert = IuObject.convert(WebCertificateReference.verify(webKey), a -> a[0]);

		if (type.equals(Type.RAW)) {
			IuObject.require(webKey.getPrivateKey(), Objects::isNull, () -> "Unexpected private key");
			IuObject.require(webKey.getPublicKey(), Objects::isNull, () -> "Unexpected public key");
			IuObject.require(cert, Objects::isNull, () -> "Unexpected certificate");
			return null;
		}

		if (key != null)
			throw new IllegalArgumentException("Unexpected raw key data for " + type);

		var publicKey = IuObject.first(webKey.getPublicKey(), //
				IuObject.convert(cert, X509Certificate::getPublicKey), //
				() -> "public key doesn't match X.509 certificate");
		var params = algorithmParams(publicKey);

		final var privateKey = webKey.getPrivateKey();
		final var privateParams = algorithmParams(privateKey);
		if (params == null)
			params = privateParams;
		else if (params instanceof NamedParameterSpec) {
			final var namedSpec = (NamedParameterSpec) params;
			if (privateParams != null && //
					!namedSpec.getName().equals(((NamedParameterSpec) privateParams).getName()))
				throw new IllegalArgumentException("parameter spec mismatch");
		} else if (privateParams != null && //
				!params.equals(privateParams))
			throw new IllegalArgumentException("parameter spec mismatch");

		if ((publicKey instanceof RSAPublicKey) || (privateKey instanceof RSAPrivateKey)) {
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
		} else if ((publicKey != null || privateKey != null) && params == null)
			throw new IllegalArgumentException("Missing algorithm parameters");

		if (ops != null) {
			if (ops.contains(Operation.ENCRYPT) || ops.contains(Operation.DECRYPT))
				throw new IllegalArgumentException("Secret key required by ops " + ops);
			if (publicKey == null && (ops.contains(Operation.WRAP) //
					|| ops.contains(Operation.VERIFY)))
				throw new IllegalArgumentException("Public key required by ops " + ops);
			if (privateKey == null && (ops.contains(Operation.UNWRAP) //
					|| ops.contains(Operation.SIGN)))
				throw new IllegalArgumentException("Private key required by ops " + ops);
			if (ops.contains(Operation.DERIVE_KEY) && privateKey == null && publicKey == null)
				throw new IllegalArgumentException("Public or private key required by ops " + ops);
		}

		return publicKey;
	}

	/**
	 * Creates a new builder.
	 * 
	 * @param key JCE key
	 * @return {@link Builder}
	 */
	static Builder<?> builder(Key key) {
		if (key instanceof SecretKey)
			return WebKey.builder(Type.RAW).key(key.getEncoded());

		final WebKey.Builder<?> jwkBuilder;
		final var params = WebKey.algorithmParams(key);
		if (params == null)
			jwkBuilder = WebKey.builder(Type.from(key.getAlgorithm(), null));
		else
			jwkBuilder = WebKey.builder(Objects.requireNonNull(Type.from(params), params.toString()));

		if (key instanceof PrivateKey)
			jwkBuilder.key((PrivateKey) key);
		else
			jwkBuilder.key((PublicKey) key);

		return jwkBuilder;
	}

	/**
	 * Creates a new {@link Builder}.
	 * 
	 * @param type key type
	 * @return {@link Builder}
	 */
	static Builder<?> builder(Type type) {
		return Init.SPI.getJwkBuilder(type);
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
	static Builder<?> builder(Algorithm algorithm) {
		return builder(algorithm.type[0]).algorithm(algorithm);
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
		return builder(algorithm.type[0]).ephemeral(algorithm).build();
	}

	/**
	 * Parses a JSON Web Key (JWK).
	 * 
	 * @param jwk JSON Web Key
	 * @return {@link WebKey}
	 */
	static WebKey parse(String jwk) {
		return Init.SPI.parseJwk(jwk);
	}

	/**
	 * Parses a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	static Iterable<? extends WebKey> parseJwks(String jwks) {
		return Init.SPI.parseJwks(jwks);
	}

	/**
	 * Reads at least one PEM-encoded X509 certificate, and optionally a private
	 * key, and returns a JWK partial-key representation.
	 * 
	 * @param pem PEM-encoded certificate(s) and optional private key
	 * @return {@link WebKey}
	 */
	static WebKey pem(String pem) {
		final Queue<X509Certificate> certs = new ArrayDeque<>();
		PemEncoded privateKey = null;

		final var parsed = PemEncoded.parse(pem);
		while (parsed.hasNext()) {
			final var encoded = parsed.next();
			final var keyType = encoded.getKeyType();
			if (keyType.equals(PemEncoded.KeyType.CERTIFICATE))
				certs.offer(encoded.asCertificate());
			else if (keyType.equals(PemEncoded.KeyType.PRIVATE_KEY))
				privateKey = IuObject.once(privateKey, encoded);
		}

		final var publicKey = certs.peek().getPublicKey();
		final var builder = WebKey.builder(publicKey);
		builder.cert(certs.toArray(X509Certificate[]::new));
		if (privateKey != null)
			builder.key(privateKey.asPrivate(publicKey.getAlgorithm()));
		return builder.build();
	}

	/**
	 * Reads a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	static Iterable<? extends WebKey> readJwks(URI jwks) {
		return Init.SPI.readJwks(jwks);
	}

	/**
	 * Reads a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	static Iterable<? extends WebKey> readJwks(InputStream jwks) {
		return Init.SPI.readJwks(jwks);
	}

	/**
	 * Serializes {@link WebKey}s as a JSON Web Key Set.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @return serialized JWKS
	 */
	static String asJwks(Iterable<? extends WebKey> webKeys) {
		return Init.SPI.asJwks(webKeys);
	}

	/**
	 * Writes {@link WebKey} as a JSON Web Key.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @param out     {@link OutputStream}
	 */
	static void writeJwks(Iterable<? extends WebKey> webKeys, OutputStream out) {
		Init.SPI.writeJwks(webKeys, out);
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
