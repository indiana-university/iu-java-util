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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Names the claims a grant's scope admits.
 *
 * <p>
 * Deny-by-default, and the whole of what the provider decides about
 * disclosure: a scope this doesn't know admits nothing, so a claims source
 * asked for the result of this can answer everything it was asked for without
 * reasoning about who is asking. {@code sub} is admitted whatever the scope,
 * since a relying party has to be able to match it against the ID token it
 * holds.
 * </p>
 *
 * <p>
 * A deployment with claims of its own maps its own scopes to them; this covers
 * the four sets OpenID Connect defines and nothing else.
 * </p>
 *
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">OpenID
 *      Connect Core 1.0 &sect;5.4</a>
 */
final class OidcClaimScopes {

	/** Claim admitted whatever the scope. */
	static final String SUB = "sub";

	/** What each scope OpenID Connect defines a claim set for admits. */
	private static final Map<String, List<String>> ADMITTED = Map.of( //
			"profile",
			List.of("name", "family_name", "given_name", "middle_name", "nickname", "preferred_username", "profile",
					"picture", "website", "gender", "birthdate", "zoneinfo", "locale", "updated_at"), //
			"email", List.of("email", "email_verified"), //
			"address", List.of("address"), //
			"phone", List.of("phone_number", "phone_number_verified"));

	/**
	 * Names the claims one grant's scope admits.
	 *
	 * @param scope scope the grant was authorized for
	 * @return claim names admitted, always including {@link #SUB}
	 */
	static Set<String> admitted(Set<String> scope) {
		final Set<String> admitted = new LinkedHashSet<>();
		admitted.add(SUB);

		for (final var requested : scope) {
			final var claims = ADMITTED.get(requested);
			if (claims != null)
				admitted.addAll(claims);
		}

		return Collections.unmodifiableSet(admitted);
	}

	private OidcClaimScopes() {
	}

}
