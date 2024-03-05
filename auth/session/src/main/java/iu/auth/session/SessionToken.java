package iu.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.oauth.IuAuthorizationScope;
import edu.iu.auth.session.IuSessionToken;
import iu.auth.oauth.BearerAuthCredentials;
import jakarta.json.Json;

/**
 * {@link IuSessionToken} implementation class;
 */
public class SessionToken extends BearerAuthCredentials implements IuSessionToken {
	private static final long serialVersionUID = 1L;

	private final String refreshToken;
	private final Instant tokenExpires;
	private final Instant sessionExpires;

	/**
	 * Constructor.
	 * 
	 * @param subject        authorized subject
	 * @param accessToken    access token
	 * @param refreshToken   refresh token
	 * @param tokenExpires   token expiration time
	 * @param sessionExpires session expiration times
	 */
	public SessionToken(Subject subject, String accessToken, String refreshToken, Instant tokenExpires,
			Instant sessionExpires) {
		super(subject, accessToken);
		this.tokenExpires = tokenExpires.truncatedTo(ChronoUnit.SECONDS);

		this.refreshToken = refreshToken;
		if (refreshToken == null)
			this.sessionExpires = null;
		else
			this.sessionExpires = sessionExpires.truncatedTo(ChronoUnit.SECONDS);
	}

	@Override
	public Instant getTokenExpires() {
		return tokenExpires;
	}

	@Override
	public Instant getSessionExpires() {
		return sessionExpires;
	}

	@Override
	public String getRefreshToken() {
		return refreshToken;
	}

	@Override
	public String asTokenResponse() {
		final var expiresIn = Duration.between(Instant.now(), tokenExpires).toSeconds();
		if (expiresIn < 1)
			throw new IllegalStateException("session is expired");

		final var scope = new StringBuilder();
		for (final var authScope : getSubject().getPrincipals(IuAuthorizationScope.class)) {
			if (scope.length() > 0)
				scope.append(' ');
			scope.append(authScope.getName());
		}

		final var responseBuilder = Json.createObjectBuilder();
		responseBuilder.add("token_type", "Bearer");
		responseBuilder.add("access_token", getAccessToken());
		responseBuilder.add("expires_in", expiresIn);
		responseBuilder.add("scope", scope.toString());
		if (refreshToken != null)
			responseBuilder.add("refresh_token", refreshToken);

		return responseBuilder.toString();
	}

	@Override
	public int hashCode() {
		return IuObject.hashCodeSuper(super.hashCode(), refreshToken, tokenExpires, sessionExpires);
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj))
			return false;
		SessionToken other = (SessionToken) obj;
		return IuObject.equals(refreshToken, other.refreshToken) //
				&& IuObject.equals(tokenExpires, other.tokenExpires) //
				&& IuObject.equals(sessionExpires, other.sessionExpires);
	}

}
