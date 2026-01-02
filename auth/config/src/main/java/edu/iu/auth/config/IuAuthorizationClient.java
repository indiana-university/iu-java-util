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
package edu.iu.auth.config;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import edu.iu.IuIterable;
import edu.iu.client.IuJsonAdapter;

/**
 * Provides client configuration.
 */
public interface IuAuthorizationClient {

	/**
	 * Enumerate token endpoint authentication methods types.
	 */
	enum AuthMethod {

		/**
		 * Direct use of client secret as password at API endpoint via Authorization
		 * Basic header.
		 * 
		 * @deprecated Most container standards <em>require</em> support for basic
		 *             authentication, and Basic auth is supported by current HTTP
		 *             standards. However, password authentication is insecure and
		 *             <em>should not</em> be used in production. This
		 *             {@link AuthMethod} is useful for development and test
		 *             environments and will be retained long term, but is deprecated to
		 *             discourage direct use by enterprise applications.
		 */
		@Deprecated
		BASIC("basic", Duration.ofDays(120L), true),

		/**
		 * Direct use of an access token via Authorization Bearer header.
		 */
		BEARER("bearer", Duration.ofHours(12L), false),

		/**
		 * Bearer token w/ use of client secret as password at token endpoint via
		 * Authorization Basic header.
		 * 
		 * @deprecated OAuth 2.0 <em>requires</em> support for client_secret password,
		 *             and Basic auth is supported by current HTTP standards. However,
		 *             password authentication is insecure and <em>should not</em> be
		 *             used in production. This {@link AuthMethod} is useful for
		 *             development and test environments and will be retained long term,
		 *             but is deprecated to discourage direct use by enterprise
		 *             applications.
		 */
		@Deprecated
		CLIENT_SECRET_BASIC("client_secret_basic", Duration.ofDays(120L), true),

		/**
		 * Bearer token w/ use of client secret as password at token endpoint via POST
		 * parameter.
		 * 
		 * @deprecated OAuth 2.0 <em>requires</em> support for client_secret POST.
		 *             However, password authentication is insecure and <em>should
		 *             not</em> be used in production. This {@link AuthMethod} is useful
		 *             for development and test environments and will be retained long
		 *             term, but is deprecated to discourage direct use by enterprise
		 *             applications.
		 */
		@Deprecated
		CLIENT_SECRET_POST("client_secret_post", Duration.ofDays(120L), true),

		/**
		 * Bearer token w/ use of client secret as HMAC key for signing a JWT assertion.
		 */
		CLIENT_SECRET_JWT("client_secret_jwt", Duration.ofDays(455L), false),

		/**
		 * Bearer token w/ use of X509 certificate to demonstrate proof of private key
		 * possession via JWT assertion.
		 */
		PRIVATE_KEY_JWT("private_key_jwt", Duration.ofDays(830L), false);

		/**
		 * JSON type adapter.
		 */
		public static IuJsonAdapter<AuthMethod> JSON = IuJsonAdapter.text(AuthMethod::from, a -> a.parameterValue);

		/**
		 * Gets an authentication method from standard parameter value.
		 * 
		 * @param parameterValue standard parameter value
		 * @return {@link AuthMethod}
		 */
		public static AuthMethod from(String parameterValue) {
			return IuIterable.select(IuIterable.iter(AuthMethod.values()),
					a -> a.parameterValue.equals(parameterValue));
		}

		/**
		 * Standard parameter value.
		 */
		public final String parameterValue;

		/**
		 * Designates the minimum duration to allow for client configuration to expire
		 * with the auth method enabled.
		 */
		public final Duration ttlPolicy;

		/**
		 * True if IP restrictions are required in order to use this authentication
		 * method.
		 */
		public final boolean requiresIpAllow;

		private AuthMethod(String parameterValue, Duration ttlPolicy, boolean requiresIpAllow) {
			this.parameterValue = parameterValue;
			this.ttlPolicy = ttlPolicy;
			this.requiresIpAllow = requiresIpAllow;
		}
	}

	/**
	 * Enumerates grant types.
	 * 
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7591#section-2">OAuth
	 *      2.0 Dynamic Client Registration</a>
	 */
	enum GrantType {

		/**
		 * Authorization code.
		 * 
		 * @see <a href=
		 *      "https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth">OIDC
		 *      Core 1.0 Section 3.1</a>
		 */
		AUTHORIZATION_CODE("code"),

		/**
		 * Client credentials.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">OAuth 2.0
		 *      Section 4.4</a>
		 */
		CLIENT_CREDENTIALS("client_credentials"),

		/**
		 * Resource owner password.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-4.3">OAuth 2.0
		 *      Section 4.3</a>
		 */
		PASSWORD("password"),

		/**
		 * Refresh token.
		 * 
		 * @see <a href=
		 *      "https://openid.net/specs/openid-connect-core-1_0.html#OfflineAccess">OIDC
		 *      Core 1.0 Section 11</a>
		 */
		REFRESH_TOKEN("refresh_token"),

		/**
		 * JWT bearer assertion.
		 * 
		 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">OAuth 2.0 JWT
		 *      Bearer Token Profiles</a>
		 */
		JWT_BEARER("urn:ietf:params:oauth:grant-type:jwt-bearer");

		/**
		 * JSON type adapter.
		 */
		public static IuJsonAdapter<GrantType> JSON = IuJsonAdapter.text(GrantType::from, a -> a.parameterValue);

		/**
		 * Gets a grant type from parameter value.
		 * 
		 * @param parameterValue standard parameter value
		 * @return {@link GrantType}
		 */
		public static GrantType from(String parameterValue) {
			return IuIterable.select(IuIterable.iter(GrantType.values()), a -> a.parameterValue.equals(parameterValue));
		}

		/**
		 * Standard parameter value.
		 */
		public final String parameterValue;

		private GrantType(String parameterValue) {
			this.parameterValue = parameterValue;
		}

	}

	/**
	 * Defines credentials issued to this client.
	 */
	interface Credentials extends IuPrivateKeyPrincipal {

		/**
		 * Gets the grant types the credentials are authorized for use with.
		 * 
		 * @return {@link Set} of {@link GrantType}
		 */
		Set<GrantType> getGrantTypes();

		/**
		 * Gets allowed authentication method.
		 * 
		 * @return {@link AuthMethod}
		 */
		AuthMethod getTokenEndpointAuthMethod();

		/**
		 * Gets the point in time the credentials expire.
		 * 
		 * @return {@link Instant}
		 */
		Instant getExpires();

	}

	/**
	 * Gets the authentication realm to use with
	 * {@link GrantType#AUTHORIZATION_CODE}.
	 * 
	 * @return redirect URIs
	 */
	String getRealm();

	/**
	 * Gets redirect URIs allowed for this client to use with
	 * {@link GrantType#AUTHORIZATION_CODE}.
	 * 
	 * @return redirect URIs
	 */
	Set<URI> getRedirectUri();

	/**
	 * Gets the allowed IP address ranges.
	 * 
	 * @return Set of allowed IP address ranges
	 */
	Set<String> getIpAllow();

	/**
	 * Defines the maximum time to live for assertions issued by this client.
	 * 
	 * @return {@link Duration}
	 */
	Duration getAssertionTtl();

	/**
	 * Gets credentials issued to this client.
	 * 
	 * @return {@link Credentials}
	 */
	Iterable<? extends Credentials> getCredentials();

}
