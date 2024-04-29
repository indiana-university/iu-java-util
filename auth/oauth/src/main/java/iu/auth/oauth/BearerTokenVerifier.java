package iu.auth.oauth;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link BearerToken} instances.
 */
final class BearerTokenVerifier implements PrincipalVerifier<BearerToken> {

	private final ThreadLocal<Boolean> loop = new ThreadLocal<>();
	private final String realm;
	private final String scope;

	/**
	 * Constructor.
	 * 
	 * @param realm bearer authentication realm
	 * @param scope OAuth formatted scope string
	 */
	BearerTokenVerifier(String realm, String scope) {
		this.realm = realm;
		this.scope = scope;
	}

	@Override
	public Class<BearerToken> getType() {
		return BearerToken.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		for (final var realm : OAuthSpi.getClient(realm).getPrincipalRealms())
			if (realm.equals(this.realm))
				return true;
		return false;
	}

	@Override
	public void verify(BearerToken id, String realm) throws IuAuthenticationException {
		final var client = OAuthSpi.getClient(realm);

		if (!realm.equals(this.realm))
			throw challenge("invalid_token", "Invalid token for realm");

		if (id.expired())
			throw challenge("invalid_token", "Token is expired");

		for (final var idRealm : client.getPrincipalRealms())
			if (id.realm().equals(idRealm))
				if (id == id.getSubject().getPrincipals().iterator().next())
					if (realm.equals(idRealm))
						return; // authoritative bearer token
					else
						throw challenge("invalid_token", "Invalid token for principal realm");
				else {
					if (Boolean.TRUE.equals(loop.get()))
						throw new IllegalStateException("illegal principal reference");
					try {
						loop.set(true);
						id.verifyPrincipal();
					} finally {
						loop.remove();
					}
				}
	}

	private IuAuthenticationException challenge(String error, String errorDescription) {
		final Map<String, String> params = new LinkedHashMap<>();
		params.put("realm", realm);
		if (scope != null)
			params.put("scope", scope);
		params.put("error", error);
		params.put("error_description", errorDescription);
		return new IuAuthenticationException(IuWebUtils.createChallenge("Bearer", params));
	}

}
