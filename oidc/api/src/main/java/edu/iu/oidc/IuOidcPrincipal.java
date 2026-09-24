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

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;

import edu.iu.IuIterable;
import edu.iu.jwt.IuAuthorizationDetails;
import edu.iu.jwt.WebToken;

/**
 * Client application view of principal user identity established via
 * interaction with an OIDC provider.
 *
 * <p>
 * The OpenID Connect claims associated with the principal are available through
 * {@link #getOidcClaims()}.
 * </p>
 */
public interface IuOidcPrincipal extends Principal {

	/**
	 * Gets the OpenID Connect claims associated with this principal.
	 *
	 * <p>
	 * The default view resolves every standard claim through
	 * {@link #getClaim(String, Class)}. Implementations may override this method to
	 * return a richer claim view.
	 * </p>
	 *
	 * @return claims associated with this principal
	 */
	default IuOidcClaims getOidcClaims() {
		return new IuOidcClaims() {
			@Override
			public String getName() {
				return getClaim("name", String.class);
			}

			@Override
			public String getGivenName() {
				return getClaim("given_name", String.class);
			}

			@Override
			public String getFamilyName() {
				return getClaim("family_name", String.class);
			}

			@Override
			public String getMiddleName() {
				return getClaim("middle_name", String.class);
			}

			@Override
			public String getNickname() {
				return getClaim("nickname", String.class);
			}

			@Override
			public String getPreferredUsername() {
				return getClaim("preferred_username", String.class);
			}

			@Override
			public URI getProfile() {
				return getClaim("profile", URI.class);
			}

			@Override
			public URI getPicture() {
				return getClaim("picture", URI.class);
			}

			@Override
			public URI getWebsite() {
				return getClaim("website", URI.class);
			}

			@Override
			public String getEmail() {
				return getClaim("email", String.class);
			}

			@Override
			public Boolean getEmailVerified() {
				return getClaim("email_verified", Boolean.class);
			}

			@Override
			public String getGender() {
				return getClaim("gender", String.class);
			}

			@Override
			public String getBirthdate() {
				return getClaim("birthdate", String.class);
			}

			@Override
			public String getZoneinfo() {
				return getClaim("zoneinfo", String.class);
			}

			@Override
			public String getLocale() {
				return getClaim("locale", String.class);
			}

			@Override
			public String getPhoneNumber() {
				return getClaim("phone_number", String.class);
			}

			@Override
			public Boolean getPhoneNumberVerified() {
				return getClaim("phone_number_verified", Boolean.class);
			}

			@Override
			public IuOidcAddress getAddress() {
				return getClaim("address", IuOidcAddress.class);
			}

			@Override
			public Instant getUpdatedAt() {
				return getClaim("updated_at", Instant.class);
			}
		};
	}

	/**
	 * Gets the authentication context class this principal's authentication
	 * satisfied.
	 *
	 * <h4>Its absence is the claim</h4>
	 *
	 * <p>
	 * OpenID Connect &sect;2 leaves {@code acr} values to agreement between the
	 * parties using them; IU's provider writes the authenticating authority's
	 * unique identifier &mdash; a federated SAML identity provider's entity ID,
	 * where a deployment authenticates that way.
	 * </p>
	 *
	 * <p>
	 * Nothing constrains a {@code client_id} from reading like a principal name, so
	 * {@link #getName()} alone does not say whether a token answers for an end user
	 * or for a client (RFC 9700 &sect;4.15). This does: a token whose subject
	 * authenticated names who authenticated them, and one that answers for the
	 * client itself has no such authority to name. Three readings cover every token
	 * a provider issues &mdash; this claim present is an end user; absent with
	 * {@link #getActor()} is a delegation, where the authority belongs to the actor
	 * and is read through {@link IuOidcActor#getAcr()}; absent with no actor
	 * is the client itself.
	 * </p>
	 *
	 * <p>
	 * Declared here rather than on {@link IuOidcClaims}, which states what a claims
	 * source may assert <em>about an end user</em>. This describes the
	 * authentication instead, and a provider writes it from what it established
	 * rather than asking a source for it.
	 * </p>
	 *
	 * @return {@code acr} claim; null when this principal's subject did not itself
	 *         authenticate
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID
	 *      Connect Core 1.0 &sect;2</a>
	 */
	default String getAcr() {
		return getClaim("acr", String.class);
	}

	/**
	 * Gets the claims describing the actor a delegated token names, when somebody
	 * is acting for this principal.
	 *
	 * <p>
	 * RFC 8693 &sect;4.1 puts the acting party's identity in the {@code act} claim
	 * and confines that object to claims about the actor alone, so what comes back
	 * here describes whoever obtained the token &mdash; never
	 * {@link #getOidcClaims() the subject it answers for}. A relying party reads it
	 * to show its user whose session they are looking through, and reads
	 * {@link IuOidcActor#getAcr()} to learn who authenticated them.
	 * </p>
	 *
	 * <p>
	 * <strong>Null is the ordinary case.</strong> Most tokens are not delegated,
	 * and answering an empty claims view would make "nobody is acting" read the
	 * same as "somebody is acting and this token says nothing about them".
	 * </p>
	 *
	 * @return claims describing the actor; null when this token is not delegated
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc8693#section-4.1">RFC 8693
	 *      &sect;4.1</a>
	 */
	default IuOidcActor getActor() {
		return getClaim("act", IuOidcActor.class);
	}

	/**
	 * Updated session cookie, populated if a state change was required while
	 * resolving the principal.
	 *
	 * @return Set-Cookie header value
	 */
	String getSetCookie();

	/**
	 * Gets the verified ID token for this principal.
	 * 
	 * @return {@link WebToken} ID token
	 */
	WebToken getIdToken();

	/**
	 * Determines whether a scope was granted to this principal.
	 *
	 * <p>
	 * An implementation checks the space-delimited {@code scope} claim from the
	 * token it verified: an ID token for a client principal, or an access token for
	 * an API bearer principal. Scope names are case-sensitive.
	 * </p>
	 * 
	 * @param scope scopes to check for
	 * @return true if the claim includes at least one requested scope; else false
	 */
	default boolean hasScope(String... scope) {
		return hasScope(IuIterable.iter(scope));
	}

	/**
	 * Determines whether a scope was granted to this principal.
	 *
	 * <p>
	 * An implementation checks the space-delimited {@code scope} claim from the
	 * token it verified: an ID token for a client principal, or an access token for
	 * an API bearer principal. Scope names are case-sensitive.
	 * </p>
	 *
	 * <p>
	 * The default denies every scope, so a principal implementation predating this
	 * method compiles and answers deny-by-default rather than failing with
	 * {@link AbstractMethodError}; override it to check an actual claim.
	 * </p>
	 *
	 * @param scope scopes to check for
	 * @return true if the claim includes at least one requested scope; else false
	 */
	default boolean hasScope(Iterable<String> scope) {
		return false;
	}

	/**
	 * Determines whether this principal has an asserted role.
	 *
	 * <p>
	 * An implementation checks the {@code roles} claim from the token it verified:
	 * an ID token for a client principal, or an access token for an API bearer
	 * principal. Role names compare without regard to case.
	 * </p>
	 * 
	 * @param role roles to check for
	 * @return true if the claim includes at least one requested role; else false
	 */
	default boolean hasRole(String... role) {
		return hasRole(IuIterable.iter(role));
	}

	/**
	 * Determines whether this principal has an asserted role.
	 *
	 * <p>
	 * An implementation checks the {@code roles} claim from the token it verified:
	 * an ID token for a client principal, or an access token for an API bearer
	 * principal. Role names compare without regard to case.
	 * </p>
	 *
	 * <p>
	 * The default denies every role, so a principal implementation predating this
	 * method compiles and answers deny-by-default rather than failing with
	 * {@link AbstractMethodError}; override it to check an actual claim.
	 * </p>
	 *
	 * @param role roles to check for
	 * @return true if the claim includes at least one requested role; else false
	 */
	default boolean hasRole(Iterable<String> role) {
		return false;
	}

	/**
	 * Gets a claim value.
	 * 
	 * @param <T>  claim value type
	 * @param name claim name
	 * @param type claim type
	 * @return claim value, from the ID token when present; else from UserInfo when
	 *         available
	 */
	<T> T getClaim(String name, Class<T> type);

	/**
	 * Gets the authorization details released by the authorization server.
	 *
	 * <p>
	 * The token response's {@code authorization_details} value is authoritative
	 * when present, including when it is empty. When the response omits that value,
	 * this method reads the matching entries from the verified ID token.
	 * </p>
	 * 
	 * @param <T>             authorization details interface type
	 * @param detailInterface authorization details interface class used to decode
	 *                        matching entries
	 * @param type            {@code authorization_details} type property value to
	 *                        match
	 * @return released authorization details matching {@code type}
	 */
	<T extends IuAuthorizationDetails> Iterable<T> getAuthorizationDetails(Class<T> detailInterface, String type);

	/**
	 * Gets an access token issued to this principal for use with a given remote
	 * resource.
	 *
	 * <p>
	 * May require an on-behalf-of exchange with the OpenID Provider's token
	 * endpoint.
	 * </p>
	 *
	 * @param resourceUri root resource URI for the API to get an access token for
	 * @return access token for use at the indicated resource URI
	 * @throws IOException if communication with an upstream provider is interrupted
	 */
	String getAccessToken(URI resourceUri) throws IOException;

}
