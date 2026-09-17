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
	 * @param scope scopes to check for
	 * @return true if the claim includes at least one requested scope; else false
	 */
	boolean hasScope(Iterable<String> scope);

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
	 * @param role roles to check for
	 * @return true if the claim includes at least one requested role; else false
	 */
	boolean hasRole(Iterable<String> role);

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
