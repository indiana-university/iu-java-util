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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

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
