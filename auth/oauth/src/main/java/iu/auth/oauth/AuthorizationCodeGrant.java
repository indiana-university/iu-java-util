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

import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.client.IuHttp;
import iu.auth.util.HttpUtils;

/**
 * {@link IuAuthorizationGrant} implementation.
 */
final class AuthorizationCodeGrant extends AbstractGrant {
	private static final long serialVersionUID = 1L;

	private final Logger LOG = Logger.getLogger(AuthorizationCodeGrant.class.getName());

	private static class AuthorizationState {
		private final String state;
		private final URI resourceUri;
		private final Map<String, String> requestAttributes;

		private AuthorizationState(String state, URI resourceUri, Map<String, String> requestAttributes) {
			this.state = state;
			this.resourceUri = resourceUri;
			this.requestAttributes = requestAttributes;
		}
	}

	private final URI resourceUri;
	private final Queue<AuthorizationState> pendingAuthorizationState = new ArrayDeque<>();
	private TokenResponse originalResponse;
	private String refreshToken;

	/**
	 * Constructor.
	 * 
	 * @param realm       authentication realm
	 * @param resourceUri root resource URI for this grant
	 */
	AuthorizationCodeGrant(String realm, URI resourceUri) {
		super(realm);
		if (!IuWebUtils.isRootOf(OAuthSpi.getClient(realm).getResourceUri(), resourceUri))
			throw new IllegalArgumentException("Invalid resource URI for this client");
		else
			this.resourceUri = resourceUri;
	}

	@Override
	public IuApiCredentials authorize(URI resourceUri) throws IuAuthenticationException {
		if (!IuWebUtils.isRootOf(this.resourceUri, resourceUri))
			throw new IllegalArgumentException("Invalid resource URI for this grant");

		final var activatedCredentials = activate();
		if (activatedCredentials != null)
			return activatedCredentials;

		final var client = OAuthSpi.getClient(realm);
		Throwable refreshFailure = null;
		if (isExpired()) {
			if (refreshToken != null)
				try {

					final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
					tokenRequestParams.put("grant_type", List.of("refresh_token"));
					tokenRequestParams.put("refresh_token", List.of(refreshToken));
					tokenRequestParams.put("scope", List.of(validatedScope));

					final var tokenResponse = new TokenResponse(client.getScope(), null,
							IuHttp.send(client.getTokenEndpoint(), tokenRequestBuilder -> {
								tokenRequestBuilder.POST(
										BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
								tokenRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
								client.getCredentials().applyTo(tokenRequestBuilder);
							}, IuHttp.READ_JSON_OBJECT));

					final var refreshToken = tokenResponse.getRefreshToken();
					if (refreshToken != null)
						this.refreshToken = refreshToken;

					final var credentials = verify(tokenResponse, originalResponse);
					client.activate(credentials);
					return credentials;

				} catch (Throwable e) {
					LOG.log(Level.INFO, e, () -> "Refresh token failed");
					refreshFailure = e;
				}

			LOG.fine("Authorized session has expired, initiating authorization code flow for " + realm);
		} else
			LOG.fine(() -> "Authorization required, initiating authorization code flow for " + realm);

		final Map<String, String> challengeAttributes = new LinkedHashMap<>();
		challengeAttributes.put("realm", realm);
		if (isExpired()) {
			challengeAttributes.put("error", "invalid_token");
			if (refreshFailure == null)
				challengeAttributes.put("error_description", "expired access token");
			else
				challengeAttributes.put("error_description", "expired access token, refresh attempt failed");
		}
		if (validatedScope != null)
			challengeAttributes.put("scope", validatedScope);

		final var state = IdGenerator.generateId();
		challengeAttributes.put("state", state);

		final var authRequestParams = new LinkedHashMap<String, Iterable<String>>();
		authRequestParams.put("client_id", List.of(client.getCredentials().getName()));
		authRequestParams.put("response_type", List.of("code"));
		authRequestParams.put("redirect_uri", List.of(client.getRedirectUri().toString()));
		if (validatedScope != null)
			authRequestParams.put("scope", List.of(validatedScope));
		authRequestParams.put("state", List.of(state));

		final var requestAttributes = client.getAuthorizationCodeAttributes();
		if (requestAttributes != null)
			for (final var clientAttributeEntry : requestAttributes.entrySet()) {
				final var name = clientAttributeEntry.getKey();
				if (authRequestParams.containsKey(name))
					throw new IllegalArgumentException("Illegal attempt to override standard auth attribute " + name);
				else {
					final var value = clientAttributeEntry.getValue();
					challengeAttributes.put(name, value);
					authRequestParams.put(name, List.of(value));
				}
			}

		synchronized (pendingAuthorizationState) {
			pendingAuthorizationState.offer(new AuthorizationState(state, resourceUri, requestAttributes));
		}

		final var challenge = new IuAuthenticationException( //
				HttpUtils.createChallenge("Bearer", challengeAttributes), refreshFailure);
		challenge.setLocation(IuException.unchecked(() -> new URI(
				client.getAuthorizationEndpoint() + "?" + IuWebUtils.createQueryString(authRequestParams))));
		throw challenge;
	}

	/**
	 * Attempts to complete pending authorization.
	 * 
	 * @param code  authorization code
	 * @param state state
	 * @return resource URI from the {@link #authorize(URI)} invocation that
	 *         initiated authorization code, if authorization was completed
	 *         successfully for this grant; null if the state value is invalid for
	 *         or has already been used by, this grant.
	 * @throws IuAuthenticationException if state matches a pending authorization
	 *                                   for this grant, but is no longer valid,
	 *                                   i.e., because the authentication timeout
	 *                                   interval has passed so authorization has
	 *                                   not been completed. In this case, the
	 *                                   resource URI is provided via
	 *                                   {@link IuAuthenticationException#getLocation()}.
	 *                                   In this case, the application is expected
	 *                                   to start the authorization process over
	 *                                   again.
	 */
	URI authorize(String code, String state) throws IuAuthenticationException {
		final var client = OAuthSpi.getClient(realm);

		final var authorizationState = getAuthorizationState(state);
		if (authorizationState == null)
			return null;

		final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
		tokenRequestParams.put("grant_type", List.of("authorization_code"));
		tokenRequestParams.put("code", List.of(code));
		tokenRequestParams.put("scope", List.of(validatedScope));
		tokenRequestParams.put("redirect_uri", List.of(client.getRedirectUri().toString().toString()));

		final var authResponse = IuException
				.unchecked(() -> IuHttp.send(client.getTokenEndpoint(), authRequestBuilder -> {
					authRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
					authRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
					client.getCredentials().applyTo(authRequestBuilder);
				}, IuHttp.READ_JSON_OBJECT));

		final var codeResponse = new TokenResponse(client.getScope(), authorizationState.requestAttributes,
				authResponse);
		verify(codeResponse);
		originalResponse = codeResponse;
		refreshToken = codeResponse.getRefreshToken();
		return authorizationState.resourceUri;
	}

	private AuthorizationState getAuthorizationState(String state) throws IuAuthenticationException {
		final var client = OAuthSpi.getClient(realm);

		AuthorizationState matchingAuthorizationState = null;
		synchronized (pendingAuthorizationState) {
			final var i = pendingAuthorizationState.iterator();
			while (i.hasNext()) {
				final var pendingAuthorizationState = i.next();
				if (pendingAuthorizationState.state.equals(state)) {
					matchingAuthorizationState = pendingAuthorizationState;
					i.remove();
					break;
				} else
					try {
						IdGenerator.verifyId(pendingAuthorizationState.state,
								client.getAuthenticationTimeout().toMillis());
					} catch (IllegalArgumentException e) {
						LOG.log(Level.FINER, e, () -> "Pruning invalid/expired state " + pendingAuthorizationState);
						i.remove();
					}
			}
		}

		if (matchingAuthorizationState == null)
			return null;

		try {
			IdGenerator.verifyId(matchingAuthorizationState.state, client.getAuthenticationTimeout().toMillis());
			return matchingAuthorizationState;
		} catch (IllegalArgumentException e) {
			final Map<String, String> challengeAttributes = new LinkedHashMap<>();
			challengeAttributes.put("realm", client.getRealm());
			challengeAttributes.put("error", "invalid_request");
			challengeAttributes.put("error_description", "invalid or expired state");

			final var challenge = new IuAuthenticationException(
					HttpUtils.createChallenge("Bearer", challengeAttributes));
			challenge.setLocation(matchingAuthorizationState.resourceUri);
			throw challenge;
		}
	}

}
