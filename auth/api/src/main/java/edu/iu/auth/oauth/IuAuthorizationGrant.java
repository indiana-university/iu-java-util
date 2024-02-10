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

import java.util.Set;

import edu.iu.auth.IuAuthenticationException;

/**
 * Represents an authorization grant, as described by the
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4">OAuth 2.0
 * Authorization Framework</a>.
 */
public interface IuAuthorizationGrant {

	/**
	 * Gets the client ID.
	 * 
	 * @return client ID.
	 */
	String getClientId();

	/**
	 * Gets the requested scope.
	 * 
	 * @return requested scope, may be null to request the default scope as defined
	 *         by the authorization server
	 */
	Set<String> getScope();

	/**
	 * Authorizes the grant using previously established authentication attributes.
	 * 
	 * @return response details following successful authorization.
	 * 
	 * @throws IuAuthenticationException If authorization could not be granted and
	 *                                   the client <em>must</em> complete
	 *                                   authentication before attempting
	 *                                   authorization.
	 */
	IuAuthorizationResponse authorize() throws IuAuthenticationException;

	/**
	 * Discards all established credentials, forcing direct interaction with the
	 * authorization server on the next use.
	 * 
	 * <p>
	 * Implementations <em>should</em> delegate credentials revocation to the
	 * authorization server, if support.
	 * </p>
	 */
	void revoke();

}
