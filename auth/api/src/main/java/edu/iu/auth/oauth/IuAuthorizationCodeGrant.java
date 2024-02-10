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
package edu.iu.auth.oauth;

import java.net.URI;

import edu.iu.auth.IuAuthenticationException;

/**
 * Represents an authorization grant, as described by the
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4">OAuth 2.0
 * Authorization Framework</a>.
 */
public interface IuAuthorizationCodeGrant extends IuAuthorizationGrant {

	/**
	 * Gets the state identifier, used to locate a grant within an
	 * {@link IuAuthorizationSession authorization session}.
	 * 
	 * @return state identifier
	 */
	String getState();

	/**
	 * Gets the redirect URI.
	 * 
	 * @return {@link URI}
	 */
	URI getRedirectUri();

	/**
	 * Authorizes the grant using a code provided by the authorization server.
	 * 
	 * @param code authorization code
	 * @return response details following successful authorization
	 * 
	 * @throws IuAuthenticationException If authorization could not be
	 *                                            granted due to missing or expired
	 *                                            authentication.
	 */
	IuAuthorizationResponse authorize(String code) throws IuAuthenticationException;

}
