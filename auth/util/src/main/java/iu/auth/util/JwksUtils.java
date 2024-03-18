package iu.auth.util;

import java.math.BigInteger;
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
import java.util.Base64;

import jakarta.json.JsonObject;

/**
 * Common JWKS processing utilities.
 */
final class JwksUtils {

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
		return new BigInteger(1, Base64.getUrlDecoder().decode(encoded));
	}

	private JwksUtils() {
	}

}
