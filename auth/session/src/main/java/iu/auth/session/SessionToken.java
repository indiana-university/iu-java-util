/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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

	private static String getSessionRealm(Subject subject) {
		for (final var scope : subject.getPrincipals(IuAuthorizationScope.class))
			if (scope.getName().equals("session"))
				return scope.getRealm();
		throw new IllegalArgumentException("Missing session scope " + subject);
	}

	/**
	 * Refresh token.
	 */
	private final String refreshToken;

	/**
	 * Token expiration time.
	 */
	private final Instant tokenExpires;

	/**
	 * Session expiration time.
	 */
	private final Instant sessionExpires;

	/**
	 * Constructor.
	 * 
	 * @param subject      authorized subject
	 * @param accessToken  access token
	 * @param tokenExpires token expiration time
	 */
	public SessionToken(Subject subject, String accessToken, Instant tokenExpires) {
		this(subject, accessToken, tokenExpires, null, null);
	}

	/**
	 * Constructor.
	 * 
	 * @param subject        authorized subject
	 * @param accessToken    access token
	 * @param refreshToken   refresh token
	 * @param tokenExpires   token expiration time
	 * @param sessionExpires session expiration times
	 */
	public SessionToken(Subject subject, String accessToken, Instant tokenExpires, String refreshToken,
			Instant sessionExpires) {
		super(getSessionRealm(subject), subject, accessToken);
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

		return responseBuilder.build().toString();
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
