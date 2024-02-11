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
package iu.auth.oauth;

import java.io.NotSerializableException;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IuBadRequestException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuBearerAuthCredentials;
import edu.iu.auth.oauth.IuTokenResponse;

/**
 * Represents an OAuth client credentials grant..
 */
sealed abstract class AbstractGrant implements IuAuthorizationGrant, Serializable
		permits ClientCredentialsGrant, AuthorizationCodeGrant {

	private static final long serialVersionUID = 1L;

	private record AuthorizedCredentials<A extends IuApiCredentials & Serializable>(A credentials, Instant expires) {
		private boolean isExpired() {
			return expires.isBefore(Instant.now());
		}
	}

	/**
	 * Validates and converts a configured scope
	 *
	 * @param scope configured scope
	 * @return scope attribute
	 */
	static String validateScope(Iterable<String> scope) {
		if (scope == null)
			return null;

		final var sb = new StringBuilder();
		scope.forEach(scopeToken -> {
			// scope = scope-token *( SP scope-token )
			if (sb.length() > 0)
				sb.append(' ');
			// scope-token = 1*( %x21 / %x23-5B / %x5D-7E )
			for (var i = 0; i < scopeToken.length(); i++) {
				final var c = (int) scopeToken.charAt(i);
				if (c < 0x21 || c == 0x22 || c == 0x5c || c > 0x7e)
					throw new IllegalArgumentException();
			}
		});

		if (sb.isEmpty())
			return null;
		else
			return sb.toString();
	}

	/**
	 * Configured scope, validated and serialized as a request attribute.
	 * 
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-3.3">RFC-6749
	 *      OAuth 2.0 Section-3.3</a>
	 */
	protected final String validatedScope;

	/**
	 * Authentication realm, use with {@link OAuthSpi#getClient(String)}.
	 */
	protected final String realm;

	private AuthorizedCredentials<?> authorizedCredentials;

	/**
	 * Constructor.
	 * 
	 * @param realm authentication realm
	 * @param scope authorization scope
	 */
	AbstractGrant(String realm) {
		this.realm = realm;
		this.validatedScope = validateScope(OAuthSpi.getClient(realm).getScope());
	}

	@Override
	public final void revoke() {
		final var authorizedCredentials = this.authorizedCredentials;
		this.authorizedCredentials = null;
		if (authorizedCredentials != null)
			OAuthSpi.getClient(realm).revoke(authorizedCredentials.credentials);
	}

	/**
	 * Verifies a token response.
	 * 
	 * @param tokenResponse token response
	 * @return verified credentials, will be held internally until the expiration
	 *         time implied by the token response has passed.
	 * @throws IuAuthenticationException if the token response cannot be verified
	 */
	protected final IuApiCredentials verify(IuTokenResponse tokenResponse) throws IuAuthenticationException {
		if (!"Bearer".equals(tokenResponse.getTokenType()))
			throw new IuBadRequestException("Unsupported token type");

		return authorizeBearer(OAuthSpi.getClient(realm).verify(tokenResponse), tokenResponse);
	}

	/**
	 * Verifies a refresh token response.
	 * 
	 * @param refreshTokenResponse  refresh token response
	 * @param originalTokenResponse {@link #verify(IuTokenResponse) verified} token
	 *                              response previously
	 * @return verified credentials, will be held internally until the expiration
	 *         time implied by the token response has passed.
	 * @throws IuAuthenticationException if the token response cannot be verified
	 */
	protected final IuApiCredentials verify(IuTokenResponse refreshTokenResponse, IuTokenResponse originalTokenResponse)
			throws IuAuthenticationException {
		if (!"Bearer".equals(refreshTokenResponse.getTokenType()))
			throw new IuBadRequestException("Unsupported token type");

		return authorizeBearer(OAuthSpi.getClient(realm).verify(refreshTokenResponse, originalTokenResponse),
				refreshTokenResponse);
	}

	/**
	 * Activated and returns the credentials authorized by this grant.
	 * 
	 * @return {@link IuApiCredentials}; null if credential have not been authorized
	 *         or are expired
	 * @throws IuAuthenticationException if activation failed to validate the
	 *                                   credentials and as valid
	 */
	protected final IuApiCredentials activate() throws IuAuthenticationException {
		if (authorizedCredentials == null || authorizedCredentials.isExpired())
			return null;

		OAuthSpi.getClient(realm).activate(authorizedCredentials.credentials);

		return authorizedCredentials.credentials;
	}

	/**
	 * Determines if the authorized credentials are expired.
	 * 
	 * @return true if authorized credentials were established and have expired;
	 *         else false
	 */
	protected boolean isExpired() {
		return authorizedCredentials != null && authorizedCredentials.isExpired();
	}

	private IuBearerAuthCredentials authorizeBearer(Subject subject, IuTokenResponse tokenResponse) {
		final Set<Principal> principals = new LinkedHashSet<>();

		for (final var principal : subject.getPrincipals())
			if (principal instanceof Serializable)
				principals.add(principal);
			else
				throw new IllegalStateException(new NotSerializableException(principal.getClass().getName()));

		for (final var scope : tokenResponse.getScope())
			principals.add(new AuthorizedScope(scope, OAuthSpi.getClient(realm).getRealm()));

		final var bearerSubject = new Subject(true, principals, subject.getPublicCredentials(),
				subject.getPrivateCredentials());

		final var bearer = new BearerAuthCredentials(bearerSubject, tokenResponse.getAccessToken());
		authorizedCredentials = new AuthorizedCredentials<>(bearer, tokenResponse.getExpires());
		return bearer;
	}

}
