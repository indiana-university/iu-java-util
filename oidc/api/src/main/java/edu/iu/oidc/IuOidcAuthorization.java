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

import java.io.IOException;
import java.util.function.Consumer;

import edu.iu.IuRequestAttributes;
import edu.iu.IuStatefulRedirect;
import edu.iu.session.IuSession;

/**
 * Client application resource interface for OIDC authorization code flow.
 */
public interface IuOidcAuthorization {

	/**
	 * Initialized a new authorization session.
	 *
	 * @param delegatingPrincipal       required delegating principal name; null to
	 *                                  authorize on behalf of the authenticated
	 *                                  user
	 * @param impersonatedPrincipalName requested impersonated principal name; null
	 *                                  if not requesting impersonation, SHOULD be
	 *                                  null in production environments
	 *
	 * @return authorization redirect
	 * @throws IOException if communication with an upstream provider is interrupted
	 */
	default IuStatefulRedirect init(String delegatingPrincipal, String impersonatedPrincipalName) throws IOException {
		return init(delegatingPrincipal, impersonatedPrincipalName, null);
	}

	/**
	 * Initializes a new authorization session, allowing the caller to record its
	 * own detail on the pre-authentication session.
	 *
	 * <p>
	 * The session is created and stored by this method, so a caller that needs
	 * state to survive the round trip to the authorization server has no other
	 * opportunity to write it: {@code preAuthDetail} is invoked after this
	 * implementation's own pre-authentication detail is set and before the session
	 * is stored, so one store carries both. Whatever it writes is readable from the
	 * session the caller activates on return, and is carried onto the authenticated
	 * session by
	 * {@link #authorize(IuRequestAttributes, String, String) authorize}.
	 * </p>
	 *
	 * @param delegatingPrincipal       required delegating principal name; null to
	 *                                  authorize on behalf of the authenticated
	 *                                  user
	 * @param impersonatedPrincipalName requested impersonated principal name; null
	 *                                  if not requesting impersonation, SHOULD be
	 *                                  null in production environments
	 * @param preAuthDetail             receives the pre-authentication session
	 *                                  before it is stored; null to record no
	 *                                  additional detail
	 *
	 * @return authorization redirect
	 * @throws IOException if communication with an upstream provider is interrupted
	 */
	IuStatefulRedirect init(String delegatingPrincipal, String impersonatedPrincipalName,
			Consumer<IuSession> preAuthDetail) throws IOException;

	/**
	 * Resumes an authorization session upon return from the authorization server.
	 *
	 * @param attributes request attributes
	 * @param code       authorization code
	 * @param state      state parameter value
	 * @return Verified {@link IuStatefulRedirect}
	 * @throws IOException if communication with an upstream provider is interrupted
	 */
	IuStatefulRedirect authorize(IuRequestAttributes attributes, String code, String state) throws IOException;

	/**
	 * Gets the {@link IuOidcPrincipal} previously authorized for a web session.
	 * 
	 * @param attributes request attributes
	 * @return Verified {@link IuOidcPrincipal}
	 * @throws IOException                     if communication with an upstream
	 *                                         provider is interrupted
	 */
	IuOidcPrincipal getAuthorizedPrincipal(IuRequestAttributes attributes)
			throws IOException;

}
