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

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.spi.IuAuthClientSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Represents an authorization grant, as described by the
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4">OAuth 2.0
 * Authorization Framework</a>.
 */
public interface IuAuthorizationGrant {

	/**
	 * Creates a new {@link IuAuthorizationGrant} for managing interactions with
	 * an authorization server on behalf of the remote client.
	 * 
	 * @param clientId    Client ID
	 * @param resourceUri Root resource API
	 * @param scope       Scopes to request access for
	 * 
	 * @return {@link IuAuthorizationSession}
	 */
	static IuAuthorizationGrant create(String clientId, URI resourceUri, String... scope) {
		return IuAuthSpiFactory.get(IuAuthClientSpi.class).createAuthorizationGrant(clientId, resourceUri, scope);
	}
	
	/**
	 * Authorizes access to an application resource.
	 * 
	 * @return {@link IuApiCredentials} authorized to access the resource
	 * 
	 * @throws IuAuthenticationException If authorization could not be granted and
	 *                                   the client MUST complete authentication
	 *                                   before attempting authorization.
	 */
	IuApiCredentials authorize() throws IuAuthenticationException;

}
