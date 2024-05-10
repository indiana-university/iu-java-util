package iu.auth.jwt;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.jwt.IuWebKey;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link IuWebKey} registered as a {@link Jwt} issuer or audience
 * principal.
 */
final class JwkSecretVerifier implements PrincipalVerifier<JwkSecret> {

	private final String realm;

	/**
	 * Constructor.
	 * 
	 * @param realm JWK principal realm
	 */
	JwkSecretVerifier(String realm) {
		this.realm = realm;
	}

	@Override
	public Class<JwkSecret> getType() {
		return JwkSecret.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return true;
	}

	@Override
	public void verify(JwkSecret id, String realm) throws IuAuthenticationException {
		if (!id.getName().equals(realm))
			throw new IllegalArgumentException("realm mismatch");
	}

}
