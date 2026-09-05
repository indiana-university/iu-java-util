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

import java.util.Set;

import edu.iu.oidc.IuOidcClaims;

/**
 * Supplies the standard claims an OpenID Provider asserts about one end user.
 *
 * <p>
 * The seam between a provider endpoint and whatever holds identity data. A
 * deployment implements this over its own directory, database, or attribute
 * service; the endpoint asks it for a principal and never learns where the
 * answer came from. That keeps an identity service free of OpenID Connect
 * &mdash; it neither verifies tokens nor knows which relying party is asking
 * &mdash; while the provider keeps the parts that are its own: which claims a
 * grant's scope admits, whether what came back is who was asked about, and how
 * what it publishes is signed and encrypted.
 * </p>
 *
 * <h2>Who decides what is withheld</h2>
 *
 * <p>
 * Not this. A provider works out which claims a grant's scope admits &mdash;
 * from the sets OpenID Connect defines, deny-by-default &mdash; and names them.
 * An implementation answers those and no others, and never has to reason about
 * scope or about which relying party is asking. A source that holds more than
 * was named simply doesn't volunteer it.
 * </p>
 */
public interface OidcClaimsSource {

	/**
	 * Gets the claims this source holds for one principal, limited to those the
	 * provider named.
	 *
	 * <p>
	 * The answer is what a provider publishes, so
	 * {@link Object#toString() toString()} <em>must</em> render it as the claims
	 * document a UserInfo response carries &mdash; a deployment configures that
	 * rendering for itself, and the provider signs and encrypts the result without
	 * looking at it.
	 * </p>
	 *
	 * <p>
	 * {@link IuOidcClaims#getSub() sub} <em>must</em> answer {@code principalName}
	 * back. A relying party matches it against the ID token it holds and refuses
	 * the response when the two disagree, so a source that resolves a principal
	 * name to some other form &mdash; a username for a numeric ID, say &mdash;
	 * would break that comparison. A provider checks rather than trusting, and
	 * refuses to publish claims about anyone else.
	 * </p>
	 *
	 * @param principalName  principal name, which the provider has already settled
	 *                       from a verified grant, and which is also the
	 *                       {@code sub} the answer must carry
	 * @param admittedClaims names of the claims the grant admits, {@code sub}
	 *                       among them; anything else this source holds is not to
	 *                       be answered
	 * @return claims held for {@code principalName}, limited to
	 *         {@code admittedClaims}, rendering as a claims document
	 */
	IuOidcClaims claims(String principalName, Set<String> admittedClaims);

}
