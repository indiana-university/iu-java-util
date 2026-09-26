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
 * Names the claims a grant's scope admits, and separates the scopes OpenID
 * Connect defines from a deployment's own.
 *
 * <p>
 * Deny-by-default, and the whole of what the provider decides about disclosure:
 * a scope this doesn't know admits nothing, so a claims source asked for the
 * result of this can answer everything it was asked for without reasoning about
 * who is asking. {@code sub} is what {@code openid} admits, so a grant that
 * never asked for an ID token doesn't get the subject named back at it either.
 * </p>
 *
 * <p>
 * Two of the six scopes named here bind no claims. {@link #OPENID} and
 * {@link #OFFLINE_ACCESS} shape what a grant is answered with &mdash; an ID
 * token, a refresh token &mdash; rather than naming a claim set, and
 * {@code openid} admitting {@code sub} is incidental to that. The other four
 * are the &sect;5.4 claim sets and nothing else.
 * </p>
 *
 * <p>
 * A deployment with claims of its own maps its own scopes to them, through
 * {@link edu.iu.oidc.config.IuOidcClaimsSource#admitted(Set, edu.iu.oidc.config.IuOidcClaimsSource.Usage)
 * admitted()}; {@link #additional(Set)} is what names the scopes that reaches
 * with. The split is the point: the &sect;5.4 half is fixed by specification and
 * is the same wherever the claims are published, while the deployment's half is
 * its own and may differ by destination.
 * </p>
 *
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">OpenID
 *      Connect Core 1.0 &sect;5.4</a>
 */
final class OidcClaimScopes {

	/** The scope that asks for an ID token. */
	static final String OPENID = "openid";

	/** The scope that asks for a refresh token. */
	static final String OFFLINE_ACCESS = "offline_access";

	/** The scope that asks for the end user's profile claims. */
	static final String PROFILE = "profile";

	/** The scope that asks for the end user's email address. */
	static final String EMAIL = "email";

	/** The scope that asks for the end user's postal address. */
	static final String ADDRESS = "address";

	/** The scope that asks for the end user's phone number. */
	static final String PHONE = "phone";

	/**
	 * Every scope OpenID Connect defines, which is every scope a deployment does
	 * not get to decide the meaning of.
	 */
	private static final Set<String> OIDC_SCOPES = Set.of(OPENID, OFFLINE_ACCESS, PROFILE, EMAIL, ADDRESS, PHONE);

	/** What each scope OpenID Connect defines a claim set for admits. */
	private static final Map<String, List<String>> ADMITTED = Map.of( //
			OPENID, List.of("sub"), //
			PROFILE,
			List.of("name", "family_name", "given_name", "middle_name", "nickname", "preferred_username", "profile",
					"picture", "website", "gender", "birthdate", "zoneinfo", "locale", "updated_at"), //
			EMAIL, List.of("email", "email_verified"), //
			ADDRESS, List.of("address"), //
			PHONE, List.of("phone_number", "phone_number_verified"));

	/**
	 * Names the claims one grant's scope admits, of those OpenID Connect defines.
	 *
	 * <p>
	 * Takes the whole granted scope and ignores what it doesn't recognize, since
	 * the &sect;5.4 mapping is the same wherever the claims end up. What a
	 * deployment's own scopes admit is {@link #additional(Set) asked of the claims
	 * source} and added to this.
	 * </p>
	 *
	 * @param scope scope the grant was authorized for
	 * @return claim names admitted; empty if the scope names none OpenID Connect
	 *         binds a claim to, {@code openid} included
	 */
	static Set<String> admitted(Set<String> scope) {
		final Set<String> admitted = new LinkedHashSet<>();

		for (final var requested : scope) {
			final var claims = ADMITTED.get(requested);
			if (claims != null)
				admitted.addAll(claims);
		}

		return Collections.unmodifiableSet(admitted);
	}

	/**
	 * Names the scopes in a grant that OpenID Connect does not define, which are
	 * the deployment's own to answer for.
	 *
	 * @param scope scope the grant was authorized for
	 * @return scopes OpenID Connect defines no meaning for; empty if the grant
	 *         asked for none of the deployment's own
	 */
	static Set<String> additional(Set<String> scope) {
		final Set<String> additional = new LinkedHashSet<>();

		for (final var requested : scope)
			if (!OIDC_SCOPES.contains(requested))
				additional.add(requested);

		return Collections.unmodifiableSet(additional);
	}

	private OidcClaimScopes() {
	}

}
