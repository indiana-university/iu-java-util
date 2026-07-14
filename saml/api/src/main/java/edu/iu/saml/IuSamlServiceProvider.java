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
package edu.iu.saml;

import java.net.URI;

import edu.iu.IuRequestAttributes;
import edu.iu.IuStatefulRedirect;

/**
 * SAML 2.0 Service Provider interface.
 *
 */
public interface IuSamlServiceProvider {

	/**
	 * Gets configured SP metadata as raw XML.
	 * 
	 * @return SP metadata XML
	 */
	String metadata();

	/**
	 * Initiate an authentication request.
	 * 
	 * @param postUri           HTTP POST URI for handling the SAML response
	 * @param returnUri         URI to return the user to after successful
	 *                          authentication
	 * 
	 * @return {@link IuStatefulRedirect}
	 */
	IuStatefulRedirect initRequest(URI postUri, URI returnUri);

	/**
	 * Handles a SAML assertion consumer service POST request.
	 * 
	 * @param requestAttributes Incoming request attributes
	 * @param samlResponse      SAML response received from identity provider
	 *                          after user has been authenticated.
	 * @param relayState        state value that received back from identity
	 *                          provider after successful authentication.
	 * 
	 * @return {@link IuStatefulRedirect}
	 */
	IuStatefulRedirect verifyResponse(IuRequestAttributes requestAttributes, String samlResponse, String relayState);

	/**
	 * Gets the authenticated SAML principal.
	 * 
	 * @param requestAttributes Incoming request attributes
	 * @return {@link IuSamlPrincipal}
	 */
	IuSamlPrincipal getPrincipalIdentity(IuRequestAttributes requestAttributes);

}
