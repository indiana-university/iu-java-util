package iu.auth.jwt;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.jwt.IuWebToken.Builder;
import edu.iu.client.IuJsonBuilder;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;

/**
 * JWT {@link Builder} implementation.
 */
class JwtBuilder extends IuJsonBuilder<JwtBuilder> implements Builder {

	private static final Set<String> REGISTERED_CLAIMS = Set.of("iss", "sub", "aud", "iat", "nbf", "exp");

	private Queue<String> aud = new ArrayDeque<>();
	private IuPrincipalIdentity issuer;
	private WebKey signingKey;
	private IuPrincipalIdentity audience;
	private String realm;
	private Algorithm alg;
	private Encryption enc;

	/**
	 * Constructor.
	 * 
	 * @param issuer     Issuer principal identity
	 * @param signingKey Signing key
	 */
	JwtBuilder(IuPrincipalIdentity issuer, WebKey signingKey) {
		if (!IuException.unchecked(() -> IuPrincipalIdentity.verify(issuer, issuer.getName())))
			throw new IllegalArgumentException("Not authoritative");
		this.issuer = issuer;
		param("iss", issuer.getName());
	}

	@Override
	public Builder subject(IuPrincipalIdentity sub, String realm) {
		if (!IuException.unchecked(() -> IuPrincipalIdentity.verify(sub, realm)))
			throw new IllegalArgumentException("Not authoritative");
		param("sub", sub.getName());
		return this;
	}

	@Override
	public Builder audience(IuPrincipalIdentity audience, String realm) {
		IuException.unchecked(() -> IuPrincipalIdentity.verify(audience, realm));
		if (this.audience != null)
			this.audience = audience;
		this.realm = IuObject.once(this.realm, realm);
		aud.offer(audience.getName());
		return this;
	}

	@Override
	public Builder notBefore(Instant nbf) {
		if (nbf.isBefore(Instant.now().minusSeconds(15L)))
			throw new IllegalArgumentException("nbf must not be more than PT15S in the past");
		param("nbf", nbf, Jwt.NUMERIC_DATE);
		return this;
	}

	@Override
	public Builder expires(Instant exp) {
		if (exp.isBefore(Instant.now().plusSeconds(15L)))
			throw new IllegalArgumentException("exp must be at least 15 seconds in the future");
		param("exp", exp, Jwt.NUMERIC_DATE);
		return this;
	}

	@Override
	public Builder claim(String name, Object value) {
		if (REGISTERED_CLAIMS.contains(name))
			throw new IllegalArgumentException("invalid extended claim name");
		param(name, value);
		return this;
	}

	@Override
	public Builder encrypt(String alg, String enc) {
		this.alg = IuObject.once(this.alg, Algorithm.from(alg));
		this.enc = IuObject.once(this.enc, Encryption.from(enc));
		return this;
	}

	@Override
	public IuWebToken sign() {
		final var alg = signingKey.getAlgorithm();

		if (alg != null)
			return sign(alg.alg);
		else
			switch (signingKey.getType()) {
			case EC_P256:
			case EC_P384:
			case EC_P521:
				return sign("ES256");

			case ED25519:
			case ED448:
				return sign("EdDSA");

			case RSA:
				return sign("RS256");

			case RSASSA_PSS:
				return sign("PS256");

			case RAW:
				return sign("HS256");

			default:
				throw new IllegalStateException("invalid signing key");
			}
	}

	@Override
	public IuWebToken sign(String alg) {
		final var claims = toJson();
		Objects.requireNonNull(audience, "missing audience");

		final WebKey encryptKey;
		if (aud.size() > 1) {
			param("aud", aud);
			encryptKey = null;
		} else {
			param("aud", audience.getName());
			encryptKey = JwtSpi.getEncryptKey(audience);
		}

		Objects.requireNonNull(claims.get("sub"), "missing subject princpal");
		Objects.requireNonNull(claims.get("exp"), "missing expiration time");

		final var jws = WebSignature.builder(Algorithm.from(alg)).key(signingKey).sign(IuText.utf8(claims.toString()));
		if (encryptKey == null)
			return new Jwt(alg, alg);
		// TODO Auto-generated method stub
		return null;
	}

}
