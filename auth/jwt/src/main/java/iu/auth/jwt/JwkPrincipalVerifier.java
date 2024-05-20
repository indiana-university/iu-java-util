package iu.auth.jwt;

import java.net.URI;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPublicKeyPrincipalConfig;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies a registered {@link Jwt} issuer or audience principal.
 */
final class JwkPrincipalVerifier implements PrincipalVerifier<JwkPrincipal>, IuPublicKeyPrincipalConfig {

	private final JwkPrincipal jwk;

	/**
	 * Constructor.
	 * 
	 * @param jwk JWK principal
	 */
	JwkPrincipalVerifier(JwkPrincipal jwk) {
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
	public Class<JwkPrincipal> getType() {
		return JwkPrincipal.class;
	}

	@Override
	public String getRealm() {
		return jwk.getName();
	}

	@Override
	public boolean isAuthoritative() {
		return false;
	}

	@Override
	public void verify(JwkPrincipal id, String realm) throws IuAuthenticationException {
		if (!getRealm().equals(realm))
			throw new IllegalArgumentException("realm mismatch");
		else if (id != jwk)
			throw new IllegalArgumentException();
	}

}
