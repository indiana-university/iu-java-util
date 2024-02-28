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

import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.auth.oauth.IuTokenResponse;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * {@link IuTokenResponse} implementation.
 */
class TokenResponse implements IuTokenResponse, Serializable {
	private static final long serialVersionUID = 1L;

	private final String tokenType;
	private final String accessToken;
	private final String refreshToken;
	private final Iterable<String> scope;
	private final Instant expires;
	private final Map<String, String> requestAttributes;
	private final Map<String, String> tokenAttributes;

	/**
	 * Constructor.
	 * 
	 * @param requestedScope    requested scope
	 * @param requestAttributes additional attributes send with the initial request
	 *                          to the authorization server
	 * @param tokenResponse     parsed token response
	 */
	TokenResponse(Iterable<String> requestedScope, Map<String, String> requestAttributes, JsonObject tokenResponse) {
		// https://datatracker.ietf.org/doc/html/rfc6749#section-5.2
		if (tokenResponse.containsKey("error"))
			throw new IllegalStateException("Error in token response; " + tokenResponse);

		// https://datatracker.ietf.org/doc/html/rfc6749#section-5.1
		if (!tokenResponse.containsKey("token_type"))
			throw new IllegalStateException("Token response missing token_type; " + tokenResponse);
		else
			tokenType = tokenResponse.getString("token_type");

		if (!tokenResponse.containsKey("access_token"))
			throw new IllegalStateException("Token response missing access_token; " + tokenResponse);
		else
			accessToken = tokenResponse.getString("access_token");

		final var expiresIn = tokenResponse.get("expires_in");
		if (expiresIn instanceof JsonString) {
			final var a = (JsonString) expiresIn;
			expires = Instant.now().plusSeconds(Long.parseLong(a.getString()));
		} else if (expiresIn instanceof JsonNumber) {
			final var a = (JsonNumber) expiresIn;
			expires = Instant.now().plusSeconds(a.intValue());
		} else
			expires = null;

		if (!tokenResponse.containsKey("refresh_token"))
			refreshToken = null;
		else
			refreshToken = tokenResponse.getString("refresh_token");

		// https://datatracker.ietf.org/doc/html/rfc6749#section-3.3
		if (!tokenResponse.containsKey("scope"))
			this.scope = requestedScope;
		else
			this.scope = Arrays.asList(tokenResponse.getString("scope").split(" "));

		this.requestAttributes = requestAttributes;

		final Map<String, String> tokenAttributes = new LinkedHashMap<>();
		for (final var tokenEntry : tokenResponse.entrySet()) {
			final var key = tokenEntry.getKey();
			if (!key.equals("token_type") //
					&& !key.equals("access_token") //
					&& !key.equals("expires_in") //
					&& !key.equals("refresh_token") //
					&& !key.equals("scope")) {
				final var value = tokenEntry.getValue();
				if (value instanceof JsonString) {
					final var s = (JsonString) value;
					tokenAttributes.put(key, s.getString());
				} else
					tokenAttributes.put(key, value.toString());
			}
		}
		this.tokenAttributes = Collections.unmodifiableMap(tokenAttributes);
	}

	@Override
	public String getTokenType() {
		return tokenType;
	}

	@Override
	public String getAccessToken() {
		return accessToken;
	}

	@Override
	public Iterable<String> getScope() {
		return scope;
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	@Override
	public Map<String, ?> getRequestAttributes() {
		return requestAttributes;
	}

	@Override
	public Map<String, ?> getTokenAttributes() {
		return tokenAttributes;
	}

	/**
	 * Gets the refresh token, if provided by the token endpoint.
	 * 
	 * @return refresh token; null if not provided
	 */
	String getRefreshToken() {
		return refreshToken;
	}

	/**
	 * Determines if this grant has expired.
	 * 
	 * @return true if expired; else false
	 */
	boolean isExpired() {
		return expires != null && expires.isBefore(Instant.now());
	}

}
