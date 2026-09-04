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
 * An authorization request, as the transport that received it reports it.
 *
 * <p>
 * One accessor per parameter {@link OidcAuthorizeEndpoint} reads, so a caller
 * adapting a servlet request, a JAX-RS request, or a test fixture never has to
 * know how OAuth 2.0 spells them. Every accessor answers {@code null} when the
 * request carried no such parameter; an adapter reports what arrived and
 * validates nothing.
 * </p>
 *
 * <p>
 * OpenID Connect permits the parameters to arrive either in the query string or
 * as a form-encoded body, and this says nothing about which: an adapter reads
 * both the way its transport does.
 * </p>
 *
 * <p>
 * {@link IuRequestAttributes} comes along because the endpoint needs the
 * request's cookies to find the session a request is resumed from, and its
 * remote address to say something useful about a request that resumes nothing.
 * </p>
 */
public interface OidcAuthorizeRequest extends IuRequestAttributes {

	/**
	 * Gets the {@code client_id} parameter.
	 *
	 * <p>
	 * Its absence is what marks a request as the second pass of one already
	 * validated: the endpoint sends the end user to an identity provider and back
	 * to a bare URI carrying no parameters, so a request without a client ID is one
	 * to resume from the session rather than one to validate.
	 * </p>
	 *
	 * @return {@code client_id}; null on a resumption
	 */
	String getClientId();

	/**
	 * Gets the {@code redirect_uri} parameter, which selects the client endpoint
	 * the request is made through and must match a registered value exactly.
	 *
	 * @return {@code redirect_uri}; null if the request named none
	 */
	String getRedirectUri();

	/**
	 * Gets the {@code response_type} parameter.
	 *
	 * @return {@code response_type}; null if the request named none
	 */
	String getResponseType();

	/**
	 * Gets the {@code scope} parameter, space-delimited as sent.
	 *
	 * @return {@code scope}; null if the request named none
	 */
	String getScope();

	/**
	 * Gets the {@code resource} parameter values.
	 *
	 * <p>
	 * RFC 8707 allows the parameter to repeat, one value per resource the client
	 * wants the eventual token usable at, so this reports every value rather than
	 * one. An adapter answering {@code null} says the request named the parameter
	 * not at all, which the endpoint reads differently from naming it emptily.
	 * </p>
	 *
	 * @return {@code resource} values in the order sent; null if the request named
	 *         none
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc8707#section-2">RFC 8707
	 *      &sect;2</a>
	 */
	Iterable<String> getResource();

	/**
	 * Gets the {@code state} parameter, echoed on whatever response this request
	 * eventually gets.
	 *
	 * @return {@code state}; null if the request named none
	 */
	String getState();

	/**
	 * Gets the {@code nonce} parameter, echoed in the ID token.
	 *
	 * @return {@code nonce}; null if the request named none
	 */
	String getNonce();

	/**
	 * Gets the PKCE {@code code_challenge} parameter.
	 *
	 * @return {@code code_challenge}; null if the request named none
	 */
	String getCodeChallenge();

	/**
	 * Gets the PKCE {@code code_challenge_method} parameter.
	 *
	 * @return {@code code_challenge_method}; null if the request named none
	 */
	String getCodeChallengeMethod();

	/**
	 * Gets the {@code impersonated_principal} parameter, naming a principal the end
	 * user is asking to act as.
	 *
	 * <p>
	 * Recorded on the grant whatever the deployment is, and read at redemption:
	 * whether a request may act on it depends on roles the authenticated principal
	 * holds and on whether the deployment is a production one, neither of which is
	 * settled here.
	 * </p>
	 *
	 * @return {@code impersonated_principal}; null if the request named none
	 */
	String getImpersonatedPrincipal();

	/**
	 * Gets the {@code delegating_principal} parameter, naming the principal the end
	 * user expects to have been delegated access by.
	 * 
	 * <p>
	 * This parameter is an authorization server hint facilitating preselection for
	 * users with multiple delegated authorization records.
	 * </p>
	 * 
	 * @return delegating user principal name
	 */
	String getDelegatingPrincipal();

}
