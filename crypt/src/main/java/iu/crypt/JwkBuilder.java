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
package iu.crypt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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

import javax.crypto.KeyGenerator;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.PemEncoded;
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
public class JwkBuilder extends WebKeyReferenceBuilder<JwkBuilder> implements Builder<JwkBuilder> {
	static {
		IuObject.assertNotOpen(JweBuilder.class);
	}

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
			jwks = IuException.unchecked(
					() -> IuHttp.get(uri, IuHttp.validate(JwkBuilder::readJwks, IuHttp.OK)).toArray(Jwk[]::new));
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
		return parse(IuJson.parse(jwk));
	}

	/**
	 * Serializes {@link WebKey}s as a JSON Web Key Set.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @return serialized JWKS
	 */
	public static String asJwks(Stream<? extends WebKey> webKeys) {
		return writeAsJwks(webKeys).toString();
	}

	/**
	 * Writes {@link WebKey} as a JSON Web Key.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @param out     {@link OutputStream}
	 */
	public static void writeJwks(Stream<? extends WebKey> webKeys, OutputStream out) {
		IuJson.serialize(writeAsJwks(webKeys), out);
	}

	/**
	 * Converts a JSON value to a JSON Web Key (JWK).
	 * 
	 * @param jwk JSON Web Key
	 * @return {@link WebKey}
	 */
	public static Jwk parse(JsonValue jwk) {
		return new JwkBuilder().jwk(jwk).build();
	}

	/**
	 * Converts a JSON Web Key (JWK) to JSON.
	 * 
	 * @param jwk JSON Web Key
	 * @return {@link WebKey}
	 */
	public static JsonObject toJson(Object jwk) {
		final var o = IuJson.object();
		((Jwk) jwk).serializeTo(o);
		return o.build();
	}

	/**
	 * Creates an Elliptic Curve {@link KeyPair} from parsed JWK attributes.
	 * 
	 * @param parsedJwk parsed JWK attributes
	 * @return {@link KeyPair}
	 */
	static KeyPair readEC(JsonObject parsedJwk) {
		return IuException.unchecked(() -> {
			final var kty = IuJson.<String>get(parsedJwk, "kty");
			final Type type;
			if (kty == null)
				type = Algorithm.from(parsedJwk.getString("alg")).type;
			else
				type = Type.from(kty, parsedJwk.getString("crv"));

			final var algorithmParamters = AlgorithmParameters.getInstance("EC");
			algorithmParamters.init(new ECGenParameterSpec(type.ecParam));
			final var spec = algorithmParamters.getParameterSpec(ECParameterSpec.class);

			final var keyFactory = KeyFactory.getInstance("EC");
			final var w = new ECPoint(Objects.requireNonNull(IuJson.get(parsedJwk, "x", UnsignedBigInteger.JSON), "x"),
					Objects.requireNonNull(IuJson.get(parsedJwk, "y", UnsignedBigInteger.JSON), "y"));
			final var pub = keyFactory.generatePublic(new ECPublicKeySpec(w, spec));

			final PrivateKey priv;
			if (parsedJwk.containsKey("d"))
				priv = keyFactory.generatePrivate(new ECPrivateKeySpec(
						Objects.requireNonNull(IuJson.get(parsedJwk, "d", UnsignedBigInteger.JSON), "d"), spec));
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

			final var modulus = Objects.requireNonNull(IuJson.get(parsedJwk, "n", UnsignedBigInteger.JSON), "n");
			final var exponent = Objects.requireNonNull(IuJson.get(parsedJwk, "e", UnsignedBigInteger.JSON), "e");
			final var pub = keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));

			final PrivateKey priv;
			if (parsedJwk.containsKey("d")) {
				final KeySpec keySpec;
				final var privateExponent = Objects.requireNonNull(IuJson.get(parsedJwk, "d", UnsignedBigInteger.JSON),
						"d");
				if (parsedJwk.containsKey("p")) {
					final var primeP = Objects.requireNonNull(IuJson.get(parsedJwk, "p", UnsignedBigInteger.JSON), "p");
					final var primeQ = Objects.requireNonNull(IuJson.get(parsedJwk, "q", UnsignedBigInteger.JSON), "q");
					final var primeExponentP = Objects
							.requireNonNull(IuJson.get(parsedJwk, "dp", UnsignedBigInteger.JSON), "dp");
					final var primeExponentQ = Objects
							.requireNonNull(IuJson.get(parsedJwk, "dq", UnsignedBigInteger.JSON), "dq");
					final var crtCoefficient = Objects
							.requireNonNull(IuJson.get(parsedJwk, "qi", UnsignedBigInteger.JSON), "qi");

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

	private static Stream<Jwk> readJwks(InputStream jwks) {
		return IuJson.parse(jwks).asJsonObject().getJsonArray("keys").stream()
				.map(a -> new JwkBuilder().jwk(a).build());
	}

	private static JsonObject writeAsJwks(Stream<? extends WebKey> webKeys) {
		final var keysBuilder = IuJson.array();
		webKeys.map(key -> (Jwk) key).forEach(key -> {
			final var jwkBuilder = IuJson.object();
			key.serializeTo(jwkBuilder);
			keysBuilder.add(jwkBuilder);
		});
		return IuJson.object().add("keys", keysBuilder).build();
	}

	private Type type;
	private Use use;
	private Set<Op> ops;
	private byte[] key;
	private PrivateKey privateKey;
	private PublicKey publicKey;

	@Override
	public JwkBuilder type(Type type) {
		Objects.requireNonNull(type);

		final var algorithm = algorithm();
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

		final var algorithm = algorithm();
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
		super.algorithm(algorithm);
		type(algorithm.type);

		if (use != null //
				&& !use.equals(algorithm.use))
			throw new IllegalArgumentException("Incorrect use " + use + " for algorithm " + algorithm);

		return this;
	}

	@Override
	public JwkBuilder ops(Op... ops) {
		Objects.requireNonNull(ops);

		final var opSet = Set.of(ops);
		if (this.ops == null)
			this.ops = opSet;
		else if (!opSet.equals(this.ops))
			throw new IllegalStateException("Ops already set to " + this.ops);

		return this;
	}

	@Override
	public JwkBuilder ephemeral() {
		return ephemeral(algorithm());
	}

	@SuppressWarnings("deprecation")
	@Override
	public JwkBuilder ephemeral(Algorithm algorithm) {
		Objects.requireNonNull(algorithm(), "must specify algorithm for ephemeral key");
		type(algorithm.type);

		switch (algorithm) {
		case A128GCMKW:
		case A128KW:
		case A192GCMKW:
		case A192KW:
		case A256GCMKW:
		case A256KW:
		case HS256:
		case HS384:
		case HS512:
		case DIRECT:
			IuException.unchecked(() -> {
				final var keygen = IuException.unchecked(() -> KeyGenerator.getInstance(algorithm.algorithm));
				keygen.init(algorithm.size);
				key(keygen.generateKey().getEncoded());
			});
			break;

		case ECDH_ES:
		case ECDH_ES_A128KW:
		case ECDH_ES_A192KW:
		case ECDH_ES_A256KW:
		case ES256:
		case ES384:
		case ES512:
			IuException.unchecked(() -> {
				final var gen = KeyPairGenerator.getInstance("EC");
				gen.initialize(new ECGenParameterSpec(algorithm.type.ecParam));
				pair(gen.generateKeyPair());
			});
			break;

		case PS256:
		case PS384:
		case PS512:
		case RS256:
		case RS384:
		case RS512:
		case RSA1_5:
		case RSA_OAEP:
		case RSA_OAEP_256:
			IuException.unchecked(() -> {
				final var gen = KeyPairGenerator.getInstance(algorithm.algorithm);
				gen.initialize(algorithm.size);
				pair(gen.generateKeyPair());
			});
			break;

		case PBES2_HS256_A128KW:
		case PBES2_HS384_A192KW:
		case PBES2_HS512_A256KW:
		default:
			throw new UnsupportedOperationException();
		}
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
				publicKey = IuException.unchecked(() -> KeyFactory.getInstance(type.kty)
						.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent())));
			} else if (privateKey instanceof RSAMultiPrimePrivateCrtKeySpec) {
				final var crt = (RSAMultiPrimePrivateCrtKeySpec) privateKey;
				publicKey = IuException.unchecked(() -> KeyFactory.getInstance(type.kty)
						.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent())));
			}

		pub(publicKey);
		acceptPrivate(privateKey);
		return this;
	}

	@Override
	public JwkBuilder cert(PrivateKey privateKey, X509Certificate... chain) {
		cert(chain);
		acceptPrivate(privateKey);
		return this;
	}

	@Override
	public JwkBuilder pem(InputStream pemEncoded) {
		final var pemIterator = PemEncoded.parse(pemEncoded);

		PrivateKey privateKey = null;
		PublicKey publicKey = null;
		final Queue<X509Certificate> certificateChain = new ArrayDeque<>();
		while (pemIterator.hasNext()) {
			final var segment = pemIterator.next();
			switch (segment.getKeyType()) {
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
		return pem(new ByteArrayInputStream(IuText.utf8(pemEncoded)));
	}

	private JwkBuilder jwk(JsonValue json) {
		final var jwk = json.asJsonObject();

		final var kty = IuJson.<String>get(jwk, "kty");
		final var crv = IuJson.<String>get(jwk, "crv");
		if (kty != null)
			type(type = Type.from(kty, crv));

		IuJson.<String>get(jwk, "kid", a -> this.id(a));
		IuJson.get(jwk, "use", Use.JSON, a -> use(a));
		IuJson.get(jwk, "alg", Algorithm.JSON, a -> algorithm(a));
		IuJson.get(jwk, "key_ops", IuJsonAdapter.of(Op[].class, Op.JSON), a -> ops(a));

		final Type type;
		if (algorithm() == null)
			type = Objects.requireNonNull(this.type, "Either kty or alg is required");
		else
			type = algorithm().type;

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
			key(IuJson.get(jwk, "k", UnpaddedBinary.JSON));
			break;
		}

		IuJson.get(jwk, "x5u", IuJsonAdapter.of(URI.class), a -> cert(a));
		IuJson.get(jwk, "x5c", IuJsonAdapter.of(X509Certificate[].class, PemEncoded.CERT_JSON), a -> cert(a));
		IuJson.get(jwk, "x5t", UnpaddedBinary.JSON, a -> x5t(a));
		IuJson.get(jwk, "x5t#S256", UnpaddedBinary.JSON, a -> x5t256(a));

		return this;
	}

	@Override
	public Jwk build() {
		return new Jwk(id(), type, use, key, publicKey, privateKey, ops, algorithm(), certificateUri(),
				certificateChain(), certificateThumbprint(), certificateSha256Thumbprint());
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

	@Override
	protected void acceptCertChain(X509Certificate[] certChain) {
		super.acceptCertChain(certChain);

		final var pub = certChain[0].getPublicKey();
		if (this.publicKey == null)
			pub(pub);
		else if (!pub.equals(this.publicKey))
			throw new IllegalStateException("Public key mismatch");
	}

	@Override
	public JwkBuilder id(String id) {
		super.id(id);
		return this;
	}

	@Override
	public JwkBuilder cert(URI uri) {
		super.cert(uri);
		return this;
	}

	@Override
	public JwkBuilder cert(X509Certificate... chain) {
		super.cert(chain);
		return this;
	}

	@Override
	public JwkBuilder x5t(byte[] certificateThumbprint) {
		super.x5t(certificateThumbprint);
		return this;
	}

	@Override
	public JwkBuilder x5t256(byte[] certificateSha256Thumbprint) {
		super.x5t256(certificateSha256Thumbprint);
		return this;
	}

	@Override
	protected JwkBuilder next() {
		return this;
	}

}
