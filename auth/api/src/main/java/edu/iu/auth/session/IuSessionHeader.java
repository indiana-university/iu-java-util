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

import java.security.Principal;
import java.time.Duration;

import edu.iu.auth.oauth.IuAuthorizationScope;

/**
 * Represents application-level metadata pertaining to an authorized session.
 */
public interface IuSessionHeader extends Principal {

	/**
	 * Gets the key ID registered for the {@link #getIssuer() issuer} to use for
	 * signing the session token.
	 * 
	 * @return signing key ID
	 */
	String getKeyId();

	/**
	 * Gets the JWT algorithm for the {@link #getIssuer() issuer} to use for signing
	 * the session token.
	 * 
	 * @return JWT signature algorithm
	 */
	String getSignatureAlgorithm();

	/**
	 * Gets the issuing application's uniform principal name (e.g., root URI).
	 * 
	 * @return issuer principal name
	 */
	String getIssuer();

	/**
	 * Gets the client application's uniform principal name (e.g., root URI, JNDI
	 * context, etc).
	 * 
	 * @return audience
	 */
	String getAudience();

	/**
	 * Gets the authorized principals to include in the session.
	 * 
	 * <p>
	 * <em>Must</em> include at least one identifying principal, as defined by the
	 * <strong>token endpoint</strong>, as the first principal returned.
	 * <em>Should</em> include at least one {@link IuAuthorizationScope} and/or
	 * {@link IuSessionAttribute} instances in addition to the identifying
	 * principal. When a <strong>session token</strong> is refreshed, authorized
	 * principals included in the session will be verified as matching those used to
	 * create the session.
	 * </p>
	 * 
	 * @return {@link Iterable} of authorized principals
	 */
	Iterable<Principal> getAuthorizedPrincipals();

	/**
	 * Determines if a refresh token can be used with the session.
	 * 
	 * <p>
	 * The default value of false is recommended for high-risk scenarios. The
	 * <strong>token endpoint</strong> <em>should</em> evaluate risk before
	 * overriding this value.
	 * </p>
	 * 
	 * @return true to generate a refresh token; false (default) to disable token
	 *         refresh
	 */
	default boolean isRefresh() {
		return false;
	}

	/**
	 * Gets the token expiration time.
	 * 
	 * <p>
	 * The default value of PT2M is recommended for high-risk scenarios. The
	 * <strong>token endpoint</strong> <em>should</em> evaluate risk before
	 * overriding this value.
	 * </p>
	 * 
	 * @return {@link Duration}
	 */
	default Duration getTokenExpires() {
		return Duration.ofMinutes(2L);
	}

	/**
	 * Gets the session expiration time.
	 * 
	 * <p>
	 * The default value of PT15M is recommended for high-risk scenarios allowing
	 * {@link #isRefresh() refresh token} use. The <strong>token endpoint</strong>
	 * <em>should</em> evaluate risk before overriding this value.
	 * </p>
	 * 
	 * @return {@link Duration}
	 */
	default Duration getSessionExpires() {
		return Duration.ofMinutes(15L);
	}

}
