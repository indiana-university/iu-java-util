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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import jakarta.json.JsonObject;

/**
 * OpenID Connect {@link IuAuthorizationClient} implementation.
 */
class OidcAuthorizationClient implements IuAuthorizationClient {

	private final String realm;
	private final URI authorizationEndpoint;
	private final URI tokenEndpoint;
	private final URI resourceUri;
	private final IuApiCredentials credentials;

	private final Set<String> nonces = new HashSet<>();

	/**
	 * Constructor.
	 * 
	 * @param config                parsed OIDC provider well-known configuration
	 * @param authenticationTimeout Max length of time to allow between initiating
	 *                              authentication (e.g., redirect to OIDC
	 *                              authorization endpoint) and completing
	 *                              authentication (i.e., ID token issued at "iat"
	 *                              claim value).
	 * @param resourceUri           client resource URI
	 * @param credentials           client credentials
	 */
	OidcAuthorizationClient(JsonObject config, URI resourceUri, IuApiCredentials credentials) {
		realm = config.getString("issuer");
		authorizationEndpoint = IuException.unchecked(() -> new URI(config.getString("authorization_endpoint")));
		tokenEndpoint = IuException.unchecked(() -> new URI(config.getString("token_endpoint")));
		this.resourceUri = resourceUri;
		this.credentials = credentials;
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
		return resourceUri;
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
		final var resourceUri = this.resourceUri.toString();
		return Map.of("resource", resourceUri, "audience", resourceUri);
	}

	@Override
	public IuApiCredentials getCredentials() {
		return credentials;
	}

}
