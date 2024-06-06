package iu.auth.jwt;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.XECPublicKey;
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
import edu.iu.client.IuJsonBuilder;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;

/**
 * JWT builder implementation.
 */
class JwtBuilder extends IuJsonBuilder<JwtBuilder> {

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

	/**
	 * Sets the subject.
	 * 
	 * @param sub   subject principal; <em>must</em> be
	 *              {@link IuPrincipalIdentity#verify(IuPrincipalIdentity, String)
	 *              verifiable as authoritative} for the authentication realm
	 * @param realm authentication realm
	 * @return this
	 */
	public JwtBuilder subject(IuPrincipalIdentity sub, String realm) {
		if (!IuException.unchecked(() -> IuPrincipalIdentity.verify(sub, realm)))
			throw new IllegalArgumentException("Not authoritative");
		param("sub", sub.getName());
		return this;
	}

	/**
	 * Sets the audience.
	 * 
	 * @param audience   audience principal; <em>must</em> be
	 *              {@link IuPrincipalIdentity#verify(IuPrincipalIdentity, String)
	 *              verifiable} for the authentication realm; <em>may</em>
	 *              non-authoritative. If the
	 *              {@link IuPrincipalIdentity#getSubject()} includes a public key
	 *              designated with <strong>use</strong> = "enc", and/or
	 *              <strong>key_op</strong> including "wrapKey" or "deriveKey" and
	 *              only one audience is provided to the builder, the JWT will be
	 *              encrypted.
	 * @param realm authentication realm
	 * @return this
	 * @see #encrypt(String, String)
	 */
	public JwtBuilder audience(IuPrincipalIdentity audience, String realm) {
		IuException.unchecked(() -> IuPrincipalIdentity.verify(audience, realm));
		if (this.audience != null)
			this.audience = audience;
		this.realm = IuObject.once(this.realm, realm);
		aud.offer(audience.getName());
		return this;
	}

	/**
	 * Sets the time before which the JWT should not be accepted.
	 * 
	 * @param nbf not before time
	 * @return this
	 */
	public JwtBuilder notBefore(Instant nbf) {
		if (nbf.isBefore(Instant.now().minusSeconds(15L)))
			throw new IllegalArgumentException("nbf must not be more than PT15S in the past");
		param("nbf", nbf, Jwt.NUMERIC_DATE);
		return this;
	}

	/**
	 * Sets the time after which the JWT should not be accepted.
	 * 
	 * @param exp not before time
	 * @return this
	 */
	public JwtBuilder expires(Instant exp) {
		if (exp.isBefore(Instant.now().plusSeconds(15L)))
			throw new IllegalArgumentException("exp must be at least 15 seconds in the future");
		param("exp", exp, Jwt.NUMERIC_DATE);
		return this;
	}

	/**
	 * Sets an extended claim value.
	 * 
	 * <p>
	 * <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1">RFC-7519
	 * JWT Registered Claims</a> are not included. Public claim names registered
	 * with IANA <em>should</em> be used in accordance with linked specifications.
	 * </p>
	 * 
	 * @param name  claim name
	 * @param value claim value
	 * @return this
	 * @see <a href="https://www.iana.org/assignments/jwt/jwt.xhtml">IANA JWT
	 *      Assignments</a>
	 */
	public JwtBuilder claim(String name, Object value) {
		if (REGISTERED_CLAIMS.contains(name))
			throw new IllegalArgumentException("invalid extended claim name");
		param(name, value);
		return this;
	}

	/**
	 * Requires a single {@link #audience(IuPrincipalIdentity, String) audience}
	 * principal that includes a public key, and sets content encryption algorithm
	 * to use for encryption.
	 * 
	 * <p>
	 * Algorithm parameters <em>must</em> be valid registered JOSE header values. If
	 * not specified, but the JWT is for a single audience principal that includes a
	 * public key, key encryption will be based on key type. Default content
	 * encryption algorithm is A128CBC-HS256.
	 * </p>
	 * 
	 * <dl>
	 * <dt>{@link RSAPublicKey}</dt>
	 * <dd>RSA-OAEP</dd>
	 * <dt>{@link ECPublicKey} or {@link XECPublicKey}</dt>
	 * <dd>ECDH-ES</dd>
	 * </dl>
	 * 
	 * @param alg key encryption algorithm
	 * @param enc content encryption algorithm
	 * 
	 * @return this
	 * @see <a href=
	 *      "https://www.iana.org/assignments/jose/jose.xhtml#web-signature-encryption-algorithms">IANA
	 *      JOSE Registry</a>
	 */
	public JwtBuilder encrypt(String alg, String enc) {
		this.alg = IuObject.once(this.alg, Algorithm.from(alg));
		this.enc = IuObject.once(this.enc, Encryption.from(enc));
		return this;
	}

	/**
	 * Signs, <em>optionally</em> encrypts, and issues the JWT using the default
	 * signature algorithm by issuer key type.
	 * 
	 * <dl>
	 * <dt>{@link RSAPrivateKey} with {@link RSAPrivateKey#getAlgorithm()} of
	 * "RSA"</dt>
	 * <dd>RS256</dd>
	 * <dt>{@link RSAPrivateKey} with {@link RSAPrivateKey#getAlgorithm()} of
	 * "RSASSA-PSS"</dt>
	 * <dd>PS256</dd>
	 * <dt>{@link ECPrivateKey}</dt>
	 * <dd>ES256</dd>
	 * <dt>EdECPrivateKey (JDK 15 or higher)</dt>
	 * <dd>EdDSA</dd>
	 * </dl>
	 * 
	 * @return {@link IuWebToken}
	 */
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

	/**
	 * Signs, <em>optionally</em> encrypts, and issues the JWT.
	 * 
	 * @param alg Signature algorithm
	 * @return {@link IuWebToken}
	 */
	public IuWebToken sign(String alg) {
		final var claims = toJson();
		Objects.requireNonNull(audience, "missing audience");

		final WebKey encryptKey;
		if (aud.size() > 1) {
			param("aud", aud);
			encryptKey = null;
			if (this.enc != null)
				throw new IllegalArgumentException("can only encrypt to a single audience");
		} else {
			param("aud", audience.getName());
			encryptKey = JwtSpi.getEncryptKey(audience);
		}

		Objects.requireNonNull(claims.get("sub"), "missing subject princpal");
		Objects.requireNonNull(claims.get("exp"), "missing expiration time");

		final var jws = WebSignature //
				.builder(Algorithm.from(alg)) //
				.type("JWT") //
				.key(signingKey) //
				.sign(IuText.utf8(claims.toString()));

		if (encryptKey == null)
			return new Jwt(realm, jws.compact());

		final Algorithm algorithm;
		if (this.alg != null)
			algorithm = this.alg;
		else
			switch (encryptKey.getType()) {
			case EC_P256:
			case EC_P384:
			case EC_P521:
			case X25519:
			case X448:
				algorithm = Algorithm.ECDH_ES;
				break;

			case RSA:
				algorithm = Algorithm.RSA_OAEP;
				break;

			default:
				throw new IllegalArgumentException("invalid encryption key");
			}

		final var jweBuilder = WebEncryption //
				.builder(Objects.requireNonNullElse(enc, Encryption.AES_128_CBC_HMAC_SHA_256));

//				.addRecipient()
		// TODO Auto-generated method stub
		return null;
	}

}
