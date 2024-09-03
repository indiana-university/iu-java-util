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
package iu.auth.client;

import java.io.Serializable;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import edu.iu.IuIterable;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuBearerToken;
import edu.iu.auth.config.IuAuthorizationResource;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.client.HttpResponseHandler;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import iu.auth.config.AuthConfig;
import jakarta.json.JsonObject;

/**
 * Abstract base class for {@link IuAuthorizationGrant}
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

	/** Client ID */
	protected final String clientId;;

	/** API endpoint {@link URI} */
	protected final URI endpointUri;

	/** Requested authorization scope(s) */
	protected final Set<String> scope;

	private BearerToken authorizedCredentials;

	/**
	 * Constructor.
	 * 
	 * @param clientId    Client ID
	 * @param endpointUri API endpoint {@link URI}, MUST be relative to at least on
	 *                    {@link IuAuthorizationResource#getEndpointUris()} entry
	 * @param scope       OPTIONAL requested scope, MUST be a subset of
	 *                    {@link IuAuthorizationResource#getScope()}
	 */
	AbstractGrant(String clientId, URI endpointUri, String... scope) {
		final var resource = AuthConfig.load(IuAuthorizationResource.class, clientId);
		this.clientId = clientId;

		var urlMatch = false;
		for (final var uri : Objects.requireNonNull(resource.getEndpointUris(), "Missing endpointUris"))
			if (IuWebUtils.isRootOf(uri, endpointUri)) {
				urlMatch = true;
				break;
			}
		if (!urlMatch)
			throw new IllegalArgumentException("Endpoint URI not allowed by " + clientId + " resource configuration");
		this.endpointUri = endpointUri;

		final var allowedScope = Objects.requireNonNull(resource.getScope(),
				"Missing scope in " + clientId + " resource configuration");
		final var allowedScopeSet = IuIterable.stream(allowedScope).collect(Collectors.toUnmodifiableSet());
		if (scope == null)
			this.scope = allowedScopeSet;
		else {
			final Set<String> requestedScopes = new LinkedHashSet<>();
			for (final var requestedScope : scope)
				if (!allowedScopeSet.contains(requestedScope))
					requestedScopes.add(requestedScope);
				else
					throw new IllegalArgumentException("Scope not allowed by " + clientId + " resource configuration");
			this.scope = Collections.unmodifiableSet(requestedScopes);
		}
	}

	/**
	 * Gets previously established API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	protected BearerToken getAuthorizedCredentials() {
		if (authorizedCredentials == null //
				|| authorizedCredentials.getToken().isExpired())
			return null;
		return authorizedCredentials;
	}

//	/**
//	 * Verifies a token response.
//	 * 
//	 * @param tokenResponse token response
//	 * @return verified credentials, will be held internally until the expiration
//	 *         time implied by the token response has passed.
//	 * @throws IuAuthenticationException if the token response cannot be verified
//	 */
//	protected final IuApiCredentials authorize(IuTokenResponse tokenResponse) throws IuAuthenticationException {
//		if (!"Bearer".equals(tokenResponse.getTokenType()))
//			throw new IllegalStateException("Unsupported token type " + tokenResponse.getTokenType() + " in response");
//
//		return authorizeBearer(OAuthSpi.getClient(realm).verify(tokenResponse), tokenResponse);
//	}
//
//	/**
//	 * Verifies a refresh token response.
//	 * 
//	 * @param refreshTokenResponse  refresh token response
//	 * @param originalTokenResponse previously {@link #authorize(IuTokenResponse)
//	 *                              authorized} token response
//	 * @return verified credentials, will be held internally until the expiration
//	 *         time implied by the token response has passed.
//	 * @throws IuAuthenticationException if the token response cannot be verified
//	 */
//	protected final IuApiCredentials authorize(IuTokenResponse refreshTokenResponse,
//			IuTokenResponse originalTokenResponse) throws IuAuthenticationException {
//		if (!"Bearer".equals(refreshTokenResponse.getTokenType()))
//			throw new IllegalArgumentException("Unsupported token type");
//
//		return authorizeBearer(OAuthSpi.getClient(realm).verify(refreshTokenResponse, originalTokenResponse),
//				refreshTokenResponse);
//	}
//
//	/**
//	 * Determines if the authorized credentials are expired.
//	 * 
//	 * @return true if authorized credentials were established and have expired;
//	 *         else false
//	 */
//	protected boolean isExpired() {
//		return authorizedCredentials != null && authorizedCredentials.expired();
//	}
//
//	private IuBearerToken authorizeBearer(IuTokenResponse tokenResponse)
//			throws IuAuthenticationException {
//		throw new UnsupportedOperatiopm
//
//		final var idToken = tokenResponse.getIdToken();
//		final Principal principal;
//		if (idToken != null)
//			principal = new OpenIdPrincipal(clientId, idToken);
//		
//		final var accessToken = tokenResponse.getAccessToken();
		
		
//		final String authorizedRealm;
//		final IuPrincipalIdentity principal;
//		if (authPrincipal == null) {
//			authorizedRealm = realm;
//			principal = null;
//		} else {
//			authorizedRealm = authPrincipal.getRealm();
//			principal = authPrincipal.getPrincipal();
//			final var subject = principal.getSubject();
//			subject.setReadOnly();
//			for (final var p : subject.getPrincipals())
//				if (!(p instanceof Serializable))
//					throw new IllegalArgumentException("Principal must be serializable",
//							new NotSerializableException(principal.getClass().getName()));
//		}
//
//		final var client = OAuthSpi.getClient(realm);
//		var matched = false;
//		for (final var principalRealm : client.getPrincipalRealms())
//			if (principalRealm.equals(authorizedRealm)) {
//				matched = true;
//				break;
//			}
//		if (!matched)
//			throw new IllegalArgumentException("Invalid principal realm");
//
//		final var authTtl = client.getAuthorizationTimeToLive();
//		var expires = tokenResponse.getExpiresIn();
//		if (expires == null //
//				|| expires.compareTo(Duration.ZERO) <= 0 //
//				|| expires.compareTo(authTtl) > 0)
//			expires = authTtl;
//
//		final var bearer = new BearerToken(authorizedRealm, principal, scope, tokenResponse.getAccessToken(),
//				Instant.now().plus(expires));
//
//		IuPrincipalIdentity.verify(bearer, this.realm);
//
//		return authorizedCredentials = bearer;
//	}

}