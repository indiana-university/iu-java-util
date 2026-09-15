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
package edu.iu.oidc;

/**
 * The {@code act} claim: who is really behind a token issued for somebody else.
 *
 * <p>
 * Set only when a token exchange is honored, and then on both tokens. The two
 * carry different amounts of it: an access token names the actor and when they
 * authenticated, since a resource server needs to know an action was delegated
 * and not who the delegate is, while an ID token adds {@code name} and
 * {@code email} so a relying party can show its user whose session they are
 * looking through. Every claim here is the actor's own, never the subject's
 * &mdash; which is what RFC 8693 &sect;4.1 means by claims within {@code act}
 * pertaining only to the identity of the current actor.
 * </p>
 *
 * <p>
 * {@link #getAuthTime()} is here rather than beside the subject's own claims for
 * that reason. An exchanged token's subject never authenticated &mdash; a
 * caller named them, and this provider was willing to say so &mdash; so a
 * top-level {@code auth_time} would assert something untrue about them. The
 * actor did authenticate, and this is when.
 * </p>
 *
 * <p>
 * A bean, so a token builder renders it as the nested object RFC 8693 defines
 * rather than as a string. Optional claims are omitted from the rendering when
 * they answer {@code null}, which is what makes one interface serve both tokens.
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8693#section-4.1">RFC 8693
 *      &sect;4.1</a>
 */
public interface IuOidcActor {

	/**
	 * Gets the actor's own principal name.
	 *
	 * @return {@code sub} claim
	 */
	String getSub();

	/**
	 * Gets the actor's display name.
	 *
	 * @return {@code name} claim; null on an access token, and on an ID token when
	 *         the claims source holds none
	 */
	String getName();

	/**
	 * Gets the actor's email address.
	 *
	 * @return {@code email} claim; null on an access token, and on an ID token when
	 *         the claims source holds none
	 */
	String getEmail();

	/**
	 * Gets the time the actor authenticated.
	 *
	 * <p>
	 * Carried on both tokens, so a relying party has an authentication age to
	 * enforce a maximum against even though the subject of an exchanged token has
	 * no authentication of their own to measure.
	 * </p>
	 *
	 * @return {@code auth_time} claim, as a NumericDate &mdash; seconds since the
	 *         epoch, the same as {@code auth_time} is spelled anywhere else; null
	 *         when the exchanged token recorded none
	 */
	Long getAuthTime();

}
