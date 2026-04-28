/*
 * Copyright © 2026 Indiana University
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
package iu.oidc.client;

import java.net.http.HttpRequest.Builder;
import java.time.Instant;
import java.util.Map;

import edu.iu.IuIterable;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcTokenResponse;
import iu.oidc.client.config.IuOidcClientReference;

/**
 * Authenticates with refresh_token.
 */
public class RefreshTokenGrant extends OidcTokenGrant {

	private String refreshToken;

	/**
	 * Constructor.
	 *
	 * @param config        {@link IuOidcClientReference}
	 * @param tokenResponse {@link IuTokenResponse} that supplied the refresh token
	 * @param notAfter      expiration time
	 * @param refreshToken  refresh token from the last request
	 */
	public RefreshTokenGrant(IuOidcClientReference config, IuOidcTokenResponse tokenResponse, Instant notAfter) {
		super(config, tokenResponse, notAfter);
		this.refreshToken = tokenResponse.getRefreshToken();
	}

	@Override
	protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
		params.put("grant_type", IuIterable.iter("refresh_token"));
		params.put("refresh_token", IuIterable.iter(refreshToken));
		addClientAuth(requestBuilder, params);
	}

	@Override
	public WebToken validateTokenResponse(IuOidcTokenResponse response) {
		final var idToken = super.validateTokenResponse(response);
		refreshToken = response.getRefreshToken();
		return idToken;
	}

}
