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

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebKey;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * JSON Web Key (JWK) implementation.
 */
public class Jwk extends JsonKeyReference<Jwk> implements WebKey {
	static {
		IuObject.assertNotOpen(JweBuilder.class);
	}

	/**
	 * JSON type adapter.
	 */
	public static final IuJsonAdapter<WebKey> JSON = IuJsonAdapter.from(v -> {
		return new Jwk(v.asJsonObject());
	}, v -> {
		final var o = IuJson.object();
		((Jwk) v).serializeTo(o);
		return o.build();
	});

	private static Map<URI, Jwk[]> JWKS_CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	private static JsonObject writeAsJwks(Iterable<? extends WebKey> webKeys) {
		return IuJson.object().add("keys", IuJsonAdapter.of(Iterable.class, JSON).toJson(webKeys)).build();
	}

	/**
	 * Gets key set by URI.
	 * 
	 * @param uri Key set URI
	 * @return key set
	 */
	public static Iterable<Jwk> readJwks(URI uri) {
		var jwks = JWKS_CACHE.get(uri);
		if (jwks == null)
			JWKS_CACHE.put(uri, jwks = IuException.unchecked(() -> IuJsonAdapter.<Stream<Jwk>>of(Stream.class, JSON)
					.fromJson(IuHttp.get(uri, IuHttp.READ_JSON_OBJECT).getJsonArray("keys")).toArray(Jwk[]::new)));
		return IuIterable.iter(jwks);
	}

	/**
	 * Reads a key set from an input stream.
	 * 
	 * @param in input stream
	 * @return {@link WebKey}
	 */
	public static Iterable<Jwk> readJwks(InputStream in) {
		return IuException.unchecked(() -> {
			return IuJsonAdapter.<Iterable<Jwk>>of(Iterable.class, JSON)
					.fromJson(IuJson.parse(in).asJsonObject().getJsonArray("keys"));
		});
	}

	/**
	 * Parses a JSON Web Key Set (JWKS).
	 * 
	 * @param jwks serialized JWKS
	 * @return parsed key set
	 */
	public static Iterable<Jwk> parseJwks(JsonObject jwks) {
		return IuJsonAdapter.<Iterable<Jwk>>of(Iterable.class, JSON).fromJson(jwks.getJsonArray("keys"));
	}

	/**
	 * Serializes {@link WebKey}s as a JSON Web Key Set.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @return serialized JWKS
	 */
	public static JsonObject asJwks(Iterable<? extends WebKey> webKeys) {
		return writeAsJwks(webKeys);
	}

	/**
	 * Writes {@link WebKey} as a JSON Web Key.
	 * 
	 * @param webKeys {@link WebKey}s
	 * @param out     {@link OutputStream}
	 */
	public static void writeJwks(Iterable<? extends WebKey> webKeys, OutputStream out) {
		IuJson.serialize(writeAsJwks(webKeys), out);
	}

	private static KeyPair readRSA(Type type, JsonObject parsedJwk) {
		return IuException.unchecked(() -> {
			final var keyFactory = KeyFactory.getInstance(type.kty);

			final var modulus = IuJson.get(parsedJwk, "n", UnsignedBigInteger.JSON);
			final var exponent = IuJson.get(parsedJwk, "e", UnsignedBigInteger.JSON);

			final PublicKey pub;
			if (exponent != null)
				pub = keyFactory.generatePublic(new RSAPublicKeySpec(Objects.requireNonNull(modulus, "n"), exponent));
			else
				pub = null;

			final PrivateKey priv;
			if (parsedJwk.containsKey("d")) {
				Objects.requireNonNull(modulus, "n");

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

	private static KeyPair readXEC(byte[] x, byte[] d, NamedParameterSpec namedSpec) {
		final var keyFactory = IuException.unchecked(() -> KeyFactory.getInstance(namedSpec.getName()));
		return new KeyPair(IuObject.convert(x, u -> IuException.unchecked(() -> {
			final var a = EncodingUtils.reverse(u);
			if (namedSpec.getName().equals(Type.X25519.algorithmParams))
				a[0] &= 0x7f;
			return keyFactory.generatePublic(new XECPublicKeySpec(namedSpec, UnsignedBigInteger.bigInt(a)));
		})), IuObject.convert(d,
				s -> IuException.unchecked(() -> keyFactory.generatePrivate(new XECPrivateKeySpec(namedSpec, s)))));
	}

	private static KeyPair readEdEC(byte[] x, byte[] d, NamedParameterSpec namedSpec) {
		final var keyFactory = IuException.unchecked(() -> KeyFactory.getInstance(namedSpec.getName()));
		return new KeyPair(IuObject.convert(x, a -> IuException.unchecked(() -> {
			// Convert from RFC-8032 encoded format to JCE EdECPublicKeySpec
			// https://datatracker.ietf.org/doc/html/rfc8032#section-5.1.2
			// https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/EdECPoint.html
			final var y = EncodingUtils.reverse(a); // convert from little- to big-endian
			final var xodd = (y[0] & 0x80) != 0; // checks least-significant (odd) x bit
			y[0] &= 0x7f; // clear x-odd from unsigned big-endian integer encoding
			final var yint = UnsignedBigInteger.bigInt(y); // decode y-axis value

			// EdDSA support was introduced in JDK 15, not supported by JDK 11
			// TODO: convert to compiled code for source level 17+
			final var pointClass = ClassLoader.getPlatformClassLoader().loadClass("java.security.spec.EdECPoint");
			return keyFactory.generatePublic(
					(KeySpec) ClassLoader.getPlatformClassLoader().loadClass("java.security.spec.EdECPublicKeySpec")
							.getConstructor(NamedParameterSpec.class, pointClass).newInstance(namedSpec,
									pointClass.getConstructor(Boolean.TYPE, BigInteger.class).newInstance(xodd, yint)));
		})), IuObject.convert(d,
				s -> IuException.unchecked(() -> keyFactory.generatePrivate((KeySpec) ClassLoader
						.getPlatformClassLoader().loadClass("java.security.spec.EdECPrivateKeySpec")
						.getConstructor(NamedParameterSpec.class, byte[].class).newInstance(namedSpec, s)))));
	}

	private static KeyPair readEC(Type type, JsonObject parsedJwk) {
		return IuException.unchecked(() -> {
			final var x = IuJson.get(parsedJwk, "x", UnpaddedBinary.JSON);
			final var d = IuJson.get(parsedJwk, "d", UnpaddedBinary.JSON);

			final var spec = WebKey.algorithmParams(type.algorithmParams);
			if (spec instanceof NamedParameterSpec)
				if (type.algorithmParams.startsWith("X"))
					return readXEC(x, d, (NamedParameterSpec) spec);
				else
					return readEdEC(x, d, (NamedParameterSpec) spec);
			else {
				final var keyFactory = IuException.unchecked(() -> KeyFactory.getInstance("EC"));
				return new KeyPair(
						IuObject.convert(x,
								a -> IuException
										.unchecked(
												() -> keyFactory.generatePublic(new ECPublicKeySpec(
														new ECPoint(UnsignedBigInteger.bigInt(a),
																Objects.requireNonNull(IuJson.get(parsedJwk, "y",
																		UnsignedBigInteger.JSON), "y")),
														(ECParameterSpec) spec)))),
						IuObject.convert(d, a -> IuException.unchecked(() -> keyFactory.generatePrivate(
								new ECPrivateKeySpec(UnsignedBigInteger.bigInt(a), (ECParameterSpec) spec)))));
			}
		});
	}

	private final Type type;
	private final Use use;
	private final Set<Operation> ops;
	private final byte[] key;
	private final PublicKey publicKey;
	private final PrivateKey privateKey;
	private final PublicKey verifiedPublicKey;

	/**
	 * Constructor.
	 * 
	 * @param jwk parsed JWK parameters
	 */
	public Jwk(JsonObject jwk) {
		super(jwk);
		this.type = Objects.requireNonNull(Type.from(IuJson.get(jwk, "kty"), IuJson.get(jwk, "crv")),
				"Key type is required");

		this.use = IuJson.get(jwk, "use", Use.JSON);
		this.ops = IuJson.get(jwk, "key_ops", IuJsonAdapter.<Set<Operation>>of(Set.class, Operation.JSON));
		this.key = IuJson.get(jwk, "k", UnpaddedBinary.JSON);

		switch (type) {
		case EC_P256:
		case EC_P384:
		case EC_P521:
		case ED25519:
		case ED448:
		case X25519:
		case X448: {
			final var keyPair = readEC(type, jwk);
			this.publicKey = keyPair.getPublic();
			this.privateKey = keyPair.getPrivate();
			break;
		}

		case RSA:
		case RSASSA_PSS: {
			final var keyPair = readRSA(type, jwk);
			this.publicKey = keyPair.getPublic();
			this.privateKey = keyPair.getPrivate();
			break;
		}

		default:
			this.publicKey = null;
			this.privateKey = null;
			break;
		}

		verifiedPublicKey = WebKey.verify(this);
	}

	/**
	 * Well-known key constructor, strips private/secret key data and copies
	 * verified public key data.
	 * 
	 * @param certParams  contains certificate parameters and id+algorithm
	 * @param internalKey internal representation of the key, including
	 *                    private/secret key data
	 */
	private Jwk(JsonObject certParams, Jwk internalKey) {
		super(certParams);
		this.type = internalKey.type;
		this.use = internalKey.use;
		this.ops = internalKey.ops;
		this.key = null;
		this.privateKey = null;
		this.publicKey = internalKey.verifiedPublicKey;
		this.verifiedPublicKey = internalKey.verifiedPublicKey;
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public Use getUse() {
		return use;
	}

	@Override
	public byte[] getKey() {
		return key;
	}

	@Override
	public PublicKey getPublicKey() {
		return publicKey;
	}

	@Override
	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	@Override
	public Set<Operation> getOps() {
		return ops;
	}

	@Override
	public Jwk wellKnown() {
		if (privateKey == null && key == null && publicKey != null)
			return this;

		final var jwkBuilder = (JwkBuilder) WebKey.builder(type);
		IuObject.convert(getCertificateUri(), jwkBuilder::cert);
		IuObject.convert(verifiedCertificateChain(), jwkBuilder::cert);
		IuObject.convert(getCertificateThumbprint(), jwkBuilder::x5t);
		IuObject.convert(getCertificateSha256Thumbprint(), jwkBuilder::x5t256);
		IuObject.convert(getAlgorithm(), jwkBuilder::algorithm);
		IuObject.convert(getKeyId(), jwkBuilder::keyId);
		final var initBuilder = IuJson.object();
		jwkBuilder.build(initBuilder);
		return new Jwk(initBuilder.build(), this);
	}

	@Override
	public int hashCode() {
		return IuObject.hashCodeSuper(super.hashCode(), type, use, key, publicKey, privateKey, ops);
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj))
			return false;
		Jwk other = (Jwk) obj;
		return IuObject.equals(type, other.type) //
				&& IuObject.equals(use, other.use) //
				&& IuObject.equals(ops, other.ops) //
				&& IuObject.equals(key, other.key) //
				&& IuObject.equals(privateKey, other.privateKey) //
				&& IuObject.equals(publicKey, other.publicKey);
	}

	@Override
	public String toString() {
		final var jwkBuilder = IuJson.object();
		serializeTo(jwkBuilder);
		return jwkBuilder.build().toString();
	}

	/**
	 * Adds serialized JWK attributes to a JSON object builder.
	 * 
	 * @param jwkBuilder {@link JsonObjectBuilder}
	 * @return jwkBuilder
	 */
	JsonObjectBuilder serializeTo(JsonObjectBuilder jwkBuilder) {
		super.serializeTo(jwkBuilder);
		IuJson.add(jwkBuilder, "use", () -> use, Use.JSON);
		IuJson.add(jwkBuilder, "key_ops", () -> ops, IuJsonAdapter.of(Set.class, Operation.JSON));

		final var builder = (JwkBuilder) WebKey.builder(type);
		IuObject.convert(key, builder::key);
		IuObject.convert(publicKey, builder::key);
		IuObject.convert(privateKey, builder::key);
		builder.build(jwkBuilder);

		return jwkBuilder;
	}

	/**
	 * Determines whether or not the known components of this key match the known
	 * components of another key.
	 * 
	 * @param key {@link WebKey}
	 * @return true if all non-null components of both keys match
	 */
	boolean represents(Jwk key) {
		return super.represents(key) //
				&& IuObject.represents(type, key.type) //
				&& IuObject.represents(use, key.use) //
				&& IuObject.represents(ops, key.ops) //
				&& IuObject.represents(this.key, key.key) //
				&& IuObject.represents(publicKey, key.publicKey) //
				&& IuObject.represents(privateKey, key.privateKey);
	}

}
