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
import java.util.Map;
import java.util.Objects;

import edu.iu.IuIterable;
import iu.oidc.client.config.IuOidcClientReference;

/**
 * Authenticates using authorization_code, then refresh_token to renew after the
 * original response expires.
 * 
 * <p>
 * Verify state parameter match before using this class
 * </p>
 */
public class AuthorizationGrant extends OidcTokenGrant {

	private String code;

	/**
	 * Constructor.
	 * 
	 * @param config {@link IuOidcClientReference}
	 * @param code   authorization code received at the client's redirect URI
	 */
	public AuthorizationGrant(IuOidcClientReference config, String code) {
		super(config);
		this.code = code;
	}

	@Override
	protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
		final var code = Objects.requireNonNull(this.code, "already used");
		this.code = null;

		params.put("grant_type", IuIterable.iter("authorization_code"));
		params.put("code", IuIterable.iter(code));
		addClientAuth(requestBuilder, params);
	}

}
