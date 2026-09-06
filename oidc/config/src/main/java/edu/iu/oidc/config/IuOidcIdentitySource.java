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

/**
 * Answers whether a principal holds an identity role.
 *
 * <p>
 * The seam between a provider endpoint and whatever decides entitlement. A
 * registration names the roles a client's endpoint admits, and a token endpoint
 * asks this whether the principal it is about to issue for holds one; where
 * those roles come from &mdash; a group service, a directory, a table &mdash;
 * is the deployment's and never reaches the provider.
 * </p>
 *
 * <p>
 * Separate from {@link IuOidcClaimsSource} deliberately. What a provider
 * publishes about someone and what it will let them through for are different
 * decisions, made against different data, and a deployment may well answer them
 * from different systems. Nothing stops one implementation from doing both.
 * </p>
 */
public interface IuOidcIdentitySource {

	/**
	 * Determines whether a principal holds at least one of the named roles.
	 *
	 * <p>
	 * A caller has already handled the two cases that need no lookup &mdash; a
	 * role naming everyone, and a role naming this principal by name &mdash; so
	 * what arrives here is roles an identity service has to be asked about, at
	 * least one of them, none of them {@code null}.
	 * </p>
	 *
	 * <p>
	 * Refusing is not the same as answering {@code false}: a principal name this
	 * source cannot resolve at all is reported by throwing, and a token endpoint
	 * answers that as a bad request rather than as a denial. Answering
	 * {@code false} says the principal is known and holds none of these roles.
	 * </p>
	 *
	 * @param principalName principal name to check
	 * @param roles         identity roles to check against; at least one, none
	 *                      {@code null}
	 * @return true if {@code principalName} holds at least one of {@code roles};
	 *         else false
	 * @throws RuntimeException if {@code principalName} names no principal this
	 *                          source can resolve
	 */
	boolean hasRole(String principalName, String... roles);

}
