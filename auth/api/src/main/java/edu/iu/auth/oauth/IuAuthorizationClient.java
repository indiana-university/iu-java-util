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
package edu.iu.auth.oauth;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.spi.IuOAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Provides client configuration metadata for interacting with an authorization
 * server.
 * 
 * <p>
 * The interface <em>should</em> be implemented by the application client module
 * requiring authorization on behalf of an upstream authentication provider.
 * </p>
 */
public interface IuAuthorizationClient {

	/**
	 * Initializes client metadata for interacting with an OAuth authorization
	 * server.
	 * 
	 * <p>
	 * This method <em>must</em> be invoked exactly once per authentication realm,
	 * typically at resource container initialization time. Once the realm has been
	 * initialized, it <em>cannot</em> be modified. If client metadata needs to
	 * change, the authorization module <em>must</em> be reinitialized in order for
	 * the change can take effect.
	 * </p>
	 * 
	 * @param client client metadata
	 * @return client credentials grant for authorizing direct use of a downstream
	 *         service by the client
	 */
	static IuAuthorizationGrant initialize(IuAuthorizationClient client) {
		return IuAuthSpiFactory.get(IuOAuthSpi.class).initialize(client);
	}

	/**
	 * Gets the bearer authentication realm.
	 * 
	 * <p>
	 * The bearer authentication realm typically refers the client application's
	 * resource URI or session authorization endpoint.
	 * </p>
	 * 
	 * @return authentication realm
	 */
	String getRealm();

	/**
	 * Gets supported token principal authentication realms.
	 * 
	 * <p>
	 * To allow client credentials flow, include {@link #getRealm() the bearer
	 * authentication realm}. For authorization code flow, indicate the delegated
	 * authentication realm, typically the authorization server's resource URI or
	 * authorization endpoint.
	 * 
	 * @return token principal authentication realms
	 */
	Iterable<String> getPrincipalRealms();

	/**
	 * Gets the maximum length of time to allow for authentication, including
	 * interactions between the user agent and authorization server.
	 * 
	 * @return {@link Duration}
	 */
	Duration getAuthenticationTimeout();

	/**
	 * Gets the maximum length of time to allow for an access token to be
	 * authorized.
	 * 
	 * <p>
	 * This value will be used to determine the token expiration time if expires_in
	 * is not sent. It will also be compared to the expires_in value and used
	 * instead if smaller.
	 * </p>
	 * 
	 * @return {@link Duration}
	 */
	Duration getAuthorizationTimeToLive();

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
	 * Gets the request authorization scope.
	 * 
	 * @return request authorization scope
	 */
	Iterable<String> getScope();

	/**
	 * Gets the endpoint {@link URI} for the token server.
	 * 
	 * @return Token endpoint {@link URI}
	 */
	URI getTokenEndpoint();

	/**
	 * Gets the client's API credentials.
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getCredentials();

	/**
	 * Verifies an token response as valid within the client's authentication realm.
	 * 
	 * <p>
	 * The token response will already have been verified according to the rules
	 * outlined at
	 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-5.1">OAuth 2.0
	 * RFC-6749 Section 5.1</a>. This method <em>should</em> performs additional
	 * validation specified by the application realm and/or authentication provider.
	 * </p>
	 * 
	 * @param tokenResponse unverified token response
	 * @return {@link IuAuthorizedPrincipal}, must refer to a valid principal
	 *         identity for a configured {@link #getPrincipalRealms() principal
	 *         authentication realm} if in response to the authorization code flow,
	 *         or null if in response to client credentials flow
	 * @throws IuAuthenticationException If the token response is invalid (i.e.,
	 *                                   expired or revoked) for the authentication
	 *                                   realm and the user or remote client
	 *                                   <em>must</em> authenticate before access
	 *                                   can be authorized.
	 */
	IuAuthorizedPrincipal verify(IuTokenResponse tokenResponse) throws IuAuthenticationException;

	/**
	 * Verifies a refresh response as valid within the client's authentication
	 * realm.
	 * 
	 * <p>
	 * The token response will already have been verified according to the rules
	 * outlined at
	 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-5.1">OAuth 2.0
	 * RFC-6749 Section 5.1</a>. This method <em>should</em> performs additional
	 * validation specified by the application realm and/or authentication provider.
	 * </p>
	 * 
	 * @param refreshTokenResponse  refresh token response
	 * @param originalTokenResponse {@link #verify(IuTokenResponse) verified} token
	 *                              response previously
	 * @return {@link IuAuthorizedPrincipal}, must refer to a valid principal
	 *         identity for a configured {@link #getPrincipalRealms() principal
	 *         authentication realm}
	 * @throws IuAuthenticationException If the token response is invalid (i.e.,
	 *                                   expired or revoked) for the authentication
	 *                                   realm and the user or remote client
	 *                                   <em>must</em> authenticate before access
	 *                                   can be authorized.
	 */
	IuAuthorizedPrincipal verify(IuTokenResponse refreshTokenResponse, IuTokenResponse originalTokenResponse)
			throws IuAuthenticationException;

	/**
	 * Gets the endpoint {@link URI} for the authorization server.
	 * 
	 * @return Authorization endpoint {@link URI}
	 */
	default URI getAuthorizationEndpoint() {
		return null;
	}

	/**
	 * Gets the redirect URI for use with authorization code flow.
	 * 
	 * @return redirect URI; null if authorization code is not supported for the
	 *         authentication realm.
	 */
	default URI getRedirectUri() {
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
	 * @return {@link Map} of additional client credentials attributes; <em>may</em>
	 *         be null or empty if not required for establishing client credentials.
	 */
	default Map<String, String> getClientCredentialsAttributes() {
		return null;
	}

}
