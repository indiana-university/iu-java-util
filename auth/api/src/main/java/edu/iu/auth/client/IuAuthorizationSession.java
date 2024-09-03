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
package edu.iu.auth.client;

import java.net.URI;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.spi.IuAuthClientSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Manages client-side authorization session state.
 */
public interface IuAuthorizationSession {

	/**
	 * Creates a new {@link IuAuthorizationSession} for managing interactions with
	 * an authorization server.
	 * 
	 * @param clientId    Client ID
	 * @param resourceUri Root resource API
	 * @param scope       Scopes to request access for
	 * 
	 * @return {@link IuAuthorizationSession}
	 */
	static IuAuthorizationSession create(String clientId, URI resourceUri, String... scope) {
		return IuAuthSpiFactory.get(IuAuthClientSpi.class).createAuthorizationSession(clientId, resourceUri, scope);
	}

	/**
	 * Initiates authorization for an end-user.
	 * 
	 * @param redirectUri Application redirect {@link URI}, MUST be registered for
	 *                    the client at the token endpoint. The user will be
	 *                    returned to this URI once authorization is complete.
	 * @return {@link URI} to direct the user agent to after completing
	 *         authorization
	 */
	URI initiate(URI redirectUri);

	/**
	 * Completes end-user authorization via a code and state value provided by the
	 * authorization server.
	 * 
	 * @param code  authorization code
	 * @param state opaque value tying the authorization code to a pending request
	 *
	 * @return {@link IuAuthorizationGrant}
	 * @throws IuAuthenticationException If authorization could not be granted due
	 *                                   to missing or expired authentication.
	 */
	IuAuthorizationGrant complete(String code, String state) throws IuAuthenticationException;

}
