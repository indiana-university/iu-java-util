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
import java.util.Map;

import edu.iu.auth.IuApiCredentials;

/**
 * Provides client application metadata for configuration an OpenID client
 * module. To implementation by an application-level web request handler.
 * 
 * <p>
 * Only one OAuth authorization client configuration can be configured per
 * OpenID provider. It is expected but <em>optional</em> for implementations to
 * be context-senstive.
 * </p>
 */
public interface IuOpenIdClient {

	/**
	 * Gets the maximum time to retain provider trusted keys before attempting to
	 * refresh.
	 * <p>
	 * Once keys have been read successfully at least once, the last known good
	 * version will be returned in the event of an error.
	 * </p>
	 * 
	 * @return {@link Duration}
	 */
	Duration getTrustRefreshInterval();

	/**
	 * Gets the maximum length of time to allow for authentication, including
	 * interactions between the user agent and authorization server.
	 * 
	 * @return {@link Duration}
	 */
	Duration getAuthenticationTimeout();

	/**
	 * Gets the root resource URI covered by this client's protection domain.
	 * 
	 * <p>
	 * All client-side application URIs used with this client <em>must</em> begin
	 * with this URI. The root resource URI <em>should</em> end with a '/' character
	 * unless the client is only intended to protect a single URI.
	 * </p>
	 * 
	 * @return {@link URI}
	 */
	URI getResourceUri();

	/**
	 * Gets the client's API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getCredentials();

	/**
	 * Gets the redirect URI for use with authorization code flow.
	 * 
	 * @return redirect URI; null if authorization code is not supported for the
	 *         authentication realm.
	 */
	URI getRedirectUri();

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

	/**
	 * Revokes credentials established for use with the client's authentication
	 * realm, if supported.
	 * 
	 * @param credentials credentials to revoke
	 */
	default void revoke(IuApiCredentials credentials) {
	}

}
