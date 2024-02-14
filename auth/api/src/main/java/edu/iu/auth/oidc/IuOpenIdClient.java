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

import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuOutOfServiceException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuTokenResponse;

/**
 * Provides client application metadata for configuring the OpenID client module
 * {@code iu.util.auth.oidc}. To be implemented by an application-level web
 * request handler.
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
	 * 
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
	 * Gets the maximum length of time to allow an authenticated session, as timed
	 * by the {@code auth_time} OIDC claim, to be remain active before requesting
	 * the provide re-establish credentials for the principal.
	 * 
	 * @return {@link Duration}, will be truncated to seconds
	 */
	Duration getAuthenticatedSessionTimeout();

	/**
	 * Gets the maximum length of time to assume a prior activation to be
	 * repeatable, typically measured in seconds. Once this interval is passed,
	 * activation will be confirmed by sending session-bound access tokens to a
	 * downstream verification API.
	 * 
	 * @return {@link Duration}, will be truncated to milliseconds
	 */
	Duration getActivationInterval();

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
	 * Gets the redirect URI for use with authorization code flow.
	 * 
	 * @return redirect URI; null if authorization code is not supported for the
	 *         authentication realm.
	 */
	URI getRedirectUri();

	/**
	 * Gets the client's API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getCredentials();

	/**
	 * Revalidates verified credentials as not expired, not revoked, and not
	 * prohibited by an environment restriction specified by the application realm
	 * or authentication provider, as a condition for session activation.
	 * 
	 * @param credentials credentials to revalidate
	 * @throws IuAuthenticationException      If the credentials are invalid (i.e.,
	 *                                        expired or revoked) for the
	 *                                        authentication realm and the user or
	 *                                        remote client <em>must</em>
	 *                                        reauthenticate before a session may be
	 *                                        activated.
	 * @throws IuBadRequestException          If the credentials are malformed or
	 *                                        not understood.
	 * @throws IuAuthorizationFailedException If access has been revoked since since
	 *                                        initial authorization.
	 * @throws IuOutOfServiceException        If credentials and authorization are
	 *                                        still valid but access should be
	 *                                        denied due to an environment
	 *                                        restriction.
	 * @throws IllegalStateException          If an unrecoverable error occurs,
	 *                                        e.g., communicating with an
	 *                                        authentication provider
	 */
	void activate(IuApiCredentials credentials) throws IuAuthenticationException, IuBadRequestException,
			IuAuthorizationFailedException, IuOutOfServiceException, IllegalStateException;

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
