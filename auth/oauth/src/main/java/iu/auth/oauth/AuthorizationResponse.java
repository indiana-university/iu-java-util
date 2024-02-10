package iu.auth.oauth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * OAuth token response wrapper implementation.
 */
class AuthorizationResponse implements IuAuthorizationResponse {

	private final String clientId;
	private final String tokenType;
	private final String accessToken;
	private final String refreshToken;
	private final Set<String> scope;
	private final Instant expires;
	private final Map<String, String> clientAttributes;
	private final Map<String, String> attributes;

	/**
	 * Constructor.
	 * 
	 * @param grant
	 * @param client
	 * @param principal     authenticated principal
	 * @param tokenResponse parsed token response
	 */
	AuthorizationResponse(IuAuthorizationGrant grant, IuAuthorizationClient client, JsonObject tokenResponse) {
		this.clientId = client.getCredentials().getName();

		// https://datatracker.ietf.org/doc/html/rfc6749#section-5.2
		if (tokenResponse.containsKey("error"))
			throw new IllegalStateException("Error in token response; " + tokenResponse);

		// https://datatracker.ietf.org/doc/html/rfc6749#section-5.1
		if (!tokenResponse.containsKey("token_type"))
			throw new IllegalStateException("Token response missing token_type; " + tokenResponse);
		else
			tokenType = tokenResponse.getString("token_type");

		if (!tokenResponse.containsKey("access_token"))
			throw new IllegalStateException("Token response missing access_token; " + tokenResponse);
		else
			accessToken = tokenResponse.getString("access_token");

		final var expiresIn = tokenResponse.get("expires_in");
		if (expiresIn instanceof JsonString a)
			expires = Instant.now().plusSeconds(Long.parseLong(a.getString()));
		else if (expiresIn instanceof JsonNumber a)
			expires = Instant.now().plusSeconds(a.intValue());
		else
			expires = null;

		if (!tokenResponse.containsKey("refresh_token"))
			refreshToken = null;
		else
			refreshToken = tokenResponse.getString("refresh_token");

		// https://datatracker.ietf.org/doc/html/rfc6749#section-3.3
		final var scope = grant.getScope();
		if (!tokenResponse.containsKey("scope"))
			this.scope = Set.of(scope.split(" "));
		else
			this.scope = Set.of(tokenResponse.getString("scope"));
		
		// TODO: client attributes

		final Map<String, String> attributes = new LinkedHashMap<>();
		for (final var tokenEntry : tokenResponse.entrySet()) {
			final var key = tokenEntry.getKey();
			if (!key.equals("token_type") //
					&& !key.equals("access_token") //
					&& !key.equals("expires_in") //
					&& !key.equals("refresh_token") //
					&& !key.equals("scope")) {
				final var value = tokenEntry.getValue();
				if (value instanceof JsonString s)
					attributes.put(key, s.getString());
				else
					attributes.put(key, value.toString());
			}
		}
		this.attributes = attributes;
	}

	@Override
	public String getClientId() {
		return clientId;
	}

	@Override
	public String getTokenType() {
		return tokenType;
	}

	@Override
	public String getAccessToken() {
		return accessToken;
	}

	@Override
	public Set<String> getScope() {
		return scope;
	}

	@Override
	public Map<String, String> getAttributes() {
		return attributes;
	}

	/**
	 * Gets the refresh token, if provided by the token endpoint.
	 * 
	 * @return refresh token; null if not provided
	 */
	String getRefreshToken() {
		return refreshToken;
	}

	/**
	 * Determines if this grant has expired.
	 * 
	 * @return true if expired; else false
	 */
	boolean isExpired() {
		return expires.isBefore(Instant.now());
	}

	@Override
	public Map<String, String> getClientAttributes() {
		// TODO Auto-generated method stub
		return null;
	}

}
