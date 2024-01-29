package iu.auth.oauth;

import java.security.Principal;
import java.time.Instant;

import edu.iu.auth.oauth.IuAuthorizationResponse;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * OAuth token response wrapper implementation.
 */
class AuthorizationResponse implements IuAuthorizationResponse {

	private final Principal principal;
	private final JsonObject tokenResponse;
	private final Instant expires;

	/**
	 * Constructor.
	 * 
	 * @param principal     authenticated principal
	 * @param tokenResponse parsed token response
	 */
	AuthorizationResponse(Principal principal, JsonObject tokenResponse) {
		this.principal = principal;
		this.tokenResponse = tokenResponse;

		final long seconds;
		final var expiresIn = tokenResponse.get("expires_in");
		if (expiresIn instanceof JsonString a)
			seconds = Long.parseLong(a.getString());
		else if (expiresIn instanceof JsonNumber a)
			seconds = a.intValue();
		else
			throw new IllegalStateException("Invalid expires_in attribute in token response");
		expires = Instant.now().plusSeconds(seconds);
	}

	@Override
	public Principal getPrincipal() {
		return principal;
	}

	@Override
	public String getAccessToken() {
		return tokenResponse.getString("access_token");
	}

	/**
	 * Determines if this grant has expired.
	 * 
	 * @return true if expired; else false
	 */
	boolean isExpired() {
		return expires.isBefore(Instant.now());
	}

}
