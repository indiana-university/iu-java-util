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
package iu.esas.thirdparty.auth.oidc.client;

import java.net.URI;

import edu.iu.IuObject;
import edu.iu.auth.session.IuSession;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import iu.auth.config.OAuthTokenResponse;
import iu.crypt.JwtBuilder;

/**
 * Extends {@link JwtBuilder} to add claims values for use with
 * {@link IuSession}.
 */
class TokenResponseJwtBuilder extends JwtBuilder<TokenResponseJwtBuilder> {
	static {
		IuObject.assertNotOpen(TokenResponseJwtBuilder.class);
	}

	/**
	 * Default constructor.
	 */
	TokenResponseJwtBuilder() {
	}

	/**
	 * Sets the client ID.
	 * 
	 * @param clientId client ID
	 * @return this
	 */
	TokenResponseJwtBuilder clientId(String clientId) {
		return param("client_id", clientId);
	}

	/**
	 * Sets the redirect URI.
	 * 
	 * @param redirectUri redirect URI
	 * @return this
	 */
	TokenResponseJwtBuilder redirectUri(URI redirectUri) {
		return param("redirect_uri", redirectUri, IuJsonAdapter.of(URI.class));
	}

	/**
	 * Sets the token response.
	 * 
	 * @param tokenResponse fully formed token response
	 * @return this
	 */
	TokenResponseJwtBuilder tokenResponse(OAuthTokenResponse tokenResponse) {
		return param("token_response", tokenResponse,
				IuJsonAdapter.adapt(OAuthTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));
	}

	@Override
	public TokenResponseJwt build() {
		prepare();
		return new TokenResponseJwt(toJson());
	}

}
