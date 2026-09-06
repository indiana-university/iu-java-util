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

/**
 * The {@code act} claim: who is really behind a token issued for somebody else.
 *
 * <p>
 * Set only when an impersonation request is honored, and then on both tokens.
 * The two carry different amounts of it: an access token names the actor and
 * nothing more, since a resource server needs to know an action was delegated
 * and not who the delegate is, while an ID token adds {@code name} and
 * {@code email} so a relying party can show its user whose session they are
 * looking through. Both are the actor's own claims, never the subject's.
 * </p>
 *
 * <p>
 * A bean, so a token builder renders it as the nested object RFC 8693 defines
 * rather than as a string. The two optional claims are omitted from the
 * rendering when they answer {@code null}, which is what makes one interface
 * serve both tokens.
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8693#section-4.1">RFC 8693
 *      &sect;4.1</a>
 */
public interface OidcActor {

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

}
