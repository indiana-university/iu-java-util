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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Supplier;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.oauth.OAuthClient;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebToken;

/**
 * Authenticates to an OAuth 2.0 Token endpoint, verifies and holds a JWT access
 * token until expired.
 */
public abstract class OAuthAccessTokenGrant {

	private final Supplier<? extends OAuthClient> client;

	private String accessToken;
	private Instant notAfter;

	/**
	 * Constructor.
	 * 
	 * @param client Configured {@link OAuthClient}
	 */
	public OAuthAccessTokenGrant(Supplier<? extends OAuthClient> client) {
		this.client = client;
	}

	/**
	 * Prepares an {@link HttpRequest.Builder} for the token endpoint.
	 * 
	 * @param requestBuilder {@link HttpRequest.Builder}
	 */
	protected abstract void tokenAuth(HttpRequest.Builder requestBuilder);

	/**
	 * Performs post-verification of validated JWT claims.
	 * 
	 * @param jwt parsed JWT
	 */
	protected abstract void verifyToken(WebToken jwt);

	/**
	 * Validates a JWT access token.
	 * 
	 * @param accessToken JWT compact serialization
	 * @return Parsed and validated {@link WebToken}
	 */
	protected WebToken validateJwt(String accessToken) {
		return WebToken.verify(accessToken,
				IuObject.convert(getClient().getJwksUri(), WebKey::readJwks).iterator().next());
	}

	/**
	 * Gets the configured {@link OAuthClient}
	 * 	
	 * @return Configured {@link OAuthClient}
	 */
	protected OAuthClient getClient() {
		return client.get();
	}

	/**
	 * Gets the access token, completing OAuth 2.0 token interactions as needed.
	 * 
	 * @return access token
	 */
	public String getAccessToken() {
		if (accessToken == null //
				|| Instant.now().isAfter(notAfter)) {
			final var clientCredentials = getClient();
			final var tokenUri = clientCredentials.getTokenUri();

			final var tokenResponse = IuException
					.unchecked(() -> IuHttp.send(tokenUri, this::tokenAuth, IuHttp.READ_JSON_OBJECT));

			final String accessToken = IuJson.get(tokenResponse, "access_token");
			if (getClient().getJwksUri() != null)
				verifyToken(validateJwt(accessToken));

			final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
			notAfter = IuObject.require(
					now.plusSeconds(Objects.requireNonNull(
							IuJson.get(tokenResponse, "expires_in", IuJsonAdapter.of(Integer.class)), "expires_in")),
					now::isBefore, "non-positive expires_in");
			this.accessToken = accessToken;
		}
		return accessToken;
	}

}
