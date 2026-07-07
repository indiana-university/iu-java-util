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

import edu.iu.IuRequestAttributes;
import edu.iu.IuStatefulRedirect;

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
	 */
	IuStatefulRedirect init(String delegatingPrincipal, String impersonatedPrincipalName);

	/**
	 * Resumes an authorization session upon return from the authorization server.
	 * 
	 * @param attributes request attributes
	 * @param code       authorization code
	 * @param state      state parameter value
	 * @return Verified {@link IuStatefulRedirect}
	 */
	IuStatefulRedirect authorize(IuRequestAttributes attributes, String code, String state);

	/**
	 * Gets the {@link IuOidcPrincipal} previously authorized for a web session.
	 * 
	 * @param attributes request attributes
	 * @return Verified {@link IuOidcPrincipal}
	 */
	IuOidcPrincipal getAuthorizedPrincipal(IuRequestAttributes attributes);

}
