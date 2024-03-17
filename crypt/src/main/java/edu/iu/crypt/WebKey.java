package edu.iu.crypt;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.EnumSet;
import java.util.Set;

import javax.crypto.SecretKey;

import edu.iu.IuException;
import edu.iu.IuObject;
import iu.crypt.BaseWebKey;
import iu.crypt.Jose;
import iu.crypt.Jwk;

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
		 * Gets the type of a JCE public key.
		 * 
		 * @param publicKey public key
		 * @return key type
		 */
		public static Type of(PublicKey publicKey) {
			if (publicKey instanceof RSAPublicKey)
				if (publicKey.getAlgorithm().equals("RSASSA-PSS"))
					return Type.RSASSA_PSS;
				else
					return Type.RSA;
			else if (publicKey instanceof ECPublicKey) {
				final var ec = (ECPublicKey) publicKey;
				final var curveDescr = ec.getParams().getCurve().toString();
				if (curveDescr.contains("P-521"))
					return Type.EC_P521;
				else if (curveDescr.contains("P-384"))
					return Type.EC_P384;
				else
					return Type.EC_P256;
			} else
				return Type.RAW;
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
		HS256("HS256", "HmacSHA256", null, 256, Type.RAW, Use.SIGN),

		/**
		 * HMAC symmetric key signature w/ SHA-384.
		 */
		HS384("HS384", "HmacSHA384", null, 384, Type.RAW, Use.SIGN),

		/**
		 * HMAC symmetric key signature w/ SHA-512.
		 */
		HS512("HS512", "HmacSHA512", null, 512, Type.RAW, Use.SIGN),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-256.
		 */
		@Deprecated
		RS256("RS256", "SHA256withRSA", null, 256, Type.RSA, Use.SIGN),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-384.
		 */
		@Deprecated
		RS384("RS384", "SHA384withRSA", null, 384, Type.RSA, Use.SIGN),

		/**
		 * RSASSA-PKCS1-v1_5 using SHA-512.
		 */
		@Deprecated
		RS512("RS512", "SHA512withRSA", null, 512, Type.RSA, Use.SIGN),

		/**
		 * Elliptic Curve signature w/ SHA-256.
		 */
		ES256("ES256", "SHA256withECDSA", null, 256, Type.EC_P256, Use.SIGN),

		/**
		 * Elliptic Curve signature w/ SHA-384.
		 */
		ES384("ES384", "SHA384withECDSA", null, 384, Type.EC_P384, Use.SIGN),

		/**
		 * Elliptic Curve signature w/ SHA-512.
		 */
		ES512("ES512", "SHA512withECDSA", null, 512, Type.EC_P521, Use.SIGN),

		/**
		 * RSASSA-PSS using SHA-256 and MGF1 with SHA-256.
		 */
		PS256("PS256", "RSASSA-PSS", "SHA256withRSAandMGF1", 256, Type.RSASSA_PSS, Use.SIGN),

		/**
		 * RSASSA-PSS using SHA-384 and MGF1 with SHA-384.
		 */
		PS384("PS384", "RSASSA-PSS", "SHA384withRSAandMGF1", 384, Type.RSASSA_PSS, Use.SIGN),

		/**
		 * RSASSA-PSS using SHA-512 and MGF1 with SHA-512.
		 */
		PS512("PS512", "RSASSA-PSS", "SHA512withRSAandMGF1", 512, Type.RSASSA_PSS, Use.SIGN),

		/**
		 * RSAES-PKCS1-v1_5.
		 */
		RSA1_5("RSA1_5", "RSA", "RSA", 0, Type.RSA, Use.ENCRYPT),

		/**
		 * RSAES OAEP w/ default parameters.
		 */
		RSA_OAEP("RSA-OAEP", "RSA", "RSA", 0, Type.RSA, Use.ENCRYPT),

		/**
		 * RSAES OAEP w/ SHA-256 and MGF-1.
		 */
		RSA_OAEP_256("RSA-OAEP-256", "RSA", "RSA", 256, Type.RSA, Use.ENCRYPT),

		/**
		 * AES-128 Key Wrap.
		 */
		A128KW("A128KW", "AESWrap", null, 128, Type.RAW, Use.ENCRYPT),

		/**
		 * AES-192 Key Wrap.
		 */
		A192KW("A192KW", "AESWrap", null, 192, Type.RAW, Use.ENCRYPT),

		/**
		 * AES-256 Key Wrap.
		 */
		A256KW("A256KW", "AESWrap", null, 256, Type.RAW, Use.ENCRYPT),

		/**
		 * Direct use (as CEK).
		 */
		DIRECT("dir", "AES/GCM/NoPadding", "GCM", 0, Type.RAW, Use.ENCRYPT),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ defaults.
		 */
		ECDH_ES("ECDH-ES", "ECDH", "AESWrap", 128, Type.EC_P256, Use.ENCRYPT),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-128 Key
		 * Wrap.
		 */
		ECDH_ES_A128KW("ECDH-ES+A128KW", "ECDH", "AESWrap", 128, Type.EC_P256, Use.ENCRYPT),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-192 Key
		 * Wrap.
		 */
		ECDH_ES_A192KW("ECDH-ES+A192KW", "ECDH", "AESWrap", 192, Type.EC_P384, Use.ENCRYPT),

		/**
		 * Elliptic Curve Diffie-Hellman Ephemeral Static key agreement w/ AES-256 Key
		 * Wrap.
		 */
		ECDH_ES_A256KW("ECDH-ES+A256KW", "ECDH", "AESWrap", 256, Type.EC_P521, Use.ENCRYPT),

		/**
		 * AES-128 GCM Key Wrap.
		 */
		A128GCMKW("A128GCMKW", "AES/GCM/NoPadding", null, 128, Type.RAW, Use.ENCRYPT),

		/**
		 * AES-192 GCM Key Wrap.
		 */
		A192GCMKW("A192GCMKW", "AES/GCM/NoPadding", null, 192, Type.RAW, Use.ENCRYPT),

		/**
		 * AES-256 GCM Key Wrap.
		 */
		A256GCMKW("A256GCMKW", "AES/GCM/NoPadding", null, 256, Type.RAW, Use.ENCRYPT),

		/**
		 * PBES2 with HMAC SHA-256 and "A128KW" wrapping.
		 */
		PBES2_HS256_A128KW("PBES2-HS256+A128KW", "PBEWithHmacSHA256AndAES_128", null, 128, Type.RAW, Use.ENCRYPT),

		/**
		 * PBES2 with HMAC SHA-384 and "A192KW" wrapping.
		 */
		PBES2_HS384_A192KW("PBES2-HS384+A192KW", "PBEWithHmacSHA384AndAES_192", null, 192, Type.RAW, Use.ENCRYPT),

		/**
		 * PBES2 with HMAC SHA-512 and "A256KW" wrapping.
		 */
		PBES2_HS512_A256KW("PBES2-HS512+A256KW", "PBEWithHmacSHA512AndAES_256", null, 256, Type.RAW, Use.ENCRYPT);

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
		 * JCE algorithm name.
		 */
		public final String algorithm;

		/**
		 * JCE auxiliary algorithm name.
		 */
		public final String auxiliaryAlgorithm;

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

		private Algorithm(String alg, String algorithm, String auxiliaryAlgorithm, int size, Type type, Use use) {
			this.alg = alg;
			this.algorithm = algorithm;
			this.auxiliaryAlgorithm = auxiliaryAlgorithm;
			this.size = size;
			this.type = type;
			this.use = use;
		}
	}

	/**
	 * Resolves a {@link WebKey} from a {@link WebSignatureHeader}.
	 * 
	 * <ol>
	 * <li>Direct attachment {@link WebSignatureHeader#getKey}, <em>may</em> be
	 * validated against {@link WebSignatureHeader#getKeyId}.</li>
	 * <li>By GET request to {@link WebSignatureHeader#getKeySetUri()},
	 * <em>must</em> be selected by {@link WebSignatureHeader#getKeyId()}.</li>
	 * <li>Derived from the first certificate in
	 * {@link WebSignatureHeader#getCertificateChain()}</li>
	 * <li>Derived from the first certificate in
	 * {@link WebSignatureHeader#getCertificateChain()}</li>
	 * <li>If used, the certificate will be further identified by
	 * {@link WebSignatureHeader#getKeyId()} and
	 * {@link WebSignatureHeader#getAlgorithm()}, and matched against
	 * {@link WebSignatureHeader#getCertificateThumbprint()} and/or
	 * {@link WebSignatureHeader#getCertificateSha256Thumbprint}.</li>
	 * </ol>
	 * 
	 * @param header encryption or signature header
	 * @return well-known public key defined by the header
	 */
	static WebKey from(WebSignatureHeader header) {
		return Jose.getKey(header);
	}

	/**
	 * Gets the well-known {public-only) version of a web key.
	 * 
	 * @param webKey full web key
	 * @return key for which {@link #getPrivateKey()} and {@link #getKey()} always
	 *         return null, otherwise equal.
	 */
	static WebKey wellKnown(WebKey webKey) {
		if (webKey == null)
			return null;
		else if (webKey.getPrivateKey() == null && webKey.getKey() == null)
			return webKey;
		else
			return (WebKey) Proxy.newProxyInstance(WebKey.class.getClassLoader(), new Class<?>[] { WebKey.class },
					(proxy, method, args) -> {
						switch (method.getName()) {
						case "getKey":
							return null;
						case "getPrivateKey":
							return null;
						case "hashCode":
							return System.identityHashCode(proxy);
						case "equals":
							return proxy == args[0];
						case "toString":
							return Jwk.asJwk((WebKey) proxy);

						default:
							return IuException.checkedInvocation(() -> method.invoke(webKey, args));
						}
					});
	}

	/**
	 * Creates a web key for a well-known certificate chain.
	 * 
	 * @param id        key ID
	 * @param certChain certificate chain, must include at least one certificate
	 * 
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, X509Certificate... certChain) {
		return BaseWebKey.from(id, null, Type.of(certChain[0].getPublicKey()), null, null, certChain);
	}

	/**
	 * Creates a web key for a well-known certificate chain.
	 * 
	 * @param id        key ID
	 * @param use       public key use
	 * @param certChain certificate chain, must include at least one certificate
	 * 
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Use use, X509Certificate... certChain) {
		return BaseWebKey.from(id, null, Type.of(certChain[0].getPublicKey()), use, null, certChain);
	}

	/**
	 * Creates a web key for a well-known certificate chain.
	 * 
	 * @param id        key ID
	 * @param algorithm algorithm
	 * @param certChain certificate chain, must include at least one certificate
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Algorithm algorithm, X509Certificate... certChain) {
		return BaseWebKey.from(id, algorithm, algorithm.type, algorithm.use, null, certChain);
	}

	/**
	 * Creates a web key for a locally managed certificate.
	 * 
	 * @param id         key ID
	 * @param privateKey private key
	 * @param certChain  certificate chain, must include at least one certificate
	 *                   with public key related to the given private key
	 * 
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, PrivateKey privateKey, X509Certificate... certChain) {
		return BaseWebKey.from(id, null, Type.of(certChain[0].getPublicKey()), null, privateKey, certChain);
	}

	/**
	 * Creates a web key for a locally managed certificate.
	 * 
	 * @param id         key ID
	 * @param use        public key use
	 * @param privateKey private key
	 * @param certChain  certificate chain, must include at least one certificate
	 *                   with public key related to the given private key
	 * 
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Use use, PrivateKey privateKey, X509Certificate... certChain) {
		return BaseWebKey.from(id, null, Type.of(certChain[0].getPublicKey()), use, privateKey, certChain);
	}

	/**
	 * Creates a web key for a locally managed certificate.
	 * 
	 * @param id         key ID
	 * @param algorithm  algorithm
	 * @param privateKey private key
	 * @param certChain  certificate chain, must include at least one certificate
	 *                   with public key related to the given private key
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Algorithm algorithm, PrivateKey privateKey, X509Certificate... certChain) {
		return BaseWebKey.from(id, algorithm, algorithm.type, algorithm.use, privateKey, certChain);
	}

	/**
	 * Creates a web key from a public/private key pair.
	 * 
	 * @param id      key ID
	 * @param keyPair public/private key pair
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, KeyPair keyPair) {
		return BaseWebKey.from(id, null, Type.of(keyPair.getPublic()), null, keyPair);
	}

	/**
	 * Creates a web key from a public/private key pair.
	 * 
	 * @param id      key ID
	 * @param use     public key use
	 * @param keyPair public/private key pair
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Use use, KeyPair keyPair) {
		return BaseWebKey.from(id, null, Type.of(keyPair.getPublic()), use, keyPair);
	}

	/**
	 * Creates a web key from a public/private key pair.
	 * 
	 * @param id        key ID
	 * @param algorithm algorithm
	 * @param keyPair   public/private key pair
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Algorithm algorithm, KeyPair keyPair) {
		return BaseWebKey.from(id, algorithm, algorithm.type, algorithm.use, keyPair);
	}

	/**
	 * Creates a web key from PEM-encoded key and/or certificate data.
	 * 
	 * @param id         key ID
	 * @param type       key type
	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
	 *                   concatenated
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Type type, String pemEncoded) {
		return BaseWebKey.from(id, null, type, null, pemEncoded);
	}

	/**
	 * Creates a web key from PEM-encoded key and/or certificate data.
	 * 
	 * @param id         key ID
	 * @param type       key type
	 * @param use        public key use
	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
	 *                   concatenated
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Type type, Use use, String pemEncoded) {
		return BaseWebKey.from(id, null, type, use, pemEncoded);
	}

	/**
	 * Reads a web key from PEM-encoded data.
	 * 
	 * @param id         key ID
	 * @param algorithm  algorithm
	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
	 *                   concatenated
	 * @return {@link WebKey}
	 */
	static WebKey from(String id, Algorithm algorithm, String pemEncoded) {
		return BaseWebKey.from(id, algorithm, algorithm.type, algorithm.use, pemEncoded);
	}

	/**
	 * Creates a JSON Web Key (JWK).
	 * 
	 * @param jwk JWK serialized form
	 * @return {@link WebKey}
	 */
	static WebKey readJwk(String jwk) {
		return Jwk.readJwk(jwk);
	}

	/**
	 * Serializes a {@link WebKey} as JWK.
	 * 
	 * @param webKey web key
	 * @return serialized JWK
	 */
	static String asJwk(WebKey webKey) {
		return Jwk.asJwk(webKey);
	}

	/**
	 * Reads a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return {@link WebKey}
	 */
	static Iterable<WebKey> readJwks(String jwks) {
		return Jwk.readJwks(jwks);
	}

	/**
	 * Reads a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return {@link WebKey}
	 */
	static Iterable<WebKey> readJwks(URI jwks) {
		return BaseWebKey.readJwks(jwks);
	}

	/**
	 * Gets web key from a well known key set by URI and id.
	 * 
	 * @param uri   Well-known key set URI
	 * @param keyId Key ID
	 * @return {@link WebKey}
	 */
	static WebKey readJwk(URI uri, String keyId) {
		return BaseWebKey.readJwk(uri, keyId);
	}

	/**
	 * Serializes {@link WebKey}s as a JWKS.
	 * 
	 * @param webKeys web keys
	 * @return serialized JWKS
	 */
	static String asJwks(Iterable<WebKey> webKeys) {
		return Jwk.asJwks(webKeys);
	}

	/**
	 * Gets a certificate chain by URI.
	 * 
	 * @param uri Well-known certificate chain URI.
	 * @return certificate chain
	 */
	static X509Certificate[] getCertificateChain(URI uri) {
		return BaseWebKey.getCertificateChain(uri);
	}

	/**
	 * Gets the key type.
	 * 
	 * @return key type
	 */
	default Type getType() {
		final var alg = getAlgorithm();
		if (alg != null)
			return alg.type;

		return Type.of(getPublicKey());
	}

	/**
	 * Gets the public key use.
	 * 
	 * @return public key use.
	 */
	default Use getUse() {
		final var alg = getAlgorithm();
		if (alg != null)
			return alg.use;

		return null;
	}

	/**
	 * Gets the raw key data for use when {@link Type#RAW}.
	 * 
	 * @return {@link SecretKey}; null if {@link #getType()} is not {@link Type#RAW}
	 */
	default byte[] getKey() {
		return null;
	}

	/**
	 * Gets the JCE private key implementation.
	 * 
	 * @return {@link PrivateKey}; null if not known or {@link #getType()} is
	 *         {@link Type#RAW}
	 */
	default PrivateKey getPrivateKey() {
		return null;
	}

	/**
	 * Gets the JCE public key implementation.
	 * 
	 * @return {@link PublicKey}; null if {@link #getType()} is {@link Type#RAW},
	 *         defaults to the public key of the first {@link #getCertificateChain()
	 *         chained certificate}.
	 */
	default PublicKey getPublicKey() {
		final var cert = getCertificateChain();
		if (cert != null && cert.length > 0)
			return cert[0].getPublicKey();
		else
			return null;
	}

	/**
	 * Gets the key operations.
	 * 
	 * @return ops; <em>should</em> be validated, <em>may</em> be null (default) to
	 *         expect validation to be skipped
	 */
	default Set<Op> getOps() {
		return null;
	}

	/**
	 * Gets the algorithm.
	 * 
	 * @return algorithm
	 */
	default Algorithm getAlgorithm() {
		return null;
	}

	/**
	 * Gets the Key ID.
	 * 
	 * @return JSON kid attribute value
	 */
	default String getId() {
		return "default";
	}

}
