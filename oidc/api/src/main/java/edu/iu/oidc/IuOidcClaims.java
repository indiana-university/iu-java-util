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

import java.net.URI;
import java.time.Instant;

/**
 * The claims an OpenID Provider may assert about an end user.
 *
 * <p>
 * One typed accessor per claim OpenID Connect &sect;5.1 defines, so what a
 * source knows is stated in Java rather than assembled as a document. A claim's
 * Java type is chosen for what it means rather than for how it prints.
 * </p>
 *
 * <h2>Who reads this</h2>
 *
 * <p>
 * An implementation does. A provider does not: it names the claims a grant
 * admits and leaves the claims source to write them onto a token or render them
 * as a document, so nothing in it ever calls an accessor here. What this states is the shape those have to produce &mdash;
 * which claim a property is, and what type it serializes as.
 * </p>
 *
 * <p>
 * Property names convert to claim names directly under lower case with
 * underscores, so {@link #getPreferredUsername()} is {@code preferred_username}
 * and {@link #getEmailVerified()} is {@code email_verified}. Two claims need a
 * consumer's attention, since a general-purpose converter renders neither the
 * way OpenID Connect requires: {@link #getUpdatedAt() updated_at} is a
 * <em>NumericDate</em>, seconds since the epoch as a JSON number rather than a
 * formatted timestamp, and {@link #getAddress() address} is a nested object
 * rather than a string.
 * </p>
 *
 * <p>
 * A deployment with claims of its own declares an interface extending this one
 * and renders that type; nothing here is an escape hatch for untyped values.
 * Claims declared that way are released the same way these are, under a scope
 * OpenID Connect doesn't define.
 * </p>
 *
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
 *      Connect Core 1.0 &sect;5.1</a>
 */
public interface IuOidcClaims extends IuOidcNameClaims {

	/**
	 * Gets the shorthand name the end user wishes to be referred to by.
	 *
	 * @return {@code preferred_username} claim; null if not known
	 */
	default String getPreferredUsername() {
		return null;
	}

	/**
	 * Gets the URL of the end user's profile page.
	 *
	 * @return {@code profile} claim; null if not known
	 */
	default URI getProfile() {
		return null;
	}

	/**
	 * Gets the URL of the end user's profile picture, which refers to an image file
	 * rather than to a page containing one.
	 *
	 * @return {@code picture} claim; null if not known
	 */
	default URI getPicture() {
		return null;
	}

	/**
	 * Gets the URL of a web page or blog the end user or their organization
	 * publishes.
	 *
	 * @return {@code website} claim; null if not known
	 */
	default URI getWebsite() {
		return null;
	}

	/**
	 * Gets the end user's preferred email address.
	 *
	 * <p>
	 * Not guaranteed unique or stable, and {@link #getEmailVerified() unverified}
	 * unless separately asserted, so a relying party must not key anything by it.
	 * </p>
	 *
	 * @return {@code email} claim; null if not known
	 */
	default String getEmail() {
		return null;
	}

	/**
	 * Reports whether the provider has verified that the end user controls
	 * {@link #getEmail() their email address}.
	 *
	 * <p>
	 * A {@link Boolean} rather than a {@code boolean}: a provider that has taken no
	 * position answers {@code null}, which is not the same as answering that the
	 * address is unverified.
	 * </p>
	 *
	 * @return {@code email_verified} claim; null if the provider asserts nothing
	 */
	default Boolean getEmailVerified() {
		return null;
	}

	/**
	 * Gets the end user's gender, for which OpenID Connect defines {@code female}
	 * and {@code male} and leaves any other value to the deployment.
	 *
	 * @return {@code gender} claim; null if not known
	 */
	default String getGender() {
		return null;
	}

	/**
	 * Gets the end user's birthday as {@code YYYY-MM-DD}, or as {@code YYYY} when
	 * only the year is known.
	 *
	 * @return {@code birthdate} claim; null if not known
	 */
	default String getBirthdate() {
		return null;
	}

	/**
	 * Gets the end user's time zone as an IANA zone name, such as
	 * {@code America/Indiana/Indianapolis}.
	 *
	 * @return {@code zoneinfo} claim; null if not known
	 */
	default String getZoneinfo() {
		return null;
	}

	/**
	 * Gets the end user's locale as a BCP 47 language tag, such as {@code en-US}.
	 *
	 * @return {@code locale} claim; null if not known
	 */
	default String getLocale() {
		return null;
	}

	/**
	 * Gets the end user's preferred telephone number, in E.164 form where possible.
	 *
	 * @return {@code phone_number} claim; null if not known
	 */
	default String getPhoneNumber() {
		return null;
	}

	/**
	 * Reports whether the provider has verified that the end user controls
	 * {@link #getPhoneNumber() their telephone number}.
	 *
	 * @return {@code phone_number_verified} claim; null if the provider asserts
	 *         nothing
	 */
	default Boolean getPhoneNumberVerified() {
		return null;
	}

	/**
	 * Gets the end user's preferred postal address.
	 *
	 * @return {@code address} claim; null if not known
	 */
	default IuOidcAddress getAddress() {
		return null;
	}

	/**
	 * Gets when the end user's information was last updated.
	 *
	 * <p>
	 * A <em>NumericDate</em>: whatever renders these claims must publish it as
	 * seconds since the epoch, as a JSON number, rather than as a formatted
	 * timestamp.
	 * </p>
	 *
	 * @return {@code updated_at} claim; null if not known
	 */
	default Instant getUpdatedAt() {
		return null;
	}

	/**
	 * Renders these claims as the JSON object an unsigned UserInfo response
	 * publishes.
	 *
	 * <p>
	 * The document <em>must</em> name the principal the source was asked about as
	 * {@code sub}, and must carry nothing the provider did not admit. A relying
	 * party matches {@code sub} against the ID token it holds and refuses the
	 * response when the two disagree; the provider does not check, because it does
	 * not parse what it publishes.
	 * </p>
	 *
	 * <p>
	 * It carries neither {@code iss} nor {@code aud}: a plain document has nothing
	 * to lift out of a response and replay elsewhere, so OpenID Connect &sect;5.3.2
	 * requires neither. A signed response does require both, and is not rendered
	 * here at all &mdash; it is a JWT, built claim by claim like any other token,
	 * with the provider writing both itself.
	 * </p>
	 *
	 * <p>
	 * Declared here to state the obligation, not to enforce it: an interface
	 * redeclaring a method of {@link Object} does not oblige an implementation to
	 * override it, so a type that leaves this alone compiles and publishes its
	 * identity hash as a claims document.
	 * </p>
	 *
	 * @return claims rendered as a JSON object
	 */
	@Override
	String toString();

}
