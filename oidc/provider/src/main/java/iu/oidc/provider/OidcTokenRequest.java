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

import edu.iu.IuRequestAttributes;

/**
 * A token request, as the transport that received it reports it.
 *
 * <p>
 * One accessor per parameter {@link OidcTokenEndpoint} reads, so a caller
 * adapting a servlet request, a JAX-RS request, or a test fixture never has to
 * know how OAuth 2.0 spells them. Every accessor answers {@code null} when the
 * request carried no such parameter; an adapter reports what arrived and
 * validates nothing.
 * </p>
 *
 * <p>
 * A token request is a form-encoded POST, so unlike an authorization request
 * there is no query string to read from. The one thing that isn't a form
 * parameter is {@link #getAuthorization() the Authorization header}, which is
 * where an HTTP Basic client credential arrives.
 * </p>
 *
 * <p>
 * {@link IuRequestAttributes} comes along because a refused request is logged
 * against the address it came from, which is the only thing that identifies a
 * caller whose credential didn't verify.
 * </p>
 */
public interface OidcTokenRequest extends IuRequestAttributes {

	/**
	 * Gets the {@code grant_type} parameter, which selects what the request is
	 * redeeming.
	 *
	 * @return {@code grant_type}; null if the request named none
	 */
	String getGrantType();

	/**
	 * Gets the {@code client_id} parameter.
	 *
	 * <p>
	 * Not the only way a request names its client: a Basic credential names one in
	 * its username and an assertion in its issuer. Where both are present they must
	 * agree.
	 * </p>
	 *
	 * @return {@code client_id}; null if the request named none
	 */
	String getClientId();

	/**
	 * Gets the {@code client_secret} parameter, which is how a client presents a
	 * secret in the request body rather than as a Basic credential.
	 *
	 * @return {@code client_secret}; null if the request named none
	 */
	String getClientSecret();

	/**
	 * Gets the {@code client_assertion} parameter.
	 *
	 * @return {@code client_assertion}; null if the request named none
	 */
	String getClientAssertion();

	/**
	 * Gets the {@code client_assertion_type} parameter, which must name the JWT
	 * bearer assertion type when an assertion is presented.
	 *
	 * @return {@code client_assertion_type}; null if the request named none
	 */
	String getClientAssertionType();

	/**
	 * Gets the issuer {@link #getClientAssertion() the presented assertion} claims,
	 * before anything has verified it.
	 *
	 * <p>
	 * An assertion names its client in its own {@code iss} claim, so a request
	 * presenting one need not also name {@code client_id}. Reading that claim means
	 * decoding the assertion's payload as JSON, which an adapter's transport
	 * already does &mdash; the same reason
	 * {@link OidcAuthorizeRequest#getAuthorizationDetails() authorization details}
	 * arrive parsed rather than as a document.
	 * </p>
	 *
	 * <p>
	 * <em>Unverified.</em> It selects which registration the assertion is then
	 * verified against and nothing else, so a request naming a registration its
	 * assertion cannot satisfy fails at verification rather than here. An adapter
	 * answers {@code null} for an assertion it cannot decode or one claiming no
	 * issuer, both of which the endpoint refuses as a malformed assertion; it does
	 * not need to validate the claim beyond reading it.
	 * </p>
	 *
	 * @return {@code iss} the assertion claims; null if no assertion was presented,
	 *         or it cannot be decoded, or it claims no issuer
	 */
	String getClientAssertionIssuer();

	/**
	 * Gets the {@code Authorization} request header, verbatim.
	 *
	 * <p>
	 * Reported as sent rather than decoded, since the endpoint has to tell an
	 * unsupported scheme from a malformed credential and both from none at all. A
	 * transport that decodes it here would collapse those.
	 * </p>
	 *
	 * @return {@code Authorization} header; null if the request carried none
	 */
	String getAuthorization();

	/**
	 * Gets the {@code redirect_uri} parameter, which selects the client endpoint
	 * the request is made through and, for an authorization code, must repeat the
	 * URI the code was issued to.
	 *
	 * @return {@code redirect_uri}; null if the request named none
	 */
	String getRedirectUri();

	/**
	 * Gets the {@code resource} parameter values.
	 *
	 * <p>
	 * RFC 8707 allows the parameter to repeat, one value per resource the client
	 * wants the token usable at, so this reports every value rather than one.
	 * </p>
	 *
	 * @return {@code resource} values in the order sent; null if the request named
	 *         none
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc8707#section-2">RFC 8707
	 *      &sect;2</a>
	 */
	Iterable<String> getResource();

	/**
	 * Gets the {@code scope} parameter, space-delimited as sent.
	 *
	 * <p>
	 * Read only for {@code client_credentials}. A code or refresh grant carries the
	 * scope it was authorized with, and a redemption request doesn't get to widen
	 * or narrow it.
	 * </p>
	 *
	 * @return {@code scope}; null if the request named none
	 */
	String getScope();

	/**
	 * Gets the {@code code} parameter, naming the authorization code being
	 * redeemed.
	 *
	 * @return {@code code}; null if the request named none
	 */
	String getCode();

	/**
	 * Gets the PKCE {@code code_verifier} parameter.
	 *
	 * @return {@code code_verifier}; null if the request named none
	 */
	String getCodeVerifier();

	/**
	 * Gets the {@code refresh_token} parameter, naming the refresh token being
	 * redeemed.
	 *
	 * @return {@code refresh_token}; null if the request named none
	 */
	String getRefreshToken();

}
