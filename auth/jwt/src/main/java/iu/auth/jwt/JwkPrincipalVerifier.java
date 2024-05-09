package iu.auth.jwt;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.jwt.IuWebKey;
import edu.iu.crypt.WebKey;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link IuWebKey} registered as a {@link Jwt} issuer or audience
 * principal.
 */
final class JwkPrincipalVerifier implements PrincipalVerifier<JwkPrincipal> {

	private final String realm;

	/**
	 * Constructor.
	 * 
	 * @param realm JWK principal realm
	 */
	JwkPrincipalVerifier(String realm) {
		this.realm = realm;
	}

	@Override
	public Class<JwkPrincipal> getType() {
		return JwkPrincipal.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return false;
	}

	@Override
	public void verify(JwkPrincipal id, String realm) throws IuAuthenticationException {
		if (!id.getName().equals(realm))
			throw new IllegalArgumentException("realm mismatch");
		id.getSubject().getPublicCredentials(WebKey.class).iterator().next();
	}

}
