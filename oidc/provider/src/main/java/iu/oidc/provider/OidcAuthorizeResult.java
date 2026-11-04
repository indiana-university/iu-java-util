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
package iu.oidc.provider;

import java.net.URI;

/**
 * What an authorization request came to, for a transport to carry out.
 *
 * <p>
 * Sealed over two outcomes, and only two: either the user agent goes somewhere
 * and the request is done with, or the end user has to authenticate first. What
 * a transport does with either is its own &mdash; a servlet writes headers, a
 * JAX-RS resource builds a {@code Response} &mdash; and the endpoint neither
 * knows nor cares which.
 * </p>
 *
 * <p>
 * A request too malformed to answer this way doesn't produce a result at all.
 * Before a client and its redirect URI are verified there is no address this
 * provider has agreed to send a user agent to, so the endpoint raises
 * {@link edu.iu.IuBadRequestException} and the transport's error boundary
 * answers the user agent where it stands. Redirecting an error to an unverified
 * URI would make the endpoint an open redirector.
 * </p>
 */
public sealed interface OidcAuthorizeResult {

	/**
	 * Send the user agent to {@code location}; nothing further is pending.
	 *
	 * <p>
	 * Covers both ways a validated request ends: an authorization code, and an
	 * OAuth 2.0 error the client's own redirect URI is entitled to receive. They
	 * are one outcome here because a transport does the same thing with each, and
	 * because which of the two it is, is already legible in the URI.
	 * </p>
	 *
	 * @param location where to send the user agent
	 */
	record Redirect(URI location) implements OidcAuthorizeResult {
	}

	/**
	 * Authenticate the end user, then re-enter this endpoint at
	 * {@code returnUri}.
	 *
	 * <p>
	 * The validated request has been recorded in a session; {@code setCookie} is
	 * what carries the end user back to it, and a transport <em>must</em> relay it
	 * alongside whatever its own authentication mechanism sets. Losing it strands
	 * the request: the return leg finds nothing to resume.
	 * </p>
	 *
	 * <p>
	 * {@code returnUri} carries no parameters of its own, deliberately. The
	 * validated request rides in the session rather than in a URI an identity
	 * provider would see, so {@code state}, {@code nonce}, and the PKCE challenge
	 * never travel through it or turn up in its logs. It is this provider's own
	 * authorization endpoint, derived from the issuer, so the URI a relying party
	 * discovers and the URI the end user comes back to are the same one.
	 * </p>
	 *
	 * @param setCookie {@code Set-Cookie} value carrying the recorded request
	 * @param returnUri where the end user re-enters once authenticated
	 */
	record AuthenticationRequired(String setCookie, URI returnUri) implements OidcAuthorizeResult {
	}

}
