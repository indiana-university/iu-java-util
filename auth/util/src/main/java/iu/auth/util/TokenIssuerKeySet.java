package iu.auth.util;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.auth0.jwt.algorithms.Algorithm;

import edu.iu.auth.session.IuSessionProviderKey;
import jakarta.json.Json;

/**
 * Encapsulates a token issuer's key set.
 */
public class TokenIssuerKeySet implements AlgorithmFactory {

	private final Iterable<IuSessionProviderKey> providerKeys;
	private final Map<AlgorithmKey, Algorithm> algorithms = new HashMap<>();

	/**
	 * Constructor.
	 * 
	 * @param providerKeys provider key set
	 */
	public TokenIssuerKeySet(Iterable<IuSessionProviderKey> providerKeys) {
		this.providerKeys = providerKeys;
		for (final var providerKey : providerKeys) {
			Objects.requireNonNull(providerKey.getId(), "id");
			Objects.requireNonNull(providerKey.getType(), "type");
			Objects.requireNonNull(providerKey.getUsage(), "usage");
			Objects.requireNonNull(providerKey.getPublic(), "public");
			Objects.requireNonNull(providerKey.getPrivate(), "private");
		}
	}

	@Override
	@SuppressWarnings("exports")
	public Algorithm getAlgorithm(String kid, String alg) {
		final var cacheKey = new AlgorithmKey(kid, alg);

		var cachedAlgorithm = algorithms.get(cacheKey);
		if (cachedAlgorithm == null) {
			IuSessionProviderKey providerKey = null;
			for (final var k : providerKeys)
				if (kid.equals(k.getId()))
					providerKey = k;

			Objects.requireNonNull(providerKey, "Invalid key id");
			final var pub = Objects.requireNonNull(providerKey.getPublic(), "public");
			final var priv = Objects.requireNonNull(providerKey.getPrivate(), "private");

			switch (alg) {
			case "ES256":
				cachedAlgorithm = Algorithm.ECDSA256((ECPublicKey) pub, (ECPrivateKey) priv);
				break;
			case "ES384":
				cachedAlgorithm = Algorithm.ECDSA384((ECPublicKey) pub, (ECPrivateKey) priv);
				break;
			case "ES512":
				cachedAlgorithm = Algorithm.ECDSA512((ECPublicKey) pub, (ECPrivateKey) priv);
				break;
			case "RS256":
				cachedAlgorithm = Algorithm.RSA256((RSAPublicKey) pub, (RSAPrivateKey) priv);
				break;
			case "RS384":
				cachedAlgorithm = Algorithm.RSA384((RSAPublicKey) pub, (RSAPrivateKey) priv);
				break;
			case "RS512":
				cachedAlgorithm = Algorithm.RSA512((RSAPublicKey) pub, (RSAPrivateKey) priv);
				break;
			default:
				throw new UnsupportedOperationException("Unsupported JWT algorithm " + alg);
			}

			synchronized (algorithms) {
				algorithms.put(cacheKey, cachedAlgorithm);
			}

		}
		return cachedAlgorithm;
	}

	/**
	 * Gets this issuer's public keys as an well-known key set for publishing
	 * externally.
	 * 
	 * @return JWKS well-known key set
	 */
	public String publish() {
		final var jwks = Json.createArrayBuilder();

		for (final var key : providerKeys) {
			final var jwkb = Json.createObjectBuilder();
			jwkb.add("kid", key.getId());

			switch (key.getUsage()) {
			case ENCRYPT:
				jwkb.add("use", "enc");
				break;
			case SIGN:
				jwkb.add("use", "sig");
				break;
			}

			switch (key.getType()) {
			case RSA: {
				final var rsa = (RSAPublicKey) key.getPublic();
				jwkb.add("kty", "RSA");
				jwkb.add("e", Base64.getUrlEncoder().encodeToString(rsa.getPublicExponent().toByteArray()));
				jwkb.add("n", Base64.getUrlEncoder().encodeToString(rsa.getModulus().toByteArray()));
				break;
			}

			case EC_P256: {
				final var w = ((ECPublicKey) key.getPublic()).getW();
				jwkb.add("kty", "EC");
				jwkb.add("crv", "P-256");
				jwkb.add("x", Base64.getUrlEncoder().encodeToString(w.getAffineX().toByteArray()));
				jwkb.add("y", Base64.getUrlEncoder().encodeToString(w.getAffineY().toByteArray()));
				break;
			}

			case EC_P384: {
				final var w = ((ECPublicKey) key.getPublic()).getW();
				jwkb.add("kty", "EC");
				jwkb.add("crv", "P-384");
				jwkb.add("x", Base64.getUrlEncoder().encodeToString(w.getAffineX().toByteArray()));
				jwkb.add("y", Base64.getUrlEncoder().encodeToString(w.getAffineY().toByteArray()));
				break;
			}

			case EC_P521: {
				final var w = ((ECPublicKey) key.getPublic()).getW();
				jwkb.add("kty", "EC");
				jwkb.add("crv", "P-521");
				jwkb.add("x", Base64.getUrlEncoder().encodeToString(w.getAffineX().toByteArray()));
				jwkb.add("y", Base64.getUrlEncoder().encodeToString(w.getAffineY().toByteArray()));
				break;
			}
			}

			jwks.add(jwkb);
		}
		return Json.createObjectBuilder().add("keys", jwks).build().toString();
	}

}