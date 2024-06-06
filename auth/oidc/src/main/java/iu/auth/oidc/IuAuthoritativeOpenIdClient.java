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
package iu.auth.oidc;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import edu.iu.auth.IuApiCredentials;

/**
 * Extends {@link IuOpenIdClient} with attributes necessary to establish an
 * authoritative trust relationship with the OpenID Provider.
 */
public interface IuAuthoritativeOpenIdClient extends IuOpenIdClient {

	/**
	 * Gets the client's API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getCredentials();

	/**
	 * Gets the maximum length of time to allow for authentication, including
	 * interactions between the user agent and authorization server.
	 * 
	 * @return {@link Duration}
	 */
	Duration getAuthenticationTimeout();

	/**
	 * Gets the maximum length of time to allow an authenticated session, as timed
	 * by the {@code auth_time} OIDC claim, to be remain active before requesting
	 * the provide re-establish credentials for the principal.
	 * 
	 * @return {@link Duration}, will be truncated to seconds
	 */
	Duration getAuthenticatedSessionTimeout();

	/**
	 * Gets the redirect URI for use with authorization code flow.
	 * 
	 * @return redirect URI; null if authorization code is not supported for the
	 *         authentication realm.
	 */
	URI getRedirectUri();

	/**
	 * Generates a one-time number.
	 * 
	 * <p>
	 * <em>Must</em>
	 * </p>
	 * 
	 * <ul>
	 * <li>Be globally unique</li>
	 * <li>Be returned from this method more than once</li>
	 * <li>Allow exactly one call to {@link #verifyNonce(String)} to complete
	 * successfully</li>
	 * </ul>
	 * 
	 * @return one-time number
	 */
	String createNonce();

	/**
	 * Verifies that a one-time number value was previously returned from
	 * {@link #createNonce()}.
	 * 
	 * @param nonce one-time number
	 */
	void verifyNonce(String nonce);

	/**
	 * Gets the expected algorithm for token response signature.
	 * 
	 * @return JWT signature algorithm to expect in the {@code alg} claim
	 */
	default String getIdTokenAlgorithm() {
		return "RS256";
	}

	/**
	 * Gets the requested authorization scope.
	 * 
	 * <p>
	 * <em>Should not</em> include <strong>openid</strong>.
	 * </p>
	 * 
	 * @return request authorization scope
	 */
	default Iterable<String> getScope() {
		return null;
	}

	/**
	 * Get additional attributes to pass to the authorization endpoint when
	 * initiating authorization code flow.
	 * 
	 * <p>
	 * It is <em>not required</em> for repeat invocation to return the same
	 * attributes. An authorization session will invoke this method once when
	 * initiating authorization code flow, and retain those attributes throughout
	 * the flow.
	 * </p>
	 * 
	 * <p>
	 * <em>Must</em> not include any standard attributes defined by OAuth 2.0 or
	 * OIDC Core 1.0 for authorization code flow.
	 * </p>
	 * 
	 * @return {@link Map} of additional authorization code attributes; <em>may</em>
	 *         be null or empty if authorization code flow is not supported or
	 *         additional attributes are not required for token verification.
	 */
	default Map<String, String> getAuthorizationCodeAttributes() {
		return null;
	}

	/**
	 * Get additional attributes to pass to the token endpoint when initiating
	 * client credentials code flow.
	 * 
	 * <p>
	 * <em>Must</em> not include any standard attributes defined by OAuth 2.0 or
	 * OIDC Core 1.0 for authorization code flow.
	 * </p>
	 * 
	 * @return {@link Map} of additional client credentials attributes; <em>may</em>
	 *         be null or empty if not required for establishing client credentials.
	 */
	default Map<String, String> getClientCredentialsAttributes() {
		return null;
	}

}
