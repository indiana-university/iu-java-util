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
package edu.iu.auth.session;

import java.security.Principal;
import java.time.Instant;

import edu.iu.auth.oauth.IuBearerAuthCredentials;
import edu.iu.auth.spi.IuSessionSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * <strong>Session tokens</strong> form trust relationships between
 * applications.
 */
public interface IuSessionToken extends IuBearerAuthCredentials {

	/**
	 * An application's <strong>token endpoint</strong> <em>may</em> create a
	 * <strong>session token</strong> after <strong>authorizing</strong> the client.
	 * 
	 * <p>
	 * If the <strong>token endpoint</strong> delegates authentication to the
	 * client, its access control logic should verify credentials for both the
	 * authenticated principal and the client's principal identity. Once
	 * <strong>authorized</strong>, a <strong>session token</strong> <em>may</em> be
	 * created and provided to the client by passing verified metadata as
	 * {@link IuSessionHeader}.
	 * </p>
	 * 
	 * @param header {@link IuSessionHeader}
	 * @return {@link IuSessionToken}
	 */
	static IuSessionToken create(IuSessionHeader header) {
		return IuAuthSpiFactory.get(IuSessionSpi.class).create(header);
	}

	/**
	 * Refreshes a session token.
	 * 
	 * <p>
	 * Used by the application token endpoint.
	 * </p>
	 * 
	 * @param authorizedPrincipals {@link Iterable} of authorized principals to use
	 *                             for verfying the refresh token.
	 * @param refreshToken         refresh token
	 * @return {@link IuSessionToken}
	 */
	static IuSessionToken refresh(Iterable<Principal> authorizedPrincipals, String refreshToken) {
		return IuAuthSpiFactory.get(IuSessionSpi.class).refresh(authorizedPrincipals, refreshToken);
	}

	/**
	 * Authorizes a session from its access token.
	 * 
	 * <p>
	 * Used by the application service endpoint.
	 * </p>
	 * 
	 * @param accessToken access token
	 * @return {@link IuSessionToken}
	 */
	static IuSessionToken authorize(String accessToken) {
		return IuAuthSpiFactory.get(IuSessionSpi.class).authorize(accessToken);
	}

	/**
	 * Gets the token expiration time.
	 * 
	 * @return token expiration time
	 */
	Instant getTokenExpires();

	/**
	 * Gets the session expiration time.
	 * 
	 * @return session expiration time
	 */
	Instant getSessionExpires();

	/**
	 * Gets a refresh token.
	 * 
	 * @return refresh token
	 */
	String getRefreshToken();

	/**
	 * Serializes as an OAuth token response.
	 * 
	 * @return OAuth token response
	 */
	String asTokenResponse();

}
