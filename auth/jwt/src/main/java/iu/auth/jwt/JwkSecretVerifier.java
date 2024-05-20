package iu.auth.jwt;

import java.net.URI;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPublicKeyPrincipalConfig;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies a secret key as {@link Jwt} issuer or audience principal.
 */
final class JwkSecretVerifier implements PrincipalVerifier<JwkSecret>, IuPublicKeyPrincipalConfig {

	private final JwkSecret jwk;

	/**
	 * Constructor.
	 * 
	 * @param jwk JWK principal
	 */
	JwkSecretVerifier(JwkSecret jwk) {
		this.jwk = jwk;
	}

	@Override
	public IuPrincipalIdentity getIdentity() {
		return jwk;
	}

	@Override
	public String getAuthScheme() {
		return null;
	}

	@Override
	public URI getAuthenticationEndpoint() {
		return null;
	}

	@Override
	public Class<JwkSecret> getType() {
		return JwkSecret.class;
	}

	@Override
	public String getRealm() {
		return jwk.getName();
	}

	@Override
	public boolean isAuthoritative() {
		return true;
	}

	@Override
	public void verify(JwkSecret id, String realm) throws IuAuthenticationException {
		if (!id.getName().equals(realm))
			throw new IllegalArgumentException("realm mismatch");
		else if (id != jwk)
			throw new IllegalArgumentException();
	}

}
