/*
 * Copyright © 2025 Indiana University
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
package iu.auth.config;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import edu.iu.IuWebUtils;
import edu.iu.auth.oauth.OAuthAuthorizationClient;
import edu.iu.crypt.WebToken;

/**
 * Authenticates to an OAuth 2.0 Token endpoint, verifies and holds a JWT access
 * token until expired.
 */
public class OidcAuthorizationGrant extends OAuthAccessTokenGrant {

	/**
	 * Constructor.
	 * 
	 * @param credentialSupplier Supplies client credentials
	 */
	public OidcAuthorizationGrant(Supplier<OAuthAuthorizationClient> credentialSupplier) {
		super(credentialSupplier);
	}

	@Override
	protected void verifyToken(WebToken jwt) {
		// validity of non-null nbf and exp are handled by super -> WebToken.verify
		Objects.requireNonNull(jwt.getNotBefore(), "nbf");
		Objects.requireNonNull(jwt.getExpires(), "exp");
	}

	@Override
	protected OAuthAuthorizationClient getClient() {
		return (OAuthAuthorizationClient) super.getClient();
	}

	@Override
	protected void tokenAuth(HttpRequest.Builder requestBuilder) {
		final var client = getClient();
		requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
//		requestBuilder.header("Authorization", "Basic " + IuText
//				.base64(IuText.utf8(client.getClientId() + ":" + client.getClientSecret())));
		requestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(Map.of( //
				"grant_type", List.of("authorization_code"), //
				"redirect_uri", List.of(client.getRedirectUri().toString())
		// , //
//				"code", List.of(code) //
		))));
	}

}
