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
package edu.iu.auth.session;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuAuthorizationScope;
import edu.iu.auth.oauth.IuBearerAuthCredentials;
import edu.iu.auth.spi.IuSessionSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * <strong>Session tokens</strong> form trust relationships between
 * applications.
 */
public interface IuSessionToken extends IuBearerAuthCredentials {

	/**
	 * Registers a session token provider's issuer credentials.
	 * 
	 * <p>
	 * The subject provided must include:
	 * </p>
	 * 
	 * <ul>
	 * <li>One {@link IuPrincipalIdentity} with a valid URI principal name
	 * designating the authentication realm supported by the session endpoint.</li>
	 * <li>At least one {@link IuAuthorizationScope}, with
	 * {@link IuAuthorizationScope#getRealm()} matching the principal name of the
	 * identifying principal, designating the authorization scope(s) supported by
	 * the provider.</li>
	 * <li>At least one {@link IuSessionProviderKey} instance available via
	 * {@link Subject#getPrivateCredentials(Class)}</li>
	 * </ul>
	 * 
	 * <p>
	 * A provider can only be registered once, either via this method or using
	 * {@link #register(URI, URI, Duration, Duration)}. Issuer credentials can be
	 * use to create and authorize a token, and cannot be modified without
	 * restarting the application.
	 * </p>
	 * 
	 * @param realm    authentication realms supported for principal identity
	 *                 verification
	 * @param provider {@link Subject} describes the session token provider.
	 * @return JWKS representation of the provider's public keys, for publishing to
	 *         remote clients as a {@link #register(URI, URI, Duration, Duration)
	 *         well-known key set}.
	 */
	static String register(Set<String> realm, Subject provider) {
		return IuAuthSpiFactory.get(IuSessionSpi.class).register(realm, provider);
	}

	/**
	 * Registers a remotely hosted session token provider by its well-known public
	 * key set.
	 * 
	 * <p>
	 * This registration creates an implicit trust relationship with the remote
	 * provider's authentication strategy. Sessions should
	 * 
	 * <p>
	 * A provider can only be registered once, either via this method or using
	 * {@link #register(Set, Subject)}.
	 * </p>
	 * 
	 * @param issuer          remote session provider root URI
	 * @param jwksUri         well-known key set URI
	 * @param tokenTtl        maximum length of time, relative to the token issued
	 *                        time (iat claim), to allow access tokens issued by the
	 *                        remote provider to be used for authorizing sessions on
	 *                        this endpoint
	 * @param refreshInterval cache time to live for parsed JWKS key data
	 */
	static void register(URI issuer, URI jwksUri, Duration tokenTtl, Duration refreshInterval) {
		IuAuthSpiFactory.get(IuSessionSpi.class).register(issuer, jwksUri, tokenTtl, refreshInterval);
	}

	/**
	 * An application's <strong>token endpoint</strong> <em>may</em> create a
	 * <strong>session token</strong> after <strong>authorizing</strong> the client.
	 * 
	 * <p>
	 * Issuer credentials for {@link IuSessionHeader#getIssuer()} <em>must</em> be
	 * {@link #register(Set, Subject) registered}.
	 * </p>
	 * 
	 * <p>
	 * If the <strong>token endpoint</strong> delegates authentication to the
	 * client, its access control logic should verify credentials for both the
	 * authenticated principal and the client's principal identity. Once
	 * <strong>authorized</strong>, a <strong>session token</strong> <em>may</em> be
	 * created and provided to the client by passing verified metadata as
	 * {@link IuSessionHeader}.
	 * </p>
	 * 
	 * @param header {@link IuSessionHeader}
	 * @return {@link IuSessionToken}
	 */
	static IuSessionToken create(IuSessionHeader header) {
		return IuAuthSpiFactory.get(IuSessionSpi.class).create(header);
	}

	/**
	 * Refreshes a session token.
	 * 
	 * <p>
	 * Issuer credentials for verifying the refresh token, and signing the new
	 * access token <em>must</em> be {@link #register(Set, Subject) registered}.
	 * </p>
	 * 
	 * <p>
	 * Used by the application token endpoint.
	 * </p>
	 * 
	 * @param subject      authorized subject
	 * @param refreshToken refresh token
	 * @return {@link IuSessionToken}
	 */
	static IuSessionToken refresh(Subject subject, String refreshToken) {
		return IuAuthSpiFactory.get(IuSessionSpi.class).refresh(subject, refreshToken);
	}

	/**
	 * Authorizes a session from its access token.
	 * 
	 * <p>
	 * Either {@link #register(Set, Subject) Issuer credentials} or
	 * {@link #register(URI, URI, Duration, Duration) well-known key set}
	 * <em>must</em> be registered for the token issuer.
	 * </p>
	 * 
	 * <p>
	 * Used by the application service endpoint.
	 * </p>
	 * 
	 * @param audience    expected audience; typically the service endpoint's
	 *                    external root URI
	 * @param accessToken access token
	 * @return {@link IuSessionToken}
	 */
	static IuSessionToken authorize(String audience, String accessToken) {
		return IuAuthSpiFactory.get(IuSessionSpi.class).authorize(audience, accessToken);
	}

	/**
	 * Gets the root resource URI for the session issuer.
	 * 
	 * @return issuer
	 */
	URI getIssuer();

	/**
	 * Gets the token expiration time.
	 * 
	 * @return token expiration time
	 */
	Instant getTokenExpires();

	/**
	 * Gets the session expiration time.
	 * 
	 * @return session expiration time
	 */
	Instant getSessionExpires();

	/**
	 * Gets a refresh token.
	 * 
	 * @return refresh token
	 */
	String getRefreshToken();

	/**
	 * Serializes as an OAuth token response.
	 * 
	 * @return OAuth token response
	 */
	String asTokenResponse();

}
