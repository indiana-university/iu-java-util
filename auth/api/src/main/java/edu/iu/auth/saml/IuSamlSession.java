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
import edu.iu.auth.spi.IuSamlSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Manages SAML Service Provider session state.
 * <p>
 * Implementations <em>must</em> return current session state in tokenized form
 * via the {@link #toString()} method, for re-entry using the same secret key
 * value.
 * </p>
 */
public interface IuSamlSession {

	/**
	 * Creates a new {@link IuSamlSession} for managing interactions with a locally
	 * deployed Service Provider.
	 * 
	 * @param entryPointUri application entry point URI
	 * @param postUri       HTTP POST Binding URI
	 * @param secretKey     Supplies a secret key for tokenizing the session, must
	 *                      contain 128, 192, or 256 bits of securely generated
	 *                      pseudo-random data. This value <em>should</em> be
	 *                      encoded as a cookie and stored only on the user-agent
	 *                      using HTTPOnly, Secure, SameSite='strict', and
	 *                      Max-Age=900; endpoint session storage <em>should</em>
	 *                      store the tokenized session at deactivation time, keyed
	 *                      by the SHA-256 checksum of the secret key. Secret keys
	 *                      and tokenized sessions may be rotated at the endpoint's
	 *                      discretion, but <em>must</em> be refreshed at least
	 *                      every 15 minutes to remain authenticated. Shorter
	 *                      session inactivity duration values <em>may</em> be
	 *                      configured.
	 * @return {@link IuSamlSession}
	 */
	static IuSamlSession create(URI entryPointUri, URI postUri, Supplier<byte[]> secretKey) {
		return IuAuthSpiFactory.get(IuSamlSpi.class).createSession(entryPointUri, postUri, secretKey);
	}

	/**
	 * Activates a tokenized session
	 * 
	 * @param sessionToken Session token
	 * @param secretKey    Supplies the secret key to use for decoding the tokenized
	 *                     session.
	 * @return {@link IuSamlSession}
	 */
	static IuSamlSession activate(String sessionToken, Supplier<byte[]> secretKey) {
		return IuAuthSpiFactory.get(IuSamlSpi.class).activateSession(sessionToken, secretKey);
	}

	/**
	 * Gets a Location {@link URI} for the configured Identity Provider with
	 * {@code SAMLRequest} and {@code RelayState} HTTP query parameters.
	 * 
	 * @return {@link URI}
	 */
	URI getRequestUri();

	/**
	 * Decodes a SAML Response, performs Subject Confirmation validation logic, and
	 * validates assertions to authenticate a {@link IuPrincipalIdentity} as valid
	 * for the local Service Provider.
	 * 
	 * <p>
	 * The session may be tokenized after invoking this method, then detokenized to
	 * recover the authenticated entity via {@link #getPrincipalIdentity()}.
	 * </p>
	 * 
	 * @param remoteAddr   IP address to validate against allowed list
	 * @param samlResponse SAML response that received back from identity provider
	 *                     after user has been authenticate
	 * @param relayState   state value that received back from identity provider
	 *                     after successful authentication.
	 *
	 * @throws IuAuthenticationException when relay state is invalid or verification
	 *                                   failed
	 * 
	 */
	void verifyResponse(String remoteAddr, String samlResponse, String relayState) throws IuAuthenticationException;

	/**
	 * Gets the authenticated SAML principal.
	 * 
	 * @return {@link IuPrincipalIdentity}; will include at least one
	 *         {@link IuSamlAssertion} in its
	 *         {@link IuPrincipalIdentity#getSubject() subject's}
	 *         {@link Subject#getPublicCredentials(Class) public credentials}.
	 * @throws IuAuthenticationException If not authenticated or authentication has
	 *                                   expired
	 */
	IuPrincipalIdentity getPrincipalIdentity() throws IuAuthenticationException;

	/**
	 * Tokenizes session state, for use at deactivation time, using the secret key
	 * supplied via {@link #create(URI, URI, Supplier)}, for later activation by
	 * {@link #activate(String, Supplier)}.
	 * 
	 * @return tokenized session
	 */
	@Override
	String toString();

}
