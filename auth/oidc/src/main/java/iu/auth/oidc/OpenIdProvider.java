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
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import edu.iu.auth.oidc.IuOpenIdAuthenticationAttributes;
import edu.iu.auth.oidc.IuOpenIdProvider;
import iu.auth.util.AccessTokenVerifier;
import iu.auth.util.HttpUtils;
import jakarta.json.JsonObject;

/**
 * {@link IuOpenIdProvider} implementation.
 */
public class OpenIdProvider implements IuOpenIdProvider {

	private final Logger LOG = Logger.getLogger(OpenIdProvider.class.getName());

	private final String issuer;
	private final Duration authenticationTimeout;
	private final AccessTokenVerifier accessTokenVerifier;
	private JsonObject config;

	/**
	 * Constructor.
	 * 
	 * @param configUri             Well-known configuration URI
	 * @param trustRefreshInterval  Maximum length of time to keep trusted signing
	 *                              keys in cache
	 * @param authenticationTimeout Maximum length of time to allow for user
	 *                              authentication.
	 */
	public OpenIdProvider(URI configUri, Duration trustRefreshInterval, Duration authenticationTimeout) {
		config = HttpUtils.read(configUri).asJsonObject();
		LOG.info("OIDC Provider configuration:\n" + config.toString());

		this.issuer = config.getString("issuer");

		this.accessTokenVerifier = new AccessTokenVerifier(
				IuException.unchecked(() -> new URI(config.getString("jwks_uri"))), issuer, trustRefreshInterval);

		this.authenticationTimeout = authenticationTimeout;
	}

	@Override
	public IuAuthorizationClient createAuthorizationClient(URI resourceUri, IuApiCredentials clientCredentials) {
		return new OidcAuthorizationClient(config, resourceUri, clientCredentials);
	}

	@Override
	public String getIssuer() {
		return issuer;
	}

	@Override
	public URI getUserInfoEndpoint() {
		return IuException.unchecked(() -> new URI(config.getString("userinfo_endpoint")));
	}

	@Override
	public IuOpenIdAuthenticationAttributes verifyAuthentication(IuAuthorizationResponse authResponse) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
