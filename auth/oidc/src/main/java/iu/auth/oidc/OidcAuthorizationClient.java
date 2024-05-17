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
package iu.auth.oidc;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuIterable;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import iu.auth.oauth.IuAuthorizationClient;
import iu.auth.oauth.IuAuthorizedPrincipal;
import iu.auth.oauth.IuTokenResponse;

/**
 * OpenID Connect {@link IuAuthorizationClient} implementation for authoritative
 * clients.
 */
class OidcAuthorizationClient implements IuAuthorizationClient {

	private static final Iterable<String> OIDC_SCOPE = IuIterable.iter("openid");

	private final OpenIdProvider provider;

	/**
	 * Constructor.
	 * 
	 * @param provider OIDC provider
	 */
	OidcAuthorizationClient(OpenIdProvider provider) {
		this.provider = provider;
	}

	@Override
	public String getRealm() {
		return getRedirectUri().toString();
	}

	@Override
	public Iterable<String> getPrincipalRealms() {
		return IuIterable.iter(provider.client().getRealm());
	}

	@Override
	public URI getResourceUri() {
		return provider.client().getResourceUri();
	}

	@Override
	public IuApiCredentials getCredentials() {
		return provider.authClient().getCredentials();
	}

	@Override
	public URI getRedirectUri() {
		return provider.authClient().getRedirectUri();
	}

	@Override
	public Iterable<String> getScope() {
		final var scope = provider.authClient().getScope();
		if (scope == null)
			return OIDC_SCOPE;
		else
			return (Iterable<String>) IuIterable.cat(OIDC_SCOPE, IuIterable.filter(scope, a -> !a.equals("openid")));
	}

	@Override
	public Duration getAuthenticationTimeout() {
		return provider.authClient().getAuthenticationTimeout();
	}

	@Override
	public Duration getAuthorizationTimeToLive() {
		return provider.authClient().getAuthenticatedSessionTimeout();
	}

	@Override
	public Map<String, String> getAuthorizationCodeAttributes() {
		final var client = provider.authClient();

		final Map<String, String> attributes = new LinkedHashMap<>();

		final var clientAttributes = provider.authClient().getAuthorizationCodeAttributes();
		if (clientAttributes != null)
			attributes.putAll(clientAttributes);

		attributes.put("nonce", provider.authClient().createNonce());

		final var authenticatedSessionTimeout = client.getAuthenticatedSessionTimeout();
		if (authenticatedSessionTimeout != null)
			attributes.put("max_age", Long.toString(authenticatedSessionTimeout.toSeconds()));

		return Collections.unmodifiableMap(attributes);
	}

	@Override
	public Map<String, String> getClientCredentialsAttributes() {
		return provider.authClient().getClientCredentialsAttributes();
	}

	@Override
	public URI getTokenEndpoint() {
		return IuJson.get(provider.config(), "token_endpoint", IuJsonAdapter.of(URI.class));
	}

	@Override
	public URI getAuthorizationEndpoint() {
		return IuJson.get(provider.config(), "authorization_endpoint", IuJsonAdapter.of(URI.class));
	}

	@Override
	public IuAuthorizedPrincipal verify(IuTokenResponse tokenResponse) {
		if (!IuIterable.filter(tokenResponse.getScope(), "openid"::equals).iterator().hasNext())
			throw new IllegalArgumentException("missing openid scope");

		final var accessToken = Objects.requireNonNull(tokenResponse.getAccessToken(), "access_token");
		final var idToken = (String) tokenResponse.getTokenAttributes().get("id_token");
		final var principal = new OidcPrincipal(idToken, accessToken, provider);

		return new IuAuthorizedPrincipal() {
			@Override
			public String getRealm() {
				return provider.client().getRealm();
			}

			@Override
			public IuPrincipalIdentity getPrincipal() {
				return principal;
			}
		};
	}

	@Override
	public IuAuthorizedPrincipal verify(IuTokenResponse refreshTokenResponse, IuTokenResponse originalTokenResponse) {
		// TODO establish and verify refresh token integration test
		throw new UnsupportedOperationException("TODO");
	}

}
