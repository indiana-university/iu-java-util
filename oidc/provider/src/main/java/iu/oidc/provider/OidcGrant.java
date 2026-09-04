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
import java.time.Instant;

/**
 * Authorization state bound to the end user's provider session.
 *
 * <p>
 * An authorization endpoint fills one of these in twice. The validated
 * authorization request goes in first &mdash; {@link #getClientId()},
 * {@link #getRedirectUri()}, {@link #getScope()}, {@link #getResource()},
 * {@link #getState()}, {@link #getNonce()}, and {@link #getCodeChallenge()}
 * &mdash; so the request survives the round trip to the identity provider
 * without riding on a return URI that provider would see. Once a principal is
 * established the rest is filled in and the user agent gets an authorization
 * code naming it.
 * </p>
 *
 * <p>
 * A set {@link #getClientId()} is what marks a session as carrying a request;
 * once a code is issued the detail is cleared, so the session no longer
 * resumes. A session handler proxies this interface over encrypted session
 * storage, so none of these values reach the user agent &mdash; only the
 * session cookie does.
 * </p>
 *
 * <p>
 * The completed grant is not what the authorization code names. It is signed,
 * encrypted, and handed to the token endpoint through {@link GrantStore}, which
 * the code is the decryption key for, so this interface is also the claim the
 * grant token carries. A token endpoint redeems that token, checking
 * {@link #getClientId()}, {@link #getRedirectUri()}, and
 * {@link #getCodeChallenge()} before issuing an ID token.
 * </p>
 */
public interface OidcGrant {

	/**
	 * Gets the principal name the identity provider authenticated.
	 *
	 * @return principal name
	 */
	String getPrincipalName();

	/**
	 * Sets the principal name the identity provider authenticated.
	 *
	 * @param principalName principal name
	 */
	void setPrincipalName(String principalName);

	/**
	 * Gets the principal name requested via the {@code impersonated_principal}
	 * request parameter.
	 *
	 * @return impersonated principal name, or {@code null} if the request named
	 *         none
	 */
	String getImpersonatedPrincipalName();

	/**
	 * Sets the principal name requested via the {@code impersonated_principal}
	 * request parameter.
	 *
	 * @param impersonatedPrincipalName impersonated principal name
	 */
	void setImpersonatedPrincipalName(String impersonatedPrincipalName);

	/**
	 * Gets the entity ID of the identity provider that authenticated the principal.
	 *
	 * @return authentication authority entity ID
	 */
	String getAuthnAuthority();

	/**
	 * Sets the entity ID of the identity provider that authenticated the principal.
	 *
	 * @param authnAuthority authentication authority entity ID
	 */
	void setAuthnAuthority(String authnAuthority);

	/**
	 * Gets the point in time the identity provider authenticated the principal,
	 * which an ID token reports as {@code auth_time}.
	 *
	 * @return {@link Instant}
	 */
	Instant getAuthnInstant();

	/**
	 * Sets the point in time the identity provider authenticated the principal.
	 *
	 * @param authnInstant {@link Instant}
	 */
	void setAuthnInstant(Instant authnInstant);

	/**
	 * Gets the {@code client_id} the code was issued to.
	 *
	 * @return client ID
	 */
	String getClientId();

	/**
	 * Sets the {@code client_id} the code was issued to.
	 *
	 * @param clientId client ID
	 */
	void setClientId(String clientId);

	/**
	 * Gets the {@code redirect_uri} the code was issued to, which a token endpoint
	 * must see repeated on redemption.
	 *
	 * @return redirect URI
	 */
	URI getRedirectUri();

	/**
	 * Sets the {@code redirect_uri} the code was issued to.
	 *
	 * @param redirectUri redirect URI
	 */
	void setRedirectUri(URI redirectUri);

	/**
	 * Gets the requested scope.
	 *
	 * @return space-delimited scope
	 */
	String getScope();

	/**
	 * Sets the requested scope.
	 *
	 * @param scope space-delimited scope
	 */
	void setScope(String scope);

	/**
	 * Gets the {@code resource} parameter values named when the code was issued.
	 *
	 * <p>
	 * Recorded so a token endpoint redeeming this grant &mdash; directly, or
	 * through a refresh token descending from it &mdash; can restrict the audience
	 * it addresses a token to at most what was named here, rather than trusting
	 * whatever {@code resource} the redemption request happens to carry. A request
	 * naming a subset narrows the audience further; one naming something not here
	 * is refused.
	 * </p>
	 *
	 * @return resource URIs requested; {@code null} or empty if the request named
	 *         none, which leaves the audience bounded by granted scope alone
	 */
	String[] getResource();

	/**
	 * Sets the {@code resource} parameter values named when the code was issued.
	 *
	 * @param resource resource URIs requested; {@code null} or empty if the request
	 *                 named none
	 */
	void setResource(String[] resource);

	/**
	 * Gets the {@code state} to echo on the authorization response.
	 *
	 * <p>
	 * Recorded rather than carried through the round trip, since the return URI the
	 * identity provider redirects to holds no parameters.
	 * </p>
	 *
	 * @return state, or {@code null} if the request had none
	 */
	String getState();

	/**
	 * Sets the {@code state} to echo on the authorization response.
	 *
	 * @param state state
	 */
	void setState(String state);

	/**
	 * Gets the {@code nonce} to echo in the ID token.
	 *
	 * @return nonce, or {@code null} if the request had none
	 */
	String getNonce();

	/**
	 * Sets the {@code nonce} to echo in the ID token.
	 *
	 * @param nonce nonce
	 */
	void setNonce(String nonce);

	/**
	 * Gets the PKCE {@code code_challenge}.
	 *
	 * @return code challenge, or {@code null} if the request had none
	 */
	String getCodeChallenge();

	/**
	 * Sets the PKCE {@code code_challenge}.
	 *
	 * @param codeChallenge code challenge
	 */
	void setCodeChallenge(String codeChallenge);

}
