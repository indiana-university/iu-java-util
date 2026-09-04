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
import java.util.Set;
import java.util.function.Function;

import edu.iu.oidc.IuOidcAddress;
import edu.iu.oidc.IuOidcClaims;

/**
 * One end user's claims, as far as a grant's scope admits them.
 *
 * <p>
 * A view rather than a copy, and deny-by-default: every claim answers what the
 * source holds only when the scope covering it was granted, and {@code null}
 * otherwise. A source that volunteers more than a relying party may see does
 * not thereby disclose it, and since a consumer omits what answers
 * {@code null}, a suppressed claim doesn't appear as an empty one either.
 * </p>
 *
 * <p>
 * The four scopes are the ones OpenID Connect defines a claim set for, and each
 * admits exactly the claims it names there. A scope this doesn't know admits
 * nothing: a deployment with claims of its own carries them on an interface of
 * its own, and filters them itself.
 * </p>
 *
 * <p>
 * {@link #getSub()} is not filtered, and is not read from the source. It is the
 * principal the grant was issued for, which is what a relying party matches
 * against the ID token it holds, so a source's own notion of a principal name
 * never overrides it &mdash; and the {@code openid} scope, which is what makes
 * a request an OpenID Connect one at all, isn't consulted for it.
 * </p>
 *
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">OpenID
 *      Connect Core 1.0 &sect;5.4</a>
 */
final class OidcScopedClaims implements IuOidcClaims {

	/** Scope admitting the end user's name, locale, and the rest of the profile. */
	static final String PROFILE = "profile";

	/** Scope admitting the email address and whether it was verified. */
	static final String EMAIL = "email";

	/** Scope admitting the postal address. */
	static final String ADDRESS = "address";

	/** Scope admitting the telephone number and whether it was verified. */
	static final String PHONE = "phone";

	private final String sub;
	private final IuOidcClaims claims;
	private final boolean profile;
	private final boolean email;
	private final boolean address;
	private final boolean phone;

	/**
	 * Filters what a source holds down to what a grant's scope admits.
	 *
	 * @param sub    principal the grant was issued for, which
	 *               {@link #getSub()} answers whatever the source says
	 * @param claims claims the source holds; {@code null} when it holds none, which
	 *               leaves nothing but {@code sub} to answer
	 * @param scope  scope the grant was authorized for
	 */
	OidcScopedClaims(String sub, IuOidcClaims claims, Set<String> scope) {
		this.sub = sub;
		this.claims = claims;
		this.profile = scope.contains(PROFILE);
		this.email = scope.contains(EMAIL);
		this.address = scope.contains(ADDRESS);
		this.phone = scope.contains(PHONE);
	}

	/**
	 * Reads one claim, if the scope admitting it was granted and the source holds
	 * anything at all.
	 *
	 * <p>
	 * The single place either condition is checked, so a claim is admitted by
	 * naming the scope that covers it rather than by carrying its own guard.
	 * </p>
	 *
	 * @param <T>     claim type
	 * @param granted whether the scope covering this claim was granted
	 * @param read    reads the claim from the source
	 * @return the claim; {@code null} if the scope wasn't granted, or the source
	 *         holds nothing
	 */
	private <T> T scoped(boolean granted, Function<IuOidcClaims, T> read) {
		if (!granted //
				|| claims == null)
			return null;

		return read.apply(claims);
	}

	@Override
	public String getSub() {
		return sub;
	}

	@Override
	public String getName() {
		return scoped(profile, IuOidcClaims::getName);
	}

	@Override
	public String getGivenName() {
		return scoped(profile, IuOidcClaims::getGivenName);
	}

	@Override
	public String getFamilyName() {
		return scoped(profile, IuOidcClaims::getFamilyName);
	}

	@Override
	public String getMiddleName() {
		return scoped(profile, IuOidcClaims::getMiddleName);
	}

	@Override
	public String getNickname() {
		return scoped(profile, IuOidcClaims::getNickname);
	}

	@Override
	public String getPreferredUsername() {
		return scoped(profile, IuOidcClaims::getPreferredUsername);
	}

	@Override
	public URI getProfile() {
		return scoped(profile, IuOidcClaims::getProfile);
	}

	@Override
	public URI getPicture() {
		return scoped(profile, IuOidcClaims::getPicture);
	}

	@Override
	public URI getWebsite() {
		return scoped(profile, IuOidcClaims::getWebsite);
	}

	@Override
	public String getGender() {
		return scoped(profile, IuOidcClaims::getGender);
	}

	@Override
	public String getBirthdate() {
		return scoped(profile, IuOidcClaims::getBirthdate);
	}

	@Override
	public String getZoneinfo() {
		return scoped(profile, IuOidcClaims::getZoneinfo);
	}

	@Override
	public String getLocale() {
		return scoped(profile, IuOidcClaims::getLocale);
	}

	@Override
	public Instant getUpdatedAt() {
		return scoped(profile, IuOidcClaims::getUpdatedAt);
	}

	@Override
	public String getEmail() {
		return scoped(email, IuOidcClaims::getEmail);
	}

	@Override
	public Boolean getEmailVerified() {
		return scoped(email, IuOidcClaims::getEmailVerified);
	}

	@Override
	public IuOidcAddress getAddress() {
		return scoped(address, IuOidcClaims::getAddress);
	}

	@Override
	public String getPhoneNumber() {
		return scoped(phone, IuOidcClaims::getPhoneNumber);
	}

	@Override
	public Boolean getPhoneNumberVerified() {
		return scoped(phone, IuOidcClaims::getPhoneNumberVerified);
	}

}
