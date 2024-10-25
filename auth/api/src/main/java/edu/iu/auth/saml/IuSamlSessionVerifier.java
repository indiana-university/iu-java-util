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
package edu.iu.auth.saml;

import java.net.URI;
import java.util.function.Supplier;

import javax.security.auth.Subject;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.session.IuSession;
import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * SAML session verifier interface
 *
 */
public interface IuSamlSessionVerifier {

	/**
	 * Creates a new {@link IuSamlSessionVerifier} for managing interactions with a locally
	 * deployed Service Provider.
	 * @param postUri       HTTP POST Binding URI
	 * @return {@link IuSamlSessionVerifier}
	 */
	static IuSamlSessionVerifier create(URI postUri) {
		return IuAuthSpiFactory.get(IuSamlSpi.class).createVerifier(postUri);
	}

	/**
	 * Initiate request using session and return 
	 * Location {@link URI} for the configured Identity Provider with the appropriate SAML 
	 * {@code SAMLRequest} and {@code RelayState} HTTP query parameters.
	 * @param session session
	 * @param entryPointUri entry point URI
	 * 
	 * @return {@link URI}
	 */
	URI initRequest(IuSession session, URI entryPointUri);

	/**
	 * Decodes a SAML Response, performs Subject Confirmation validation logic, and
	 * validates assertions to authenticate a {@link IuPrincipalIdentity} as valid
	 * for the local Service Provider.
	 * 
	 * @param session session
	 * 
	 * @param remoteAddr   IP address to validate against allowed list
	 * @param samlResponse SAML response that received back from identity provider
	 *                     after user has been authenticate
	 * @param relayState   state value that received back from identity provider
	 *                     after successful authentication.
	 * @return entryPointUri
	 *
	 * @throws IuAuthenticationException when relay state is invalid or verification
	 *                                   failed
	 * 
	 */
	URI verifyResponse(IuSession session, String remoteAddr, String samlResponse, String relayState) throws IuAuthenticationException;

	/**
	 * Gets the authenticated SAML principal.
	 * @param session session
	 * 
	 * @return {@link IuPrincipalIdentity}; will include at least one
	 *         {@link IuSamlAssertion} in its
	 *         {@link IuPrincipalIdentity#getSubject() subject's}
	 *         {@link Subject#getPublicCredentials(Class) public credentials}.
	 * @throws IuAuthenticationException If not authenticated or authentication has
	 *                                   expired
	 */
	IuPrincipalIdentity getPrincipalIdentity(IuSession session) throws IuAuthenticationException;


}
