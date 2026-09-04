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
 * grant's scope admits, and how what it publishes is signed and encrypted.
 * </p>
 *
 * <p>
 * An implementation answers everything it knows about the principal and filters
 * nothing. The provider suppresses what a grant doesn't cover, and does so
 * deny-by-default, so a source that volunteers more than a relying party may
 * see does not thereby disclose it.
 * </p>
 */
public interface OidcClaimsSource {

	/**
	 * Gets the claims this source holds for one principal.
	 *
	 * @param principalName principal name, which the provider has already settled
	 *                      from a verified grant
	 * @return claims held for {@code principalName}; <em>should</em> answer the
	 *         principal name back as {@link IuOidcClaims#getSub() sub}, though a
	 *         provider binds that claim to the grant regardless
	 */
	IuOidcClaims claims(String principalName);

}
