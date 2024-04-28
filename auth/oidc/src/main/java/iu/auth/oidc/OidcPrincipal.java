package iu.auth.oidc;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oidc.IuOpenIdPrincipal;

/**
 * OpenID Connect principal identity implementation;
 */
final class OidcPrincipal implements IuOpenIdPrincipal {
	private static final long serialVersionUID = 1L;

	private final String idToken;
	private final String accessToken;
	private final String name;
	private final String realm;
	private boolean revoked;

	private transient Map<String, ?> claims;
	private transient Instant claimsVerified;

	/**
	 * Constructor.
	 * 
	 * @param idToken     id token
	 * @param accessToken access token
	 * @param provider    issuing provider
	 * @throws IuAuthenticationException if the ID or access token is invalid
	 */
	OidcPrincipal(String idToken, String accessToken, OpenIdProvider provider) {
		this.idToken = idToken;
		this.accessToken = accessToken;
		this.realm = provider.client().getRealm();
		this.claims = provider.getClaims(idToken, accessToken);
		this.name = Objects.requireNonNull((String) claims.get("principal"));
		this.claimsVerified = Instant.now();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Map<String, ?> getClaims() {
		final var provider = OpenIdConnectSpi.getProvider(realm);
		final var now = Instant.now();
		if (now.isAfter(claimsVerified.plus(provider.client().getVerificationInterval()))) {
			claims = provider.getClaims(idToken, accessToken);
			IuObject.once(name, claims.get("principal"));
			claimsVerified = now;
		}
		return claims;
	}

	@Override
	public Subject getSubject() {
		final var subject = new Subject();
		subject.getPrincipals().add(this);
		subject.setReadOnly();
		return subject;
	}

	@Override
	public void revoke() {
		revoked = true;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(name, realm);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		OidcPrincipal other = (OidcPrincipal) obj;
		return IuObject.equals(name, other.name) //
				&& IuObject.equals(realm, other.realm);
	}

	@Override
	public String toString() {
		return "OIDC Principal ID [" + name + "; " + realm + "] " + claims;
	}

	/**
	 * Determines if the principal has been revoked.
	 * 
	 * @return true if revoked; else false
	 */
	boolean revoked() {
		return revoked;
	}

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	String realm() {
		return realm;
	}
}
