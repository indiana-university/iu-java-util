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

import edu.iu.jwt.WebTokenBuilder;
import edu.iu.oidc.IuOidcClaims;

/**
 * Supplies the claims an OpenID Provider asserts about one end user.
 *
 * <p>
 * The seam between a provider endpoint and whatever holds identity data. A
 * deployment implements this over its own directory, database, or attribute
 * service; the endpoint asks it for a principal and never learns where the
 * answer came from. That keeps an identity service free of OpenID Connect
 * &mdash; it neither verifies tokens nor knows which relying party is asking
 * &mdash; while the provider keeps the parts that are its own: which of the
 * claims OpenID Connect defines a grant's scope admits, which token they are
 * being written to, and how what it publishes is signed and encrypted.
 * </p>
 *
 * <h2>Disclosure is decided in two halves</h2>
 *
 * <p>
 * The claims OpenID Connect &sect;5.4 binds to {@code profile}, {@code email},
 * {@code address} and {@code phone} are fixed by specification, so the provider
 * maps those itself and an implementation never reasons about them. Every other
 * scope is the deployment's own, and so are the claims it releases &mdash;
 * {@link #admitted(Set, Usage) admitted} is where that half is decided, and it
 * sees only the scopes &sect;5.4 does not cover.
 * </p>
 *
 * <p>
 * A scope a deployment defines only reaches here if some
 * {@link IuOidcClientResource#getScope() client resource} registers it. An
 * authorization request asking for a scope no resource grants is refused as
 * {@code invalid_scope} before any of this is consulted.
 * </p>
 */
public interface IuOidcClaimsSource {

	/**
	 * Names where the claims a source releases are about to be published.
	 *
	 * <p>
	 * The same principal and the same scope may warrant different disclosure
	 * depending on who ends up holding the result. A UserInfo response is read by
	 * the relying party that asked for it; an ID token is a durable assertion the
	 * client keeps; an access token is presented to resource servers the end user
	 * never sees. A source that draws no distinction answers the same set for all
	 * of them.
	 * </p>
	 */
	enum Usage {

		/**
		 * Claims bound for an RFC 9068 access token, which a relying party presents to
		 * resource servers rather than reading itself.
		 *
		 * <p>
		 * No endpoint asks for this today: an access token this provider issues carries
		 * no claims about the end user beyond the subject and the granted scope, and
		 * the {@code act} claim naming an impersonator deliberately omits their name and
		 * email. It is named here so that a source's answer does not have to change
		 * shape if that ever stops being true.
		 * </p>
		 */
		ACCESS_TOKEN,

		/**
		 * Claims bound for an ID token, which the relying party that requested it keeps
		 * and may retain past the life of the access token issued alongside it.
		 */
		ID_TOKEN,

		/**
		 * Claims bound for a UserInfo response, which is fetched on demand and is the
		 * widest disclosure this provider makes.
		 */
		USERINFO;

	}

	/**
	 * Names the &sect;5.1.2 Additional Claims this source releases for scopes
	 * OpenID Connect does not define.
	 *
	 * <p>
	 * Only the deployment's own scopes arrive here; the sets &sect;5.4 binds to
	 * {@code profile}, {@code email}, {@code address} and {@code phone} are the
	 * provider's to map and are added to whatever this answers. A scope this
	 * source doesn't know releases nothing, the same way an unrecognized
	 * &sect;5.4 scope does.
	 * </p>
	 *
	 * <p>
	 * Whatever is named here is passed back as {@code admittedClaims} on the next
	 * call, so a name answered for one {@link Usage} and withheld for another is how
	 * a source keeps a claim out of a durable ID token while still publishing it
	 * from UserInfo.
	 * </p>
	 *
	 * @param scope scopes the grant was authorized for, less those OpenID Connect
	 *              &sect;5.4 defines a claim set for; empty if it asked for none of
	 *              the deployment's own
	 * @param usage where the claims named here are about to be published
	 * @return claim names this source releases for {@code scope} at
	 *         {@code usage}; empty, never {@code null}, to release nothing
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#AdditionalClaims">OpenID
	 *      Connect Core 1.0 &sect;5.1.2</a>
	 */
	Set<String> admitted(Set<String> scope, Usage usage);

	/**
	 * Writes the claims this source holds for one principal onto a token this
	 * provider is issuing.
	 *
	 * <p>
	 * A token carries typed claims rather than a rendered document, so this writes
	 * rather than returning: {@link WebTokenBuilder#claim(String, Object,
	 * java.lang.reflect.Type)} takes each value with the type it is to serialize
	 * as, which is what keeps {@code updated_at} a NumericDate and {@code address}
	 * a nested object without the provider having any opinion about either.
	 * {@link IuOidcClaims} names what those types are for the claims OpenID Connect
	 * defines; this source is what applies them.
	 * </p>
	 *
	 * <p>
	 * Write nothing but {@code admittedClaims}. A claim named there and not held is
	 * simply omitted, and a claim held but not named is withheld &mdash; the set is
	 * what the grant's scope admits, so writing past it discloses what was never
	 * authorized. The registered claims a token is identified by, and those the
	 * provider derives for itself, are written after this returns and are not a
	 * source's to set.
	 * </p>
	 *
	 * @param principalName  principal name the provider has settled from a verified
	 *                       grant, and the {@code sub} the token already names
	 * @param admittedClaims names of the claims to write; empty to write none
	 * @param builder        token being issued
	 */
	void claims(String principalName, Set<String> admittedClaims, WebTokenBuilder builder);

	/**
	 * Gets the claims this source holds for one principal, rendered as the document
	 * an unsigned UserInfo response publishes.
	 *
	 * <p>
	 * The answer is what a provider publishes, so {@link Object#toString()
	 * toString()} <em>must</em> render it as that claims document &mdash; a
	 * deployment configures the rendering for itself, and the provider publishes
	 * the result without looking at it. {@link IuOidcClaims} is what states the
	 * shape it has to render to.
	 * </p>
	 *
	 * <p>
	 * Only an unsigned UserInfo response reaches here. A signed one is a JWT, and
	 * is built through {@link #claims(String, Set, WebTokenBuilder)} like any other
	 * token this provider issues &mdash; which is also where the {@code iss} and
	 * {@code aud} OpenID Connect &sect;5.3.2 requires a signed response to carry
	 * come from, written by the provider rather than rendered by this source. A
	 * plain document has nothing to lift out of a response and carries neither.
	 * </p>
	 *
	 * @param principalName  principal name the provider has settled from a verified
	 *                       grant, and the {@code sub} the rendered document must
	 *                       name. The provider does not read it back to check: a
	 *                       source that resolves a principal name to some other
	 *                       form &mdash; a username for a numeric ID, say &mdash;
	 *                       breaks the comparison a relying party makes against the
	 *                       ID token it holds
	 * @param admittedClaims names of the claims the grant admits: the &sect;5.4
	 *                       sets its scope covers, {@code sub} among them when
	 *                       {@code openid} was granted, plus whatever
	 *                       {@link #admitted(Set, Usage)} named for
	 *                       {@link Usage#USERINFO}. Anything else this source holds
	 *                       is not to be answered
	 * @return claims held for {@code principalName}, limited to
	 *         {@code admittedClaims}, rendering as a claims document
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#UserInfoResponse">OpenID
	 *      Connect Core 1.0 &sect;5.3.2</a>
	 */
	IuOidcClaims claims(String principalName, Set<String> admittedClaims);

}
