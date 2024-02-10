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

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import edu.iu.auth.spi.IuOpenIdConnectSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Client-side SPI interface for interacting with an OpenID Provider.
 */
public interface IuOpenIdProvider {

	/**
	 * Configures the client view of an OpenID provider from a well-known
	 * configuration URI.
	 * 
	 * @param configUri             Well-known configuration URI
	 * @param trustRefreshInterval  Maximum length of time to keep trusted signing
	 *                              keys in cache
	 * @param authenticationTimeout Maximum length of time to allow for user
	 *                              authentication.
	 * @return Client view of the OpenID provider
	 */
	static IuOpenIdProvider from(URI configUri, Duration trustRefreshInterval, Duration authenticationTimeout) {
		return IuAuthSpiFactory.get(IuOpenIdConnectSpi.class).getOpenIdProvider(configUri, trustRefreshInterval,
				authenticationTimeout);
	}

	/**
	 * Gets the issue ID for this provider.
	 * 
	 * @return OpenID Provider issuer ID
	 */
	String getIssuer();

	/**
	 * Gets the OIDC User Info Endpoint URI
	 * 
	 * @return User Info Endpoint {@link URI}
	 */
	URI getUserInfoEndpoint();

	/**
	 * Creates an authorization client for interacting with the OpenID provider.
	 * 
	 * @param resourceUri       client resource URI
	 * @param clientCredentials client credentials
	 * @return authorization client
	 */
	IuAuthorizationClient createAuthorizationClient(URI resourceUri, IuApiCredentials clientCredentials);

	/**
	 * Verifies authentication attributes received via OAuth token response from an
	 * OIDC provider.
	 * 
	 * @param authResponse authorization response
	 * @return verified authentication attributes
	 */
	IuOpenIdAuthenticationAttributes verifyAuthentication(IuAuthorizationResponse authResponse);

}
