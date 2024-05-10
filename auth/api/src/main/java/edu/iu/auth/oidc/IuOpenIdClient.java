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

import java.net.URI;
import java.time.Duration;

/**
 * Provides client application metadata for configuring the OpenID client module
 * {@code iu.util.auth.oidc}. To be implemented by an application-level request
 * handler.
 * 
 * <p>
 * Only one OAuth authorization client configuration can be configured per
 * OpenID provider. It is expected but <em>optional</em> for implementations to
 * be context-senstive.
 * </p>
 */
public interface IuOpenIdClient {

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	String getRealm();

	/**
	 * Gets the root resource URI covered by this client's protection domain.
	 * 
	 * <p>
	 * All client-side application URIs used in this client's context <em>must</em>
	 * be rooted at this URI. The {@link URI} <em>may</em> be {@link URI#isOpaque()
	 * opaque} and/or {@link URI#isAbsolute() not absolute} if protecting only a
	 * single domain. Resource URIs <em>may</em> use the <strong>java:</strong> URI
	 * scheme for environments that have JNDI configured.
	 * </p>
	 * 
	 * @return {@link URI}
	 */
	URI getResourceUri();

	/**
	 * Gets the OpenID Provider configuration URI.
	 * 
	 * @return {@link URI}
	 */
	URI getProviderConfigUri();

	/**
	 * Gets the length of time to retain ID claims before reverifying.
	 * 
	 * @return {@link Duration}
	 */
	Duration getVerificationInterval();

}
