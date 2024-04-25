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
import java.util.Map;

import edu.iu.IuException;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oidc.IuOpenIdClient;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import iu.auth.util.AccessTokenVerifier;
import jakarta.json.JsonObject;

/**
 * {@link IuOpenIdProvider} implementation.
 */
@SuppressWarnings("deprecation") // TODO: iu-java-auth-jwt
public class OpenIdProvider implements IuOpenIdProvider {

	private final IuOpenIdClient client;
	private JsonObject config;
	private AccessTokenVerifier idTokenVerifier;

	/**
	 * Constructor.
	 * 
	 * @param configUri provider configuration URI
	 * @param client    client configuration metadata
	 */
	public OpenIdProvider(IuOpenIdClient client) {
		this.client = client;
	}

	@Override
	public OidcPrincipal hydrate(String accessToken) throws IuAuthenticationException {
		return new OidcPrincipal(null, accessToken);
	}

	/**
	 * Gets the client configuration.
	 * 
	 * @return client configuration
	 */
	IuOpenIdClient client() {
		return client;
	}

	/**
	 * Gets the OpenID Provider configuration, parsed as a JSON object.
	 * 
	 * @return {@link JsonObject} OP configuration
	 */
	JsonObject config() {
		if (config == null)
			config = IuException.unchecked(() -> IuHttp.get(client.getProviderConfigUri(), IuHttp.READ_JSON_OBJECT));
		return config;
	}

	/**
	 * Gets claims from the OP userinfo endpoint.
	 * 
	 * @param accessToken OIDC access token
	 * @return {@link JsonObject} parsed userinfo claims
	 */
	Map<String, ?> userinfo(String accessToken) {
		return IuJsonAdapter.<Map<String, ?>>basic()
				.fromJson(IuException.unchecked(
						() -> IuHttp.send(IuJson.get(config(), "userinfo_endpoint", IuJsonAdapter.of(URI.class)),
								b -> b.header("Authorization", "Bearer " + accessToken), IuHttp.READ_JSON_OBJECT)));
	}

	/**
	 * Gets a JWT validator for verifying an ID token.
	 * 
	 * @return ID token verifier
	 */
	AccessTokenVerifier idTokenVerifier() {
		if (idTokenVerifier == null)
			idTokenVerifier = new AccessTokenVerifier(
					IuException.unchecked(() -> new URI(config.getString("jwks_uri"))), config().getString("issuer"),
					() -> Duration.ofHours(2L));
		return idTokenVerifier;
	}

}
