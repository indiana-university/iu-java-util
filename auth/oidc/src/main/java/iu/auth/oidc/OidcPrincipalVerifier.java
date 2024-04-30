package iu.auth.oidc;

import edu.iu.auth.IuAuthenticationException;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link OidcPrincipal OpenID Connection principals}.
 */
final class OidcPrincipalVerifier implements PrincipalVerifier<OidcPrincipal> {

	private final boolean authoritative;
	private final String realm;

	/**
	 * Constructor.
	 * 
	 * @param authoritative authoritative flag: requires ID token or client ID to
	 *                      match principal claim when true; relies on userinfo
	 *                      access token for verification
	 * @param realm         authentication realm
	 */
	OidcPrincipalVerifier(boolean authoritative, String realm) {
		this.authoritative = authoritative;
		this.realm = realm;
	}

	@Override
	public Class<OidcPrincipal> getType() {
		return OidcPrincipal.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return authoritative;
	}

	@Override
	public void verify(OidcPrincipal id, String realm) throws IuAuthenticationException {
		if (!id.realm().equals(realm))
			throw new IllegalArgumentException("Principal is invalid for the authenticaiton realm");

		id.getClaims();
	}

}
