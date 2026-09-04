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
package edu.iu.oidc.config;

import java.security.Principal;
import java.time.Instant;

/**
 * What an upstream identity provider established about the end user.
 *
 * <p>
 * The seam between a provider endpoint and however a deployment authenticates.
 * SAML, Kerberos, and a container's own login all produce the same four facts,
 * and those four are everything an authorization endpoint needs: who the end
 * user is, who vouched for them, when, and until when. A deployment adapts its
 * own mechanism to this, and nothing about that mechanism reaches the endpoint.
 * </p>
 *
 * <p>
 * {@link #getName()}, inherited from {@link Principal}, is the principal name
 * a grant is issued for and the {@code sub} that eventually names it.
 * </p>
 */
public interface OidcAuthenticatedPrincipal extends Principal {

	/**
	 * Gets the identity provider that authenticated the end user, which an ID token
	 * reports as {@code idp}.
	 *
	 * @return authentication authority, typically an entity ID
	 */
	String getAuthnAuthority();

	/**
	 * Gets when the end user was authenticated, which an ID token reports as
	 * {@code auth_time} and a refresh token's lifetime is measured from.
	 *
	 * @return {@link Instant}
	 */
	Instant getAuthnInstant();

	/**
	 * Gets when this authentication stops being usable.
	 *
	 * <p>
	 * An authorization endpoint that finds an established principal already expired
	 * treats it as no principal at all, and sends the end user back to the identity
	 * provider rather than issuing a grant against an authentication that has run
	 * out.
	 * </p>
	 *
	 * @return {@link Instant}; {@code null} reads as expired, since an
	 *         authentication that names no end is one nothing can vouch for
	 */
	Instant getExpires();

}
