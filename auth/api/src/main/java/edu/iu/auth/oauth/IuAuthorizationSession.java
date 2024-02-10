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
import java.util.Set;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.spi.IuOAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Manages client-side authorization session state.
 */
public interface IuAuthorizationSession {

	/**
	 * Creates a new {@link IuAuthorizationSession} for managing interactions with
	 * an authorization server.
	 * 
	 * @param realm      authentication realm, <em>must</em> correspond to an
	 *                   {@link IuAuthorizationClient#initialize(IuAuthorizationClient)
	 *                   initialized client}.
	 * @param entryPoint <em>optional</em> entry point URI for the application
	 *                   requiring authorization, <em>may</em> be sent to the user
	 *                   agent as a redirect when authorization expires or used as a
	 *                   default {@link #grant(Set, URI) applicationUri} value; null
	 *                   if not defined for the application
	 * @return authorization session
	 */
	static IuAuthorizationSession create(String realm, URI entryPoint) {
		return IuAuthSpiFactory.get(IuOAuthSpi.class).createAuthorizationSession(realm, entryPoint);
	}

	/**
	 * Gets a authorization code grant with default scope and entry point.
	 * 
	 * @return new authorization code grant
	 * @throws UnsupportedOperationException if an entry point was not defined
	 */
	IuAuthorizationCodeGrant grant() throws UnsupportedOperationException;

	/**
	 * Gets a authorization code grant using the session default entry point.
	 * 
	 * @param scope requested authorization scope; may be null or empty if a
	 *              specific scope is not requested, or to accept the default scope
	 *              as defined by the authorization server
	 * @return new authorization code grant
	 * @throws UnsupportedOperationException if an entry point was not defined
	 */
	IuAuthorizationCodeGrant grant(Set<String> scope) throws UnsupportedOperationException;

	/**
	 * Gets a authorization code grant by scope and application URI.
	 * 
	 * @param scope          requested authorization scope; may be null or empty if
	 *                       a specific scope is not requested, or to accept the
	 *                       default scope as defined by the authorization server
	 * @param applicationUri URI for the application requiring authorization,
	 *                       <em>may</em> be null to use the entry point;
	 *                       <em>required</em> if an entry point was not defined for
	 *                       the session.
	 * @return authorization code grant
	 */
	IuAuthorizationCodeGrant grant(Set<String> scope, URI applicationUri);

	/**
	 * Gets an authorization grant by its state identifier.
	 * 
	 * @param state state value generated from
	 *              {@link IuAuthorizationCodeGrant#getState()}
	 * @return authorization code grant
	 * @throws IuAuthenticationException         if the state value cannot be tied
	 *                                           to a valid authorization grant
	 */
	IuAuthorizationCodeGrant resume(String state) throws IuAuthenticationException;

}
