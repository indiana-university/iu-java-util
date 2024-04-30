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
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizedPrincipal;
import edu.iu.auth.oauth.IuBearerToken;
import edu.iu.auth.oauth.IuTokenResponse;
import edu.iu.client.HttpResponseHandler;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import jakarta.json.JsonObject;

/**
 * Represents an OAuth client credentials grant..
 */
abstract class AbstractGrant implements IuAuthorizationGrant, Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Expects a 200 OK response with a valid JSON object.
	 * 
	 * <p>
	 * {@code Cache-Control: no-store} and {@code Pragma: cache} <em>must</em> be
	 * included in the response headers.
	 * </p>
	 */
	static final HttpResponseHandler<JsonObject> JSON_OBJECT_NOCACHE = IuHttp
			.validate(a -> IuJson.parse(a).asJsonObject(), IuHttp.OK, response -> {
				if (response.request().headers().firstValue("Authorization").isPresent() //
						&& !(response.headers().firstValue("Pragma").orElse("").equals("no-cache") //
								&& response.headers().firstValue("Cache-Control").orElse("").equals("no-store")))
					throw new IllegalStateException("Expected response to include Cache-Control = no-store header");
			});

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
			sb.append(scopeToken);
		});

		if (sb.length() == 0)
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

	private BearerToken authorizedCredentials;

	/**
	 * Constructor.
	 * 
	 * @param realm authentication realm
	 */
	AbstractGrant(String realm) {
		this.realm = realm;
		this.validatedScope = validateScope(OAuthSpi.getClient(realm).getScope());
	}

	/**
	 * Gets previously established API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	protected BearerToken getAuthorizedCredentials() {
		if (authorizedCredentials == null //
				|| authorizedCredentials.expired())
			return null;
		return authorizedCredentials;
	}

	/**
	 * Verifies a token response.
	 * 
	 * @param tokenResponse token response
	 * @return verified credentials, will be held internally until the expiration
	 *         time implied by the token response has passed.
	 * @throws IuAuthenticationException if the token response cannot be verified
	 */
	protected final IuApiCredentials authorize(IuTokenResponse tokenResponse) throws IuAuthenticationException {
		if (!"Bearer".equals(tokenResponse.getTokenType()))
			throw new IllegalStateException("Unsupported token type " + tokenResponse.getTokenType() + " in response");

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
	protected final IuApiCredentials authorize(IuTokenResponse refreshTokenResponse,
			IuTokenResponse originalTokenResponse) throws IuAuthenticationException {
		if (!"Bearer".equals(refreshTokenResponse.getTokenType()))
			throw new IllegalArgumentException("Unsupported token type");

		return authorizeBearer(OAuthSpi.getClient(realm).verify(refreshTokenResponse, originalTokenResponse),
				refreshTokenResponse);
	}

	/**
	 * Determines if the authorized credentials are expired.
	 * 
	 * @return true if authorized credentials were established and have expired;
	 *         else false
	 */
	protected boolean isExpired() {
		return authorizedCredentials != null && authorizedCredentials.expired();
	}

	private IuBearerToken authorizeBearer(IuAuthorizedPrincipal authPrincipal, IuTokenResponse tokenResponse)
			throws IuAuthenticationException {

		final Set<String> scope = new LinkedHashSet<>();
		tokenResponse.getScope().forEach(scope::add);

		final String authorizedRealm;
		final IuPrincipalIdentity principal;
		if (authPrincipal == null) {
			authorizedRealm = realm;
			principal = null;
		} else {
			authorizedRealm = authPrincipal.getRealm();
			principal = authPrincipal.getPrincipal();
			final var subject = principal.getSubject();
			subject.setReadOnly();
			for (final var p : subject.getPrincipals())
				if (!(p instanceof Serializable))
					throw new IllegalArgumentException("Principal must be serializable",
							new NotSerializableException(principal.getClass().getName()));
		}

		final var client = OAuthSpi.getClient(realm);
		var matched = false;
		for (final var principalRealm : client.getPrincipalRealms())
			if (principalRealm.equals(authorizedRealm)) {
				matched = true;
				break;
			}
		if (!matched)
			throw new IllegalArgumentException("Invalid principal realm");

		final var authTtl = client.getAuthorizationTimeToLive();
		var expires = tokenResponse.getExpiresIn();
		if (expires == null //
				|| expires.compareTo(Duration.ZERO) <= 0 //
				|| expires.compareTo(authTtl) > 0)
			expires = authTtl;

		final var bearer = new BearerToken(authorizedRealm, principal, scope, tokenResponse.getAccessToken(),
				Instant.now().plus(expires));

		IuPrincipalIdentity.verify(bearer, this.realm);

		return authorizedCredentials = bearer;
	}

}
