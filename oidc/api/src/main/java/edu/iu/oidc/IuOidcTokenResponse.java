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
package edu.iu.oidc;

import java.net.URI;

/**
 * Encapsulates the response from an OIDC token endpoint.
 * 
 * @see <a href=
 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-5.1">RFC-6749
 *      OAuth 2.0 Section 5.1</a>
 */
public interface IuOidcTokenResponse {

	/**
	 * Gets the token type.
	 * 
	 * @return token type; i.e. "bearer"
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-7.1">RFC-6749
	 *      OAuth 2.0 Section 7.1</a>
	 */
	String getTokenType();

	/**
	 * Gets the access token.
	 * 
	 * @return access token
	 */
	String getAccessToken();

	/**
	 * Gets the OIDC ID token.
	 * 
	 * @return OIDC ID token
	 */
	String getIdToken();

	/**
	 * Gets the refresh token.
	 * 
	 * @return refresh token
	 */
	String getRefreshToken();

	/**
	 * Gets the number of seconds until the token expires.
	 * 
	 * @return number of seconds until the token expires
	 */
	int getExpiresIn();

	/**
	 * Gets the error; SHOULD be non-null if {@link #getAccessToken()} is null.
	 * 
	 * @return error code
	 */
	String getError();

	/**
	 * Gets the OPTIONAL error description.
	 * 
	 * @return error description
	 */
	String getErrorDescription();

	/**
	 * Gets the OPTIONAL error URI.
	 * 
	 * @return error URI
	 */
	URI getErrorUri();

}
