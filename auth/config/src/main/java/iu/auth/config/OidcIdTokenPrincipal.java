package iu.auth.config;

import java.time.Instant;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.auth.IuPrincipalIdentity;
import jakarta.json.JsonObject;

/**
 * Session-bound {@link IuPrincipalIdentity} instance sourced from
 * {@link OidcIdToken}.
 */
public class OidcIdTokenPrincipal implements IuPrincipalIdentity {

	private final OidcIdToken idToken;

	/**
	 * Constructor.
	 * 
	 * @param idToken        ID token
	 * @param userinfoClaims Claims provided by the userinfo endpoint
	 */
	public OidcIdTokenPrincipal(OidcIdToken idToken, JsonObject userinfoClaims) {
		this.idToken = idToken;
		if (!userinfoClaims.containsKey("sub"))
			throw new IllegalArgumentException("userinfo missing sub claim");
		if (!userinfoClaims.getString("sub").equals(idToken.getSubject()))
			throw new IllegalArgumentException("userinfo sub claim doesn't match id token");
	}

	@Override
	public String getIssuer() {
		return idToken.getIssuer().toString();
	}

	@Override
	public Instant getIssuedAt() {
		return idToken.getIssuedAt();
	}

	@Override
	public Instant getAuthTime() {
		return idToken.getAuthTime();
	}

	@Override
	public Instant getExpires() {
		return idToken.getExpires();
	}

	@Override
	public Subject getSubject() {
		return new Subject(true, Set.of(this), Set.of(), Set.of());
	}

	@Override
	public String getName() {
		return idToken.getSubject();
	}

}
