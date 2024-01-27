package iu.auth.util;

import java.math.BigInteger;
import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import jakarta.json.JsonObject;

/**
 * Performs basic validate checks on a JWT access token
 */
public class AccessTokenVerifier {

	private static final Logger LOG = Logger.getLogger(AccessTokenVerifier.class.getName());

	/**
	 * Gets the {@link ECParameterSpec} for decoding an EC JWK.
	 * 
	 * @param jwk parsed JWK
	 * @return {@link ECParameterSpec}
	 * @throws NoSuchAlgorithmException      If the JWK is invalid
	 * @throws InvalidParameterSpecException If the JWK is invalid
	 */
	static ECParameterSpec getECParameterSpec(JsonObject jwk)
			throws NoSuchAlgorithmException, InvalidParameterSpecException {
		final String ecParam;
		switch (jwk.getString("crv")) {
		case "P-256":
			ecParam = "secp256r1";
			break;
		case "P-384":
			ecParam = "secp384r1";
			break;
		case "P-521":
			ecParam = "secp521r1";
			break;
		default:
			throw new IllegalArgumentException("Unsupported EC curve: " + jwk);
		}

		final var algorithmParamters = AlgorithmParameters.getInstance("EC");
		algorithmParamters.init(new ECGenParameterSpec(ecParam));
		return algorithmParamters.getParameterSpec(ECParameterSpec.class);
	}

	/**
	 * Reads an EC public key from
	 * 
	 * @param jwk parsed JWK
	 * @return {@link ECPublicKey}
	 * @throws InvalidKeySpecException       If the JWK is invalid
	 * @throws NoSuchAlgorithmException      If the JWK is invalid
	 * @throws InvalidParameterSpecException If the JWK is invalid
	 */
	static ECPublicKey toECPublicKey(JsonObject jwk)
			throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidParameterSpecException {
		if (!"EC".equals(jwk.getString("kty")))
			throw new IllegalArgumentException("Not an EC key: " + jwk);
		return (ECPublicKey) KeyFactory.getInstance("EC")
				.generatePublic(new ECPublicKeySpec(
						new ECPoint(decodeKeyComponent(jwk.getString("x")), decodeKeyComponent(jwk.getString("y"))),
						getECParameterSpec(jwk)));
	}

	/**
	 * Reads an RSA public key from
	 * 
	 * @param jwk parsed JWK
	 * @return {@link ECPublicKey}
	 * @throws InvalidKeySpecException  If the JWK is invalid
	 * @throws NoSuchAlgorithmException If the JWK is invalid
	 */
	static RSAPublicKey toRSAPublicKey(JsonObject jwk) throws InvalidKeySpecException, NoSuchAlgorithmException {
		if (!"RSA".equals(jwk.getString("kty")))
			throw new IllegalArgumentException("Not an RSA key: " + jwk);
		return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
				new RSAPublicKeySpec(decodeKeyComponent(jwk.getString("n")), decodeKeyComponent(jwk.getString("e"))));
	}

	private static BigInteger decodeKeyComponent(String encoded) {
		return new BigInteger(Base64.getUrlDecoder().decode(encoded));
	}

	private final URI keysetUri;
	private final String keyId;
	private final String issuer;
	private final String audience;
	private final String algorithm;
	private final Duration refreshInterval;
	private Algorithm jwtAlgorithm;
	private Instant lastUpdate;

	/**
	 * Constructor.
	 * 
	 * @param keysetUri       JWKS URL
	 * @param keyId           JWK ID; <em>must</em> refer to a public signing key
	 *                        included in the key set.
	 * @param issuer          expected issuer
	 * @param audience        expected audience
	 * @param algorithm       algorithm used to sign the JWT
	 * @param refreshInterval max time to reuse a configured {@link Algorithm}
	 *                        before refreshing from the JWKS URL.
	 */
	public AccessTokenVerifier(URI keysetUri, String keyId, String issuer, String audience, String algorithm,
			Duration refreshInterval) {
		this.keysetUri = keysetUri;
		this.keyId = keyId;
		this.issuer = issuer;
		this.audience = audience;
		this.algorithm = algorithm;
		this.refreshInterval = refreshInterval;
	}

	/**
	 * Verifies a JWT access token.
	 * 
	 * <p>
	 * Verifies:
	 * </p>
	 * <ul>
	 * <li>The use of a strong signature algorithm: RSA or ECDSA</li>
	 * <li>The RSA or ECDSA signature is valid</li>
	 * <li>The iss claim matches the configured issuer</li>
	 * <li>The aud claim includes the configured audience</li>
	 * <li>
	 * </ul>
	 * 
	 * @param token JWT access token
	 * @return Parsed JWT, can be used
	 */
	public DecodedJWT verify(String token) {
		final var verifier = JWT.require(getAlgorithm()) //
				.withIssuer(issuer).withAudience(audience) //
				.withClaimPresence("iat") //
				.withClaimPresence("exp") //
				.acceptLeeway(15L).build();

		return verifier.verify(token);
	}

	private JsonObject readJwk() {
		final var jwks = HttpUtils.read(keysetUri).asJsonObject();
		try {
			for (final var key : jwks.getJsonArray("keys")) {
				final var keyAsJsonObject = key.asJsonObject();
				if (keyId.equals(keyAsJsonObject.getString("kid")))
					return keyAsJsonObject;
			}
		} catch (Throwable e) {
			throw new IllegalStateException("Invalid JWKS format: " + jwks, e);
		}

		throw new IllegalStateException("Key " + keyId + " not in JWKS: " + jwks);
	}

	private Algorithm getAlgorithm() {
		final var now = Instant.now();
		if (lastUpdate == null || lastUpdate.isBefore(now.minus(refreshInterval))) {
			JsonObject jwk = null;
			try {
				jwk = readJwk();
				switch (algorithm) {
				case "ES256":
					jwtAlgorithm = Algorithm.ECDSA256(toECPublicKey(jwk));
					break;
				case "ES384":
					jwtAlgorithm = Algorithm.ECDSA384(toECPublicKey(jwk));
					break;
				case "ES512":
					jwtAlgorithm = Algorithm.ECDSA512(toECPublicKey(jwk));
					break;
				case "RS256":
					jwtAlgorithm = Algorithm.RSA256(toRSAPublicKey(jwk), null);
					break;
				case "RS384":
					jwtAlgorithm = Algorithm.RSA384(toRSAPublicKey(jwk), null);
					break;
				case "RS512":
					jwtAlgorithm = Algorithm.RSA512(toRSAPublicKey(jwk), null);
					break;
				default:
					throw new UnsupportedOperationException("Unsupported JWT algorithm " + algorithm);
				}
			} catch (Throwable e) {
				final var message = "JWT Algorithm initialization failure; keysetUri=" + keysetUri + " keyId=" + keyId
						+ " jwk=" + jwk;
				if (jwtAlgorithm == null)
					throw new IllegalStateException(message, e);
				else
					LOG.log(Level.INFO, message, e);
			}
			lastUpdate = Instant.now();
		}
		return jwtAlgorithm;
	}

}
