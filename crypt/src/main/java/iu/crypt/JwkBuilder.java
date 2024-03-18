package iu.crypt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.RSAMultiPrimePrivateCrtKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import javax.crypto.SecretKey;

import edu.iu.IuCacheMap;
import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Builder;
import edu.iu.crypt.WebKey.Op;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * JWK {@link Builder} implementation.
 */
public class JwkBuilder implements Builder {

	private static Map<URI, Jwk[]> JWKS_CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	/**
	 * Gets well known key set by URI.
	 * 
	 * @param uri   Well-known key set URI
	 * @param keyId Key ID
	 * @return {@link WebKey}
	 */
	public static Stream<Jwk> readJwks(URI uri) {
		var jwks = JWKS_CACHE.get(uri);
		if (jwks == null) {
			jwks = IuException.unchecked(() -> IuHttp.get(uri, IuHttp.validate(JwkBuilder::readJwks, IuHttp.OK)));
			JWKS_CACHE.put(uri, jwks);
		}
		return Stream.of(jwks);
	}

	/**
	 * Parses a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	public static Stream<Jwk> parseJwks(String jwks) {
		return IuJson.parse(jwks).asJsonObject().getJsonArray("keys").stream()
				.map(a -> new JwkBuilder().jwk(a).build());
	}

	/**
	 * Parses a JSON Web Key (JWK).
	 * 
	 * @param jwk JSON Web Key
	 * @return {@link WebKey}
	 */
	public static Jwk parse(String jwk) {
		return new JwkBuilder().jwk(IuJson.parse(jwk)).build();
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

	/**
	 * Creates an Elliptic Curve {@link KeyPair} from parsed JWK attributes.
	 * 
	 * @param parsedJwk parsed JWK attributes
	 * @return {@link KeyPair}
	 */
	static KeyPair readEC(JsonObject parsedJwk) {
		return IuException.unchecked(() -> {
			final var type = Type.from(parsedJwk.getString("kty"), parsedJwk.getString("crv"));

			final var algorithmParamters = AlgorithmParameters.getInstance("EC");
			algorithmParamters.init(new ECGenParameterSpec(type.ecParam));
			final var spec = algorithmParamters.getParameterSpec(ECParameterSpec.class);

			final var keyFactory = KeyFactory.getInstance("EC");
			final var w = new ECPoint(EncodingUtils.getBigInt(parsedJwk, "x"), EncodingUtils.getBigInt(parsedJwk, "y"));
			final var pub = keyFactory.generatePublic(new ECPublicKeySpec(w, spec));

			final PrivateKey priv;
			if (parsedJwk.containsKey("d"))
				priv = keyFactory.generatePrivate(new ECPrivateKeySpec(EncodingUtils.getBigInt(parsedJwk, "d"), spec));
			else
				priv = null;

			return new KeyPair(pub, priv);
		});
	}

	/**
	 * Creates an RSA {@link KeyPair} from parsed JWK attributes.
	 * 
	 * @param parsedJwk parsed JWK attributes
	 * @return {@link KeyPair}
	 */
	static KeyPair readRSA(JsonObject parsedJwk) {
		return IuException.unchecked(() -> {
			final var keyFactory = KeyFactory.getInstance("RSA");

			final var modulus = EncodingUtils.getBigInt(parsedJwk, "n");
			final var exponent = EncodingUtils.getBigInt(parsedJwk, "e");
			final var pub = keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));

			final PrivateKey priv;
			if (parsedJwk.containsKey("d")) {
				final KeySpec keySpec;
				final var privateExponent = EncodingUtils.getBigInt(parsedJwk, "d");
				if (parsedJwk.containsKey("p")) {
					final var primeP = EncodingUtils.getBigInt(parsedJwk, "p");
					final var primeQ = EncodingUtils.getBigInt(parsedJwk, "q");
					final var primeExponentP = EncodingUtils.getBigInt(parsedJwk, "dp");
					final var primeExponentQ = EncodingUtils.getBigInt(parsedJwk, "dq");
					final var crtCoefficient = EncodingUtils.getBigInt(parsedJwk, "qi");

					if (parsedJwk.containsKey("oth"))
						// TODO: identify a multi-prime test case
						// * JCE doesn't generate multi-prime RSA keys
						// * JCE can't read multi-prime key exported from OpenSSL as PKCS8
						// * OpenSSL doesn't export as JWK
						throw new UnsupportedOperationException();

					keySpec = new RSAPrivateCrtKeySpec(modulus, exponent, privateExponent, primeP, primeQ,
							primeExponentP, primeExponentQ, crtCoefficient);
				} else
					keySpec = new RSAPrivateKeySpec(modulus, privateExponent);

				priv = keyFactory.generatePrivate(keySpec);
			} else
				priv = null;

			return new KeyPair(pub, priv);
		});
	}

	private static Jwk[] readJwks(InputStream jwks) {
		return IuJson.parse(jwks).asJsonObject().getJsonArray("keys").stream().map(a -> new JwkBuilder().jwk(a).build())
				.toArray(Jwk[]::new);
	}

	private String id;
	private Type type;
	private Use use;
	private Algorithm algorithm;
	private Set<Op> ops;
	private byte[] key;
	private PrivateKey privateKey;
	private PublicKey publicKey;
	private URI certificateUri;
	private X509Certificate[] certificateChain;
	private byte[] certificateThumbprint;
	private byte[] certificateSha256Thumbprint;

	@Override
	public JwkBuilder id(String id) {
		Objects.requireNonNull(id);

		if (this.id == null)
			this.id = id;
		else if (!id.equals(this.id))
			throw new IllegalStateException("ID already set");

		return this;
	}

	@Override
	public JwkBuilder type(Type type) {
		Objects.requireNonNull(type);

		if (algorithm != null //
				&& !type.equals(algorithm.type))
			throw new IllegalArgumentException("Incorrect type " + type + " for algorithm " + algorithm);

		if (this.type == null)
			this.type = type;
		else if (!type.equals(this.type))
			throw new IllegalStateException("Type already set " + this.type);

		return this;
	}

	@Override
	public JwkBuilder use(Use use) {
		Objects.requireNonNull(use);

		if (algorithm != null //
				&& !use.equals(algorithm.use))
			throw new IllegalArgumentException("Incorrect use " + use + " for algorithm " + algorithm);

		if (this.use == null)
			this.use = use;
		else if (!use.equals(this.use))
			throw new IllegalStateException("Use already set to " + this.use);

		return this;
	}

	@Override
	public JwkBuilder algorithm(Algorithm algorithm) {
		Objects.requireNonNull(algorithm);

		if (type != null //
				&& !type.equals(algorithm.type))
			throw new IllegalArgumentException("Incorrect type " + type + " for algorithm " + algorithm);

		if (use != null //
				&& !use.equals(algorithm.use))
			throw new IllegalArgumentException("Incorrect use " + use + " for algorithm " + algorithm);

		if (this.algorithm == null)
			this.algorithm = algorithm;
		else if (!algorithm.equals(this.algorithm))
			throw new IllegalStateException("Algorithm already set to " + this.use);

		return this;
	}

	@Override
	public JwkBuilder ops(Op... ops) {
		Objects.requireNonNull(ops);

		final var opSet = Set.of(ops);
		if (this.ops == null)
			this.ops = opSet;
		else if (!ops.equals(this.ops))
			throw new IllegalStateException("Ops already set to " + this.ops);

		return this;
	}

	@Override
	public JwkBuilder key(byte[] key) {
		type(Type.RAW);
		Objects.requireNonNull(key);

		if (this.key == null)
			this.key = key;
		else if (!Arrays.equals(key, this.key))
			throw new IllegalStateException("Raw key data already set");

		return this;
	}

	@Override
	public JwkBuilder key(SecretKey secretKey) {
		return key(secretKey.getEncoded());
	}

	@Override
	public JwkBuilder pub(PublicKey publicKey) {
		if (type != null)
			switch (type) {
			case EC_P256:
			case EC_P384:
			case EC_P521:
				if (!(publicKey instanceof ECPublicKey))
					throw new IllegalArgumentException("Expected ECPublicKey for " + type);
				break;

			case RSA:
			case RSASSA_PSS:
				if (!(publicKey instanceof RSAPublicKey))
					throw new IllegalArgumentException("Expected RSAPublicKey for " + type);
				break;

			case RAW:
			default:
				throw new IllegalStateException("Cannot use public/private key with type RAW");
			}

		Objects.requireNonNull(publicKey, "Missing public key");

		if (this.privateKey != null)
			throw new IllegalStateException("Private key has been set");

		if (this.publicKey == null)
			this.publicKey = publicKey;
		else if (!publicKey.equals(this.publicKey))
			throw new IllegalStateException("Public key already set");

		return this;
	}

	@Override
	public JwkBuilder pair(KeyPair keyPair) {
		final var privateKey = keyPair.getPrivate();
		var publicKey = keyPair.getPublic();
		if (publicKey == null)
			if (privateKey instanceof RSAPrivateCrtKey) {
				final var crt = (RSAPrivateCrtKey) privateKey;
				publicKey = IuException.unchecked(() -> getKeyFactory(type)
						.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent())));
			} else if (privateKey instanceof RSAMultiPrimePrivateCrtKeySpec) {
				final var crt = (RSAMultiPrimePrivateCrtKeySpec) privateKey;
				publicKey = IuException.unchecked(() -> getKeyFactory(type)
						.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent())));
			}

		pub(publicKey);
		acceptPrivate(privateKey);
		return this;
	}

	@Override
	public JwkBuilder cert(URI uri) {
		acceptCertChain(PemEncoded.getCertificateChain(uri));
		this.certificateUri = uri;
		return this;
	}

	@Override
	public JwkBuilder cert(X509Certificate... chain) {
		acceptCertChain(chain);
		this.certificateChain = chain;
		return this;
	}

	@Override
	public JwkBuilder cert(PrivateKey privateKey, X509Certificate... chain) {
		cert(chain);
		acceptPrivate(privateKey);
		return this;
	}

	@Override
	public JwkBuilder x5t(byte[] certificateThumbprint) {
		Objects.requireNonNull(certificateThumbprint);

		final var cert = getCert();
		if (cert != null //
				&& !Arrays.equals(certificateThumbprint, IuCrypt.sha1(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-1 thumbprint mismatch");

		this.certificateThumbprint = certificateThumbprint;
		return null;
	}

	@Override
	public JwkBuilder x5t256(byte[] certificateSha256Thumbprint) {
		Objects.requireNonNull(certificateSha256Thumbprint);

		final var cert = getCert();
		if (cert != null //
				&& !Arrays.equals(certificateSha256Thumbprint, IuCrypt.sha256(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-256 thumbprint mismatch");

		this.certificateSha256Thumbprint = certificateSha256Thumbprint;
		return null;
	}

	@Override
	public JwkBuilder pem(InputStream pemEncoded) {
		final var pemIterator = PemEncoded.parse(pemEncoded);

		PrivateKey privateKey = null;
		PublicKey publicKey = null;
		final Queue<X509Certificate> certificateChain = new ArrayDeque<>();
		while (pemIterator.hasNext()) {
			final var segment = pemIterator.next();
			switch (segment.keyType) {
			case PUBLIC_KEY:
				Objects.requireNonNull(type, "Type must be supplied before PEM encoded key data");
				if (publicKey != null)
					throw new IllegalArgumentException("Expected at most one public key");
				else
					publicKey = segment.asPublic(type);
				break;

			case PRIVATE_KEY:
				Objects.requireNonNull(type, "Type must be supplied before PEM encoded key data");
				if (privateKey != null)
					throw new IllegalArgumentException("Expected at most one private key");
				else
					privateKey = segment.asPrivate(type);
				break;

			default:
				certificateChain.offer(segment.asCertificate());
			}
		}

		if (publicKey != null) {
			pair(new KeyPair(publicKey, privateKey));
			if (!certificateChain.isEmpty())
				cert(certificateChain.toArray(X509Certificate[]::new));
		} else if (!certificateChain.isEmpty()) {
			cert(privateKey, certificateChain.toArray(X509Certificate[]::new));
		} else
			pair(new KeyPair(publicKey, privateKey));

		return this;
	}

	@Override
	public JwkBuilder pem(String pemEncoded) {
		return pem(new ByteArrayInputStream(EncodingUtils.utf8(pemEncoded)));
	}

	private JwkBuilder jwk(JsonValue json) {
		final var jwk = json.asJsonObject();

		final var kty = jwk.getString("kty");
		final var crv = IuJson.text(jwk, "crv");
		type(Type.from(kty, crv));

		IuJson.text(jwk, "kid", this::id);
		IuJson.text(jwk, "use", a -> use(Use.from(a)));
		IuJson.text(jwk, "alg", a -> algorithm(Algorithm.from(a)));
		IuJson.get(jwk, "key_ops",
				a -> ops(a.asJsonArray().stream().map(IuJson::asText).map(Op::from).toArray(Op[]::new)));

		switch (type) {
		case EC_P256:
		case EC_P384:
		case EC_P521: {
			pair(readEC(jwk));
			break;
		}

		case RSASSA_PSS:
		case RSA: {
			pair(readRSA(jwk));
			break;
		}
		default:
			key(EncodingUtils.getBytes(jwk, "k"));
			break;
		}

		IuJson.text(jwk, "x5u", a -> cert(URI.create(a)));
		IuJson.get(jwk, "x5c", a -> cert(a.asJsonArray().stream().map(IuJson::asText).map(PemEncoded::parse)
				.map(i -> i.next()).map(PemEncoded::asCertificate).toArray(X509Certificate[]::new)));
		IuJson.text(jwk, "x5t", a -> x5t(EncodingUtils.base64(a)));
		IuJson.text(jwk, "x5t#S256", a -> x5t256(EncodingUtils.base64(a)));

		return this;
	}

	@Override
	public Jwk build() {
		return new Jwk(id, type, use, key, publicKey, privateKey, ops, algorithm, certificateUri, certificateChain,
				certificateThumbprint, certificateSha256Thumbprint);
	}

	private void acceptPrivate(PrivateKey privateKey) {
		if (privateKey != null)
			if (publicKey instanceof ECPublicKey) {
				if (!(privateKey instanceof ECPrivateKey))
					throw new IllegalArgumentException("Expected ECPrivateKey");
				if (!((ECPrivateKey) privateKey).getParams().equals(((ECPublicKey) publicKey).getParams()))
					throw new IllegalArgumentException("EC public and private keys doesn't match");
			} else if (publicKey instanceof RSAPublicKey) {
				if (!(privateKey instanceof RSAPrivateKey))
					throw new IllegalArgumentException("Expected RSAPrivateKey");
				if (!((RSAPrivateKey) privateKey).getModulus().equals(((RSAPublicKey) publicKey).getModulus()))
					throw new IllegalArgumentException("RSA public and private keys doesn't match");
			} else if (publicKey != null)
				throw new IllegalArgumentException("Invalid private key");

		this.privateKey = privateKey;
	}

	private void acceptCertChain(X509Certificate[] certChain) {
		if (this.certificateChain != null //
				&& !Arrays.equals(certChain, this.certificateChain))
			throw new IllegalStateException("Certificate chain mismatch");

		final var cert = certChain[0];
		if (certificateThumbprint != null //
				&& !Arrays.equals(certificateThumbprint, IuCrypt.sha1(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-1 thumbprint mismatch");
		if (certificateSha256Thumbprint != null //
				&& !Arrays.equals(certificateSha256Thumbprint, IuCrypt.sha256(IuException.unchecked(cert::getEncoded))))
			throw new IllegalArgumentException("SHA-256 thumbprint mismatch");

		final var pub = cert.getPublicKey();
		if (this.publicKey == null)
			pub(pub);
		else if (!pub.equals(this.publicKey))
			throw new IllegalStateException("Public key mismatch");
	}

	private X509Certificate getCert() {
		if (this.certificateChain != null)
			return certificateChain[0];
		else if (this.certificateUri != null)
			return PemEncoded.getCertificateChain(certificateUri)[0];
		else
			return null;
	}

	// TODO: REVIEW LINE

//	@Override
//	public String toString() {
//		return Jwk.asJwk(this);
//	}
//
//	/**
//	 * Resolves a {@link WebKey} from a {@link WebSignatureHeader}.
//	 * 
//	 * <ol>
//	 * <li>Direct attachment {@link WebSignatureHeader#getKey}, <em>may</em> be
//	 * validated against {@link WebSignatureHeader#getKeyId}.</li>
//	 * <li>By GET request to {@link WebSignatureHeader#getKeySetUri()},
//	 * <em>must</em> be selected by {@link WebSignatureHeader#getKeyId()}.</li>
//	 * <li>Derived from the first certificate in
//	 * {@link WebSignatureHeader#getCertificateChain()}</li>
//	 * <li>Derived from the first certificate in
//	 * {@link WebSignatureHeader#getCertificateChain()}</li>
//	 * <li>If used, the certificate will be further identified by
//	 * {@link WebSignatureHeader#getKeyId()} and
//	 * {@link WebSignatureHeader#getAlgorithm()}, and matched against
//	 * {@link WebSignatureHeader#getCertificateThumbprint()} and/or
//	 * {@link WebSignatureHeader#getCertificateSha256Thumbprint}.</li>
//	 * </ol>
//	 * 
//	 * @param header encryption or signature header
//	 * @return well-known public key defined by the header
//	 */
//	static WebKey from(WebSignatureHeader header) {
//		return Jose.getKey(header);
//	}
//
//	/**
//	 * Gets the well-known {public-only) version of a web key.
//	 * 
//	 * @param webKey full web key
//	 * @return key for which {@link #getPrivateKey()} and {@link #getKey()} always
//	 *         return null, otherwise equal.
//	 */
//	static WebKey wellKnown(WebKey webKey) {
//		if (webKey == null)
//			return null;
//		else if (webKey.getPrivateKey() == null && webKey.getKey() == null)
//			return webKey;
//		else
//			return (WebKey) Proxy.newProxyInstance(WebKey.class.getClassLoader(), new Class<?>[] { WebKey.class },
//					(proxy, method, args) -> {
//						switch (method.getName()) {
//						case "getKey":
//							return null;
//						case "getPrivateKey":
//							return null;
//						case "hashCode":
//							return System.identityHashCode(proxy);
//						case "equals":
//							return proxy == args[0];
//						case "toString":
//							return Jwk.asJwk((WebKey) proxy);
//
//						default:
//							return IuException.checkedInvocation(() -> method.invoke(webKey, args));
//						}
//					});
//	}

//	/**
//	 * Creates a web key from a secret key.
//	 * 
//	 * @param id  key ID
//	 * @param key secret key
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, SecretKey key) {
//		return JwkBuilder.from(id, null, key.getEncoded());
//	}
//
//	/**
//	 * Creates a web key for a well-known certificate chain.
//	 * 
//	 * @param id        key ID
//	 * @param certChain certificate chain, must include at least one certificate
//	 * 
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, X509Certificate... certChain) {
//		return JwkBuilder.from(id, null, Type.of(certChain[0].getPublicKey()), null, null, certChain);
//	}
//
//	/**
//	 * Creates a web key for a well-known certificate chain.
//	 * 
//	 * @param id        key ID
//	 * @param use       public key use
//	 * @param certChain certificate chain, must include at least one certificate
//	 * 
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Use use, X509Certificate... certChain) {
//		return JwkBuilder.from(id, null, Type.of(certChain[0].getPublicKey()), use, null, certChain);
//	}
//
//	/**
//	 * Creates a web key for a well-known certificate chain.
//	 * 
//	 * @param id        key ID
//	 * @param algorithm algorithm
//	 * @param certChain certificate chain, must include at least one certificate
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Algorithm algorithm, X509Certificate... certChain) {
//		return JwkBuilder.from(id, algorithm, algorithm.type, algorithm.use, null, certChain);
//	}
//
//	/**
//	 * Creates a web key for a locally managed certificate.
//	 * 
//	 * @param id         key ID
//	 * @param privateKey private key
//	 * @param certChain  certificate chain, must include at least one certificate
//	 *                   with public key related to the given private key
//	 * 
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, PrivateKey privateKey, X509Certificate... certChain) {
//		return JwkBuilder.from(id, null, Type.of(certChain[0].getPublicKey()), null, privateKey, certChain);
//	}
//
//	/**
//	 * Creates a web key for a locally managed certificate.
//	 * 
//	 * @param id         key ID
//	 * @param use        public key use
//	 * @param privateKey private key
//	 * @param certChain  certificate chain, must include at least one certificate
//	 *                   with public key related to the given private key
//	 * 
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Use use, PrivateKey privateKey, X509Certificate... certChain) {
//		return JwkBuilder.from(id, null, Type.of(certChain[0].getPublicKey()), use, privateKey, certChain);
//	}
//
//	/**
//	 * Creates a web key for a locally managed certificate.
//	 * 
//	 * @param id         key ID
//	 * @param algorithm  algorithm
//	 * @param privateKey private key
//	 * @param certChain  certificate chain, must include at least one certificate
//	 *                   with public key related to the given private key
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Algorithm algorithm, PrivateKey privateKey, X509Certificate... certChain) {
//		return JwkBuilder.from(id, algorithm, algorithm.type, algorithm.use, privateKey, certChain);
//	}
//
//	/**
//	 * Creates a web key from a public/private key pair.
//	 * 
//	 * @param id      key ID
//	 * @param keyPair public/private key pair
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, KeyPair keyPair) {
//		return JwkBuilder.from(id, null, Type.of(keyPair.getPublic()), null, keyPair);
//	}
//
//	/**
//	 * Creates a web key from a public/private key pair.
//	 * 
//	 * @param id      key ID
//	 * @param use     public key use
//	 * @param keyPair public/private key pair
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Use use, KeyPair keyPair) {
//		return JwkBuilder.from(id, null, Type.of(keyPair.getPublic()), use, keyPair);
//	}
//
//	/**
//	 * Creates a web key from a public/private key pair.
//	 * 
//	 * @param id        key ID
//	 * @param algorithm algorithm
//	 * @param keyPair   public/private key pair
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Algorithm algorithm, KeyPair keyPair) {
//		return JwkBuilder.from(id, algorithm, algorithm.type, algorithm.use, keyPair);
//	}
//
//	/**
//	 * Creates a web key from PEM-encoded key and/or certificate data.
//	 * 
//	 * @param id         key ID
//	 * @param type       key type
//	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
//	 *                   concatenated
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Type type, String pemEncoded) {
//		return JwkBuilder.from(id, null, type, null, pemEncoded);
//	}
//
//	/**
//	 * Creates a web key from PEM-encoded key and/or certificate data.
//	 * 
//	 * @param id         key ID
//	 * @param type       key type
//	 * @param use        public key use
//	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
//	 *                   concatenated
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Type type, Use use, String pemEncoded) {
//		return JwkBuilder.from(id, null, type, use, pemEncoded);
//	}
//
//	/**
//	 * Reads a web key from PEM-encoded data.
//	 * 
//	 * @param id         key ID
//	 * @param algorithm  algorithm
//	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
//	 *                   concatenated
//	 * @return {@link WebKey}
//	 */
//	static WebKey from(String id, Algorithm algorithm, String pemEncoded) {
//		return JwkBuilder.from(id, algorithm, algorithm.type, algorithm.use, pemEncoded);
//	}
//
//	static WebKey ephermeral(Type type) {
//		return ephermeral(type, 0);
//	}
//
//	static WebKey ephermeral(Type type, int size) {
//		switch (type) {
//		case RAW: {
//			final var keygen = IuException.unchecked(() -> KeyGenerator.getInstance("AES"));
//			keygen.init(size);
//			return from(null, keygen.generateKey());
//		}
//
//		case EC_P256:
//		case EC_P384:
//		case EC_P521:
//			break;
//		case RSA:
//			break;
//		case RSASSA_PSS:
//			break;
//		default:
//			break;
//		}
//		if (algorithm.startsWith("RS")) {
//			final var rsaKeygen = KeyPairGenerator.getInstance("RSA");
//			rsaKeygen.initialize(1024);
//			keyPair = rsaKeygen.generateKeyPair();
//			type = Type.RSA;
//		} else if (algorithm.startsWith("ES")) {
//			final var ecKeygen = KeyPairGenerator.getInstance("EC");
//			ecKeygen.initialize(new ECGenParameterSpec("secp256r1"));
//			keyPair = ecKeygen.generateKeyPair();
//			type = Type.EC_P256;
//		} else
//			throw new AssertionFailedError();
//	}
//
//	/**
//	 * Creates a JSON Web Key (JWK).
//	 * 
//	 * @param jwk JWK serialized form
//	 * @return {@link WebKey}
//	 */
//	static WebKey readJwk(String jwk) {
//		return Jwk.readJwk(jwk);
//	}
//
//	/**
//	 * Serializes a {@link WebKey} as JWK.
//	 * 
//	 * @param webKey web key
//	 * @return serialized JWK
//	 */
//	static String asJwk(WebKey webKey) {
//		return Jwk.asJwk(webKey);
//	}
//
//	/**
//	 * Serializes {@link WebKey}s as a JWKS.
//	 * 
//	 * @param webKeys web keys
//	 * @return serialized JWKS
//	 */
//	static String asJwks(Iterable<WebKey> webKeys) {
//		return Jwk.asJwks(webKeys);
//	}
//
//	/**
//	 * Gets the key type.
//	 * 
//	 * @return key type
//	 */
//	default Type getType() {
//		final var alg = getAlgorithm();
//		if (alg != null)
//			return alg.type;
//
//		return Type.of(getPublicKey());
//	}
//
//	/**
//	 * Gets the public key use.
//	 * 
//	 * @return public key use.
//	 */
//	default Use getUse() {
//		final var alg = getAlgorithm();
//		if (alg != null)
//			return alg.use;
//
//		return null;
//	}
//
//	/**
//	 * Gets the raw key data for use when {@link Type#RAW}.
//	 * 
//	 * @return {@link SecretKey}; null if {@link #getType()} is not {@link Type#RAW}
//	 */
//	default byte[] getKey() {
//		return null;
//	}
//
//	/**
//	 * Gets the JCE private key implementation.
//	 * 
//	 * @return {@link PrivateKey}; null if not known or {@link #getType()} is
//	 *         {@link Type#RAW}
//	 */
//	default PrivateKey getPrivateKey() {
//		return null;
//	}
//
//	/**
//	 * Gets the JCE public key implementation.
//	 * 
//	 * @return {@link PublicKey}; null if {@link #getType()} is {@link Type#RAW},
//	 *         defaults to the public key of the first {@link #getCertificateChain()
//	 *         chained certificate}.
//	 */
//	default PublicKey getPublicKey() {
//		final var cert = getCertificateChain();
//		if (cert != null && cert.length > 0)
//			return cert[0].getPublicKey();
//		else
//			return null;
//	}
//
//	/**
//	 * Gets the key operations.
//	 * 
//	 * @return ops; <em>should</em> be validated, <em>may</em> be null (default) to
//	 *         expect validation to be skipped
//	 */
//	default Set<Op> getOps() {
//		return null;
//	}
//
//	/**
//	 * Gets the algorithm.
//	 * 
//	 * @return algorithm
//	 */
//	default Algorithm getAlgorithm() {
//		return null;
//	}
//
//	/**
//	 * Gets the Key ID.
//	 * 
//	 * @return JSON kid attribute value
//	 */
//	default String getId() {
//		return "default";
//	}

}
