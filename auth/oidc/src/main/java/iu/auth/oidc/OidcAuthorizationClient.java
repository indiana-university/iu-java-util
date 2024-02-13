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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IdGenerator;
import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuOutOfServiceException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuTokenResponse;
import edu.iu.auth.oidc.IuOpenIdClient;
import iu.auth.util.AccessTokenVerifier;
import jakarta.json.JsonObject;

/**
 * OpenID Connect {@link IuAuthorizationClient} implementation.
 */
class OidcAuthorizationClient implements IuAuthorizationClient {

	private static final Iterable<String> OIDC_SCOPE = IuIterable.iter("openid");

	private final String realm;
	private final URI authorizationEndpoint;
	private final URI tokenEndpoint;
	private final IuOpenIdClient client;
	private final AccessTokenVerifier idTokenVerifier;

	private final Set<String> nonces = new HashSet<>();

	/**
	 * Constructor.
	 * 
	 * @param config          parsed OIDC provider configuration
	 * @param client          client configuration metadata
	 * @param idTokenVerifier ID token verifier
	 */
	OidcAuthorizationClient(JsonObject config, IuOpenIdClient client, AccessTokenVerifier idTokenVerifier) {
		realm = config.getString("issuer");
		authorizationEndpoint = IuException.unchecked(() -> new URI(config.getString("authorization_endpoint")));
		tokenEndpoint = IuException.unchecked(() -> new URI(config.getString("token_endpoint")));
		this.client = client;
		this.idTokenVerifier = idTokenVerifier;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public URI getAuthorizationEndpoint() {
		return authorizationEndpoint;
	}

	@Override
	public URI getTokenEndpoint() {
		return tokenEndpoint;
	}

	@Override
	public URI getRedirectUri() {
		return client.getRedirectUri();
	}

	@Override
	public Map<String, String> getAuthorizationCodeAttributes() {
		final var nonce = IdGenerator.generateId();
		synchronized (nonces) {
			nonces.add(nonce);
		}
		return Map.of("nonce", nonce);
	}

	@Override
	public Map<String, String> getClientCredentialsAttributes() {
		final var resourceUri = this.getResourceUri().toString();
		return Map.of("resource", resourceUri, "audience", resourceUri);
	}

	@Override
	public IuApiCredentials getCredentials() {
		return client.getCredentials();
	}

	@Override
	public Duration getAuthenticationTimeout() {
		return client.getAuthenticationTimeout();
	}

	@Override
	public URI getResourceUri() {
		return client.getResourceUri();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Iterable<String> getScope() {
		final var scope = client.getScope();
		if (scope == null)
			return OIDC_SCOPE;
		else
			return (Iterable<String>) IuIterable.cat(OIDC_SCOPE, IuIterable.filter(scope, a -> !"openid".equals(a)));
	}

	@Override
	public Subject verify(IuTokenResponse tokenResponse) throws IuAuthenticationException, IuBadRequestException,
			IuAuthorizationFailedException, IuOutOfServiceException, IllegalStateException {

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Subject verify(IuTokenResponse refreshTokenResponse, IuTokenResponse originalTokenResponse)
			throws IuAuthenticationException, IuBadRequestException, IuAuthorizationFailedException,
			IuOutOfServiceException, IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void activate(IuApiCredentials credentials) throws IuAuthenticationException, IuBadRequestException,
			IuAuthorizationFailedException, IuOutOfServiceException, IllegalStateException {
		// TODO Auto-generated method stub

	}

}
