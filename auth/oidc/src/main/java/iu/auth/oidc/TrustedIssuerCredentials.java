package iu.auth.oidc;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.auth.config.X500Utils;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Encapsulates a non-registered {@link IuAuthorizationCredentials} trusted to issue private
 * key assertions on behalf of an {@link IuAuthorizationClient}.
 */
class TrustedIssuerCredentials implements IuAuthorizationCredentials {

	private final WebCryptoHeader jose;
	private final X509Certificate cert;
	private final WebKey jwk;

	/**
	 * Constructor
	 * 
	 * @param jose {@link WebCryptoHeader}
	 */
	TrustedIssuerCredentials(WebCryptoHeader jose) {
		this.jose = jose;
		this.cert = WebCertificateReference.verify(jose)[0];
		this.jwk = WebKey.builder(jose.getAlgorithm()) //
				.keyId(X500Utils.getCommonName(cert.getSubjectX500Principal())) //
				.cert(cert).build();
	}

	@Override
	public Algorithm getAlg() {
		return jose.getAlgorithm();
	}

	@Override
	public Algorithm getEncryptAlg() {
		return null;
	}

	@Override
	public Encryption getEnc() {
		return null;
	}

	@Override
	public WebKey getJwk() {
		return jwk;
	}

	@Override
	public Type getType() {
		return Type.CREDENTIALS;
	}

	@Override
	public Set<GrantType> getGrantTypes() {
		return EnumSet.allOf(GrantType.class);
	}

	@Override
	public AuthMethod getTokenEndpointAuthMethod() {
		return AuthMethod.PRIVATE_KEY_JWT;
	}

	@Override
	public Instant getExpires() {
		return cert.getNotAfter().toInstant();
	}

}
