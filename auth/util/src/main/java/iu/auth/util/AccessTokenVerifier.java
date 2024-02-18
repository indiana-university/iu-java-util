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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import edu.iu.IuObject;
import jakarta.json.JsonObject;

/**
 * Verifies JWT access tokens as signed using an RSA or ECDSA public key from a
 * well-known JWKS key set.
 */
public class AccessTokenVerifier {

	private static final Logger LOG = Logger.getLogger(AccessTokenVerifier.class.getName());

	/**
	 * Cache key binding for binding initialized JWT verification resources by kid
	 * and alg claim values.
	 */
	static class AlgorithmKey {
		private final String kid;
		private final String alg;

		/**
		 * Constructor.
		 * 
		 * @param kid kid claim value
		 * @param alg alg claim value
		 */
		AlgorithmKey(String kid, String alg) {
			this.kid = kid;
			this.alg = alg;
		}

		@Override
		public int hashCode() {
			return IuObject.hashCode(alg, kid);
		}

		@Override
		public boolean equals(Object obj) {
			if (!IuObject.typeCheck(this, obj))
				return false;
			AlgorithmKey other = (AlgorithmKey) obj;
			return IuObject.equals(alg, other.alg) //
					&& IuObject.equals(kid, other.kid);
		}
	}

	private static class CachedAlgorithm {
		private final Algorithm algorithm;
		private final Instant lastUpdated;

		private CachedAlgorithm(Algorithm algorithm, Instant lastUpdated) {
			this.algorithm = algorithm;
			this.lastUpdated = lastUpdated;
		}
	}

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

	private final URI keysetUri;
	private final String issuer;
	private final Supplier<Duration> refreshInterval;
	private final Map<AlgorithmKey, CachedAlgorithm> algorithmCache = new HashMap<>();

	/**
	 * Constructor.
	 * 
	 * @param keysetUri       JWKS URL
	 * @param issuer          expected issuer
	 * @param refreshInterval max time to reuse a configured {@link Algorithm}
	 *                        before refreshing from the JWKS URL.
	 */
	public AccessTokenVerifier(URI keysetUri, String issuer, Supplier<Duration> refreshInterval) {
		this.keysetUri = keysetUri;
		this.issuer = issuer;
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
	 * <li>The aud claim includes the audience</li>
	 * </ul>
	 * 
	 * @param audience expected audience claim
	 * @param token    JWT access token
	 * @return Parsed JWT, can be used to perform additional verification
	 */
	public DecodedJWT verify(String audience, String token) {
		final var decoded = JWT.decode(token);
		final var kid = decoded.getKeyId();
		final var alg = decoded.getAlgorithm();
		final var verifier = JWT //
				.require(getAlgorithm(kid, alg)) //
				.withIssuer(issuer) //
				.withAudience(audience) //
				.withClaimPresence("iat") //
				.withClaimPresence("exp") //
				.acceptLeeway(15L) //
				.build();

		return verifier.verify(token);
	}

	private JsonObject readJwk(String keyId) {
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

	private Algorithm getAlgorithm(String kid, String alg) {
		final var now = Instant.now();
		final var cacheKey = new AlgorithmKey(kid, alg);

		var cachedAlgorithm = algorithmCache.get(cacheKey);
		if (cachedAlgorithm == null || cachedAlgorithm.lastUpdated.isBefore(now.minus(refreshInterval.get()))) {
			JsonObject jwk = null;
			Algorithm jwtAlgorithm;
			try {
				jwk = readJwk(kid);
				switch (alg) {
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
					throw new UnsupportedOperationException("Unsupported JWT algorithm " + alg);
				}

				cachedAlgorithm = new CachedAlgorithm(jwtAlgorithm, now);
				synchronized (algorithmCache) {
					algorithmCache.put(cacheKey, cachedAlgorithm);
				}

			} catch (Throwable e) {
				final var message = "JWT Algorithm initialization failure; keysetUri=" + keysetUri + " " + cacheKey
						+ " jwk=" + jwk;
				if (cachedAlgorithm == null)
					throw new IllegalStateException(message, e);
				else
					LOG.log(Level.INFO, message, e);
			}
		}
		return cachedAlgorithm.algorithm;
	}

}
