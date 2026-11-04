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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

@SuppressWarnings("javadoc")
public class OidcPrincipalTest {

	private static final Object[][] STANDARD_CLAIMS = { { "getName", "name", String.class },
			{ "getGivenName", "given_name", String.class }, { "getFamilyName", "family_name", String.class },
			{ "getMiddleName", "middle_name", String.class }, { "getNickname", "nickname", String.class },
			{ "getPreferredUsername", "preferred_username", String.class }, { "getProfile", "profile", URI.class },
			{ "getPicture", "picture", URI.class }, { "getWebsite", "website", URI.class },
			{ "getEmail", "email", String.class }, { "getEmailVerified", "email_verified", Boolean.class },
			{ "getGender", "gender", String.class }, { "getBirthdate", "birthdate", String.class },
			{ "getZoneinfo", "zoneinfo", String.class }, { "getLocale", "locale", String.class },
			{ "getPhoneNumber", "phone_number", String.class },
			{ "getPhoneNumberVerified", "phone_number_verified", Boolean.class },
			{ "getAddress", "address", IuOidcAddress.class }, { "getUpdatedAt", "updated_at", Instant.class } };

	@Test
	void testIsNotAClaimSet() {
		assertFalse(IuOidcClaims.class.isAssignableFrom(IuOidcPrincipal.class));
	}

	@Test
	void testEveryClaimAccessorIsWiredToTheDefaultView() {
		// the default getOidcClaims() view overrides each accessor by hand, so a claim
		// added to the interface without one would silently answer null forever. This
		// is what makes the sweep above a complete check rather than a sample
		final Set<String> swept = new LinkedHashSet<>();
		for (final var standardClaim : STANDARD_CLAIMS)
			swept.add((String) standardClaim[0]);

		final Set<String> declared = new LinkedHashSet<>();
		for (final var method : IuOidcClaims.class.getMethods())
			if (method.getParameterCount() == 0 //
					&& !"toString".equals(method.getName()) //
					&& !Object.class.equals(method.getDeclaringClass()))
				declared.add(method.getName());

		assertEquals(declared, swept);
	}

	@Test
	void testPreservesPrincipalNameContract() throws Exception {
		assertEquals(Principal.class, IuOidcPrincipal.class.getMethod("getName").getDeclaringClass());
	}

	@Test
	void testStandardClaimAccessorsDelegateToGetClaim() throws Exception {
		final var principal = mock(IuOidcPrincipal.class, CALLS_REAL_METHODS);

		final var claims = principal.getOidcClaims();
		for (final var standardClaim : STANDARD_CLAIMS) {
			final var accessor = (String) standardClaim[0];
			final var claimName = (String) standardClaim[1];
			final var claimType = (Class<?>) standardClaim[2];
			final var value = valueFor(claimType);
			doReturn(value).when(principal).getClaim(claimName, claimType);

			final Method method = IuOidcClaims.class.getMethod(accessor);
			assertSame(value, method.invoke(claims), accessor);
			verify(principal).getClaim(claimName, claimType);
		}
	}

	@Test
	void testTheActorIsReadFromTheActClaim() {
		final var principal = mock(IuOidcPrincipal.class, CALLS_REAL_METHODS);
		final var actor = mock(IuOidcActor.class);
		doReturn(actor).when(principal).getClaim("act", IuOidcActor.class);

		assertSame(actor, principal.getActor());
	}

	@Test
	void testAnUndelegatedTokenHasNoActor() {
		// null rather than an empty view, so "nobody is acting" does not read the same
		// as "somebody is, and this token says nothing about them"
		final var principal = mock(IuOidcPrincipal.class, CALLS_REAL_METHODS);
		doReturn(null).when(principal).getClaim("act", IuOidcActor.class);

		assertNull(principal.getActor());
	}

	@Test
	void testAcrIsReadFromThePrincipalRatherThanItsClaims() {
		// it describes the authentication, not the end user, so it is not one of the
		// claims a source asserts about somebody
		assertFalse(hasNoArgMethod(IuOidcClaims.class, "getAcr"));
		assertTrue(hasNoArgMethod(IuOidcPrincipal.class, "getAcr"));

		final var principal = mock(IuOidcPrincipal.class, CALLS_REAL_METHODS);
		doReturn("https://idp.iu.edu").when(principal).getClaim("acr", String.class);

		assertEquals("https://idp.iu.edu", principal.getAcr());
	}

	@Test
	void testAnActorCarriesItsOwnAcr() throws Exception {
		// a delegated token's subject never authenticated, so the authority belongs to
		// the actor and is read from the actor rather than the top level
		assertTrue(hasNoArgMethod(IuOidcActor.class, "getAcr"));

		// named the same way anybody else is, rather than restating name claims
		assertTrue(IuOidcNameClaims.class.isAssignableFrom(IuOidcActor.class));

		// one type for the act claim, read and written: a second "claims" view would
		// only differ in whether accessors default, which a JSON proxy does not read
		assertSame(IuOidcActor.class, IuOidcPrincipal.class.getMethod("getActor").getReturnType());
	}

	private static boolean hasNoArgMethod(Class<?> type, String name) {
		for (final var method : type.getMethods())
			if (name.equals(method.getName()) //
					&& method.getParameterCount() == 0)
				return true;

		return false;
	}

	@Test
	void testScopeAndRoleDenyByDefault() {
		// a principal implementation compiled before these methods existed answers
		// deny-by-default rather than failing with AbstractMethodError
		final var principal = mock(IuOidcPrincipal.class, CALLS_REAL_METHODS);

		assertFalse(principal.hasScope(List.of("openid")));
		assertFalse(principal.hasRole(List.of("admin")));
	}

	@Test
	void testScopeAndRoleVarargsDelegateToIterable() {
		final var principal = mock(IuOidcPrincipal.class, CALLS_REAL_METHODS);
		doReturn(true).when(principal).hasScope(ArgumentMatchers.<Iterable<String>>any());
		doReturn(true).when(principal).hasRole(ArgumentMatchers.<Iterable<String>>any());

		assertTrue(principal.hasScope("openid", "profile"));
		assertTrue(principal.hasRole("user", "admin"));
		verify(principal).hasScope(ArgumentMatchers.<Iterable<String>>any());
		verify(principal).hasRole(ArgumentMatchers.<Iterable<String>>any());
	}

	private static Object valueFor(Class<?> type) {
		if (type == String.class)
			return "value";
		if (type == Boolean.class)
			return Boolean.TRUE;
		if (type == URI.class)
			return URI.create("https://example.test");
		if (type == Instant.class)
			return Instant.EPOCH;
		return mock(type);
	}

}
