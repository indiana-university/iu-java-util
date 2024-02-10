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
package edu.iu.auth.oidc;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

import javax.security.auth.Subject;

/**
 * Represents verified attributes following successful authentication by an
 * {@link IuOpenIdProvider OIDC Provider}.
 */
public interface IuOpenIdAuthenticationAttributes {

	/**
	 * Gets the authenticated principal.
	 * 
	 * @return authenticated principal
	 */
	Principal getPrincipal();

	/**
	 * Gets the authorized subject.
	 * 
	 * @return authorized subject
	 */
	Subject getSubject();

	/**
	 * Gets the point in time when the principal's credentials were verified.
	 * 
	 * @return authentication time
	 */
	Instant getAuthenticationTime();

	/**
	 * Gets the point in time when the verified ID token was issued.
	 * 
	 * @return authentication time
	 */
	Instant getIdTokenIssuedAt();

	/**
	 * Gets the point in time when verified the ID token expires.
	 * 
	 * @return authentication time
	 */
	Instant getIdTokenExpires();

	/**
	 * Gets the authorized scope.
	 * 
	 * @return authorized scope
	 */
	String getScope();

	/**
	 * Gets claims included with the verified ID token, excluding those involved in
	 * the verification process, and attributes released through the OIDC userinfo
	 * endpoint.
	 * 
	 * @return authentication attributes
	 */
	Map<String, ?> getAttributes();

}
