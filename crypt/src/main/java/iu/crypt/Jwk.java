package iu.crypt;

import java.io.OutputStream;
import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * JSON Web Key (JWK) implementation.
 */
class Jwk implements WebKey {

	/**
	 * Adds RSA key pair attributes to an in-progress JWK builder.
	 * 
	 * @param jwkBuilder JWK builder
	 * @param pub        public key
	 * @param priv       private key
	 */
	static void writeRSA(JsonObjectBuilder jwkBuilder, RSAPublicKey pub, RSAPrivateKey priv) {
		EncodingUtils.setBigInt(jwkBuilder, "n", pub.getModulus());
		EncodingUtils.setBigInt(jwkBuilder, "e", pub.getPublicExponent());
		if (priv == null)
			return;

		jwkBuilder.add("d", EncodingUtils.base64Url(priv.getPrivateExponent().toByteArray()));
		if (priv instanceof RSAPrivateCrtKey) {
			final var crt = (RSAPrivateCrtKey) priv;
			EncodingUtils.setBigInt(jwkBuilder, "p", crt.getPrimeP());
			EncodingUtils.setBigInt(jwkBuilder, "q", crt.getPrimeQ());
			EncodingUtils.setBigInt(jwkBuilder, "dp", crt.getPrimeExponentP());
			EncodingUtils.setBigInt(jwkBuilder, "dq", crt.getPrimeExponentQ());
			EncodingUtils.setBigInt(jwkBuilder, "qi", crt.getCrtCoefficient());
			return;
		}
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
		EncodingUtils.setBigInt(jwkBuilder, "x", w.getAffineX());
		EncodingUtils.setBigInt(jwkBuilder, "y", w.getAffineY());

		if (priv != null)
			EncodingUtils.setBigInt(jwkBuilder, "d", priv.getS());
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

	private static JsonObject writeAsJwks(Stream<? extends WebKey> webKeys) {
		final var keysBuilder = IuJson.array();
		webKeys.map(key -> (Jwk) key).forEach(key -> {
			final var jwkBuilder = IuJson.object();
			key.serializeTo(jwkBuilder);
			keysBuilder.add(jwkBuilder);
		});
		return IuJson.object().add("keys", keysBuilder).build();
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
	 * Constructor.
	 * 
	 * <p>
	 * Only for use by {@link JwkBuilder}.
	 * </p>
	 * 
	 * @param id                          key ID
	 * @param type                        key type
	 * @param use                         public key use
	 * @param key                         raw encoded key
	 * @param publicKey                   public key
	 * @param privateKey                  private key
	 * @param ops                         key operations
	 * @param algorithm                   algorithm
	 * @param certificateUri              certificate URI
	 * @param certificateChain            certificate chain
	 * @param certificateThumbprint       certificate thumbprint
	 * @param certificateSha256Thumbprint certificate SHA-256 thumbprint
	 */
	Jwk(String id, Type type, Use use, byte[] key, PublicKey publicKey, PrivateKey privateKey, Set<Op> ops,
			Algorithm algorithm, URI certificateUri, X509Certificate[] certificateChain, byte[] certificateThumbprint,
			byte[] certificateSha256Thumbprint) {
		this.id = id;
		this.type = type;
		this.use = use;
		this.key = key;
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.ops = ops;
		this.algorithm = algorithm;
		this.certificateUri = certificateUri;
		this.certificateChain = certificateChain;
		this.certificateThumbprint = certificateThumbprint;
		this.certificateSha256Thumbprint = certificateSha256Thumbprint;
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

	@Override
	public String toString() {
		final var jwkBuilder = IuJson.object();
		serializeTo(jwkBuilder);
		return jwkBuilder.build().toString();
	}

	private void serializeTo(JsonObjectBuilder jwkBuilder) {
		IuJson.add(jwkBuilder, "kid", id);
		IuJson.add(jwkBuilder, "use", () -> use, a -> IuJson.toJson(a.use));
		IuJson.add(jwkBuilder, "kty", () -> type, a -> IuJson.toJson(a.kty));
		IuJson.add(jwkBuilder, "alg", () -> algorithm, a -> IuJson.toJson(a.alg));
		IuJson.add(jwkBuilder, "key_ops", () -> ops, a -> IuJson.toJson(a.stream().map(op -> op.keyOp)));
		IuJson.add(jwkBuilder, "x5u", () -> certificateUri, a -> IuJson.toJson(a.toString()));
		IuJson.add(jwkBuilder, "x5c", () -> certificateChain, a -> IuJson.toJson(
				Stream.of(a).map(cert -> Base64.getEncoder().encodeToString(IuException.unchecked(cert::getEncoded)))));
		EncodingUtils.setBytes(jwkBuilder, "x5t", certificateThumbprint);
		EncodingUtils.setBytes(jwkBuilder, "x5t#S256", certificateSha256Thumbprint);

		if (publicKey instanceof ECPublicKey)
			writeEC(jwkBuilder, type, (ECPublicKey) publicKey, (ECPrivateKey) privateKey);
		else if (publicKey instanceof RSAPublicKey)
			writeRSA(jwkBuilder, (RSAPublicKey) publicKey, (RSAPrivateKey) privateKey);
		else
			EncodingUtils.setBytes(jwkBuilder, "k", key);
	}

}
