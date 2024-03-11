package iu.crypt;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
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
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import edu.iu.IuException;
import edu.iu.crypt.WebKey;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * JSON Web Key (JWK) implementation.
 */
public class Jwk extends BaseWebKey {

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
			final var w = new ECPoint(getBigInt(parsedJwk, "x"), getBigInt(parsedJwk, "y"));
			final var pub = keyFactory.generatePublic(new ECPublicKeySpec(w, spec));

			final PrivateKey priv;
			if (parsedJwk.containsKey("d"))
				priv = keyFactory.generatePrivate(new ECPrivateKeySpec(getBigInt(parsedJwk, "d"), spec));
			else
				priv = null;

			return new KeyPair(pub, priv);
		});
	}

	/**
	 * Adds Elliptic Curve key pair attributes to an in-progress JWK builder.
	 * 
	 * @param jwkBuilder JWK builder
	 * @param type       key type
	 * @param pub        public key
	 * @param priv       private key
	 */
	static void writeEC(JsonObjectBuilder jwkBuilder, Type type, ECPublicKey pub, ECPrivateKey priv) {
		jwkBuilder.add("crv", type.crv);

		final var w = pub.getW();
		setBigInt(jwkBuilder, "x", w.getAffineX());
		setBigInt(jwkBuilder, "y", w.getAffineY());

		if (priv != null)
			setBigInt(jwkBuilder, "d", priv.getS());
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

			final var modulus = getBigInt(parsedJwk, "n");
			final var exponent = getBigInt(parsedJwk, "e");
			final var pub = keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));

			final PrivateKey priv;
			if (parsedJwk.containsKey("d")) {
				final KeySpec keySpec;
				final var privateExponent = getBigInt(parsedJwk, "d");
				if (parsedJwk.containsKey("p")) {
					final var primeP = getBigInt(parsedJwk, "p");
					final var primeQ = getBigInt(parsedJwk, "q");
					final var primeExponentP = getBigInt(parsedJwk, "dp");
					final var primeExponentQ = getBigInt(parsedJwk, "dq");
					final var crtCoefficient = getBigInt(parsedJwk, "qi");

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

	/**
	 * Adds RSA key pair attributes to an in-progress JWK builder.
	 * 
	 * @param jwkBuilder JWK builder
	 * @param pub        public key
	 * @param priv       private key
	 */
	static void writeRSA(JsonObjectBuilder jwkBuilder, RSAPublicKey pub, RSAPrivateKey priv) {
		jwkBuilder.add("n", EncodingUtils.base64Url(pub.getModulus().toByteArray()));
		jwkBuilder.add("e", EncodingUtils.base64Url(pub.getPublicExponent().toByteArray()));
		if (priv == null)
			return;

		jwkBuilder.add("d", EncodingUtils.base64Url(priv.getPrivateExponent().toByteArray()));
		if (priv instanceof RSAPrivateCrtKey) {
			final var crt = (RSAPrivateCrtKey) priv;
			setBigInt(jwkBuilder, "p", crt.getPrimeP());
			setBigInt(jwkBuilder, "q", crt.getPrimeQ());
			setBigInt(jwkBuilder, "dp", crt.getPrimeExponentP());
			setBigInt(jwkBuilder, "dq", crt.getPrimeExponentQ());
			setBigInt(jwkBuilder, "qi", crt.getCrtCoefficient());
			return;
		}
	}

	/**
	 * Writes JWK attributes to a {@link JsonObjectBuilder}.
	 * 
	 * @param jwkBuilder {@link JsonObjectBuilder}
	 * @param webKey     {@link WebKey}
	 */
	static void writeJwk(JsonObjectBuilder jwkBuilder, WebKey webKey) {
		final var id = webKey.getId();
		if (id != null)
			jwkBuilder.add("kid", webKey.getId());

		final var use = webKey.getUse();
		if (use != null)
			jwkBuilder.add("use", webKey.getUse().use);

		final var type = webKey.getType();
		if (type != null)
			jwkBuilder.add("kty", webKey.getType().kty);

		final var ops = webKey.getOps();
		if (ops != null) {
			final var keyOps = JsonP.PROVIDER.createArrayBuilder();
			ops.forEach(keyOp -> keyOps.add(keyOp.keyOp));
			jwkBuilder.add("key_ops", keyOps);
		}

		final var algorithm = webKey.getAlgorithm();
		if (algorithm != null)
			jwkBuilder.add("alg", algorithm.alg);

		final var certificateUri = webKey.getCertificateUri();
		if (certificateUri != null)
			jwkBuilder.add("x5u", certificateUri.toString());

		final var certificateChain = webKey.getCertificateChain();
		if (certificateChain != null) {
			final var x5c = JsonP.PROVIDER.createArrayBuilder();
			for (final var cert : certificateChain)
				// RFC-7517 JWK 4.7: Base64 _not_ URL encoder, with padding
				x5c.add(Base64.getEncoder().encodeToString(IuException.unchecked(cert::getEncoded)));
			jwkBuilder.add("x5c", x5c);
		}

		final var certificateThumbprint = webKey.getCertificateThumbprint();
		if (certificateThumbprint != null)
			setBytes(jwkBuilder, "x5t", certificateThumbprint);

		final var certificateSha256Thumbprint = webKey.getCertificateSha256Thumbprint();
		if (certificateSha256Thumbprint != null)
			setBytes(jwkBuilder, "x5t#S256", certificateSha256Thumbprint);

		final var publicKey = webKey.getPublicKey();
		if (publicKey instanceof ECPublicKey)
			writeEC(jwkBuilder, type, (ECPublicKey) webKey.getPublicKey(), (ECPrivateKey) webKey.getPrivateKey());
		else if (publicKey instanceof RSAPublicKey)
			writeRSA(jwkBuilder, (RSAPublicKey) webKey.getPublicKey(), (RSAPrivateKey) webKey.getPrivateKey());
		else {
			final var key = Objects.requireNonNull(webKey.getKey());
			jwkBuilder.add("k", EncodingUtils.base64Url(key));
		}
	}

	/**
	 * Reads a JSON Web Key Set.
	 * 
	 * @param jwks serialized JWKS
	 * @return {@link Map} of {@link WebKey} by Key ID (kid)
	 */
	public static Iterable<WebKey> readJwks(Reader jwks) {
		final var parsed = JsonP.PROVIDER.createReader(jwks).readObject();
		final Queue<WebKey> keys = new ArrayDeque<>();
		for (final var parsedKey : parsed.getJsonArray("keys"))
			keys.offer(new Jwk(parsedKey.asJsonObject()));
		return keys;
	}

	/**
	 * Serializes {@link WebKey} as a JSON Web Key.
	 * 
	 * @param webKey {@link WebKey}
	 * @param writer JWK serialization target writer
	 */
	public static void writeJwks(Iterable<WebKey> webKey, Writer writer) {
		final var keysBuilder = JsonP.PROVIDER.createArrayBuilder();
		for (final var key : webKey) {
			final var jwkBuilder = JsonP.PROVIDER.createObjectBuilder();
			writeJwk(jwkBuilder, key);
			keysBuilder.add(jwkBuilder);
		}
		JsonP.PROVIDER.createWriter(writer)
				.write(JsonP.PROVIDER.createObjectBuilder().add("keys", keysBuilder).build());
	}

	/**
	 * Deserializes a JSON Web Key.
	 * 
	 * @param jwk serialized JWK
	 * @return {@link WebKey}
	 */
	public static WebKey readJwk(Reader jwk) {
		return new Jwk(JsonP.PROVIDER.createReader(jwk).readObject());
	}

	/**
	 * Serializes {@link WebKey} as a JSON Web Key.
	 * 
	 * @param webKey {@link WebKey}
	 * @return serialized JWK
	 */
	public static String asJwk(WebKey webKey) {
		final var b = JsonP.PROVIDER.createObjectBuilder();
		writeJwk(b, webKey);
		return b.build().toString();
	}

	private static byte[] getBytes(JsonObject o, String name) {
		return EncodingUtils.base64Url(o.getString(name));
	}

	private static void setBytes(JsonObjectBuilder o, String name, byte[] b) {
		o.add(name, EncodingUtils.base64Url(b));
	}

	private static BigInteger getBigInt(JsonObject o, String name) {
		return new BigInteger(1, getBytes(o, name));
	}

	private static void setBigInt(JsonObjectBuilder o, String name, BigInteger bi) {
		setBytes(o, name, bi.toByteArray());
	}

	private final String id;
	private final Type type;
	private final Use use;
	private final byte[] key;
	private final PublicKey publicKey;
	private final PrivateKey privateKey;
	private final Set<Op> ops;
	private final Algorithm algorithm;
	private final URI certificateUri;
	private final X509Certificate[] certificateChain;
	private final byte[] certificateThumbprint;
	private final byte[] certificateSha256Thumbprint;

	/**
	 * Creates a JWK from serialized form.
	 * 
	 * @param parsed {@link JsonObject} with JWK attributes
	 */
	private Jwk(JsonObject parsed) {
		id = parsed.getString("kid");
		use = Use.from(parsed.getString("use"));
		type = Type.from(parsed.getString("kty"), parsed.containsKey("crv") ? parsed.getString("crv") : null);

		if (parsed.containsKey("key_ops")) {
			final var keyOps = parsed.getJsonArray("key_ops");
			final Set<Op> ops = new LinkedHashSet<>();
			for (var i = 0; i < keyOps.size(); i++)
				ops.add(Op.from(keyOps.getString(i)));
			this.ops = ops;
		} else
			ops = null;

		if (parsed.containsKey("alg"))
			algorithm = Algorithm.from(parsed.getString("alg"));
		else
			algorithm = null;

		if (parsed.containsKey("x5u"))
			certificateUri = URI.create(parsed.getString("x5u"));
		else
			certificateUri = null;

		if (parsed.containsKey("x5c"))
			certificateChain = IuException.unchecked(() -> {
				final var x5c = parsed.getJsonArray("x5c");
				final var certFactory = CertificateFactory.getInstance("X.509");
				final Queue<X509Certificate> certs = new ArrayDeque<>(x5c.size());
				for (var i = 0; i < x5c.size(); i++)
					certs.offer((X509Certificate) certFactory.generateCertificate(
							new ByteArrayInputStream(Base64.getDecoder().decode(x5c.getString(i)))));
				return certs.toArray(new X509Certificate[certs.size()]);
			});
		else
			certificateChain = null;

		if (parsed.containsKey("x5t"))
			certificateThumbprint = EncodingUtils.base64Url(parsed.getString("x5t"));
		else
			certificateThumbprint = null;

		if (parsed.containsKey("x5t#S256"))
			certificateSha256Thumbprint = EncodingUtils.base64Url(parsed.getString("x5t#S256"));
		else
			certificateSha256Thumbprint = null;

		switch (type) {
		case EC_P256:
		case EC_P384:
		case EC_P521: {
			final var ec = readEC(parsed);
			publicKey = ec.getPublic();
			privateKey = ec.getPrivate();
			key = null;
			break;
		}

		case RSASSA_PSS:
		case RSA: {
			final var rsa = readRSA(parsed);
			publicKey = rsa.getPublic();
			privateKey = rsa.getPrivate();
			key = null;
			break;
		}
		default:
			key = getBytes(parsed, "k");
			publicKey = null;
			privateKey = null;
			break;
		}
	}

	@Override
	public String getId() {
		return id;
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
	public Set<Op> getOps() {
		return ops;
	}

	@Override
	public Algorithm getAlgorithm() {
		return algorithm;
	}

	@Override
	public URI getCertificateUri() {
		return certificateUri;
	}

	@Override
	public X509Certificate[] getCertificateChain() {
		return certificateChain;
	}

	@Override
	public byte[] getCertificateThumbprint() {
		return certificateThumbprint;
	}

	@Override
	public byte[] getCertificateSha256Thumbprint() {
		return certificateSha256Thumbprint;
	}

}
