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
import java.security.Principal;

import edu.iu.jwt.WebToken;

/**
 * Client application view of principal user identity established via
 * interaction with an OIDC provider.
 */
public interface IuOidcPrincipal extends Principal {

	/**
	 * Updated session cookie, populated if a state change was required while
	 * resolving the principal.
	 * 
	 * @return Set-Cookie header value
	 */
	String getSetCookie();

	/**
	 * Gets the verified ID token for this principal.
	 * 
	 * @return {@link WebToken} ID token
	 */
	WebToken getIdToken();

	/**
	 * Gets a claim value.
	 * 
	 * @param <T>  claim value type
	 * @param name claim name
	 * @param type claim type
	 * @return claim value, from userinfo if available; else from ID token
	 */
	<T> T getClaim(String name, Class<T> type);

	/**
	 * Gets an access token issued to this principal for use with a given remote
	 * resource.
	 * 
	 * @param resourceUri root resource URI for the API to get an access token for
	 * @return access token for use at the indicated resource URI
	 */
	String getAccessToken(URI resourceUri);

}
