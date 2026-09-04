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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.oidc.IuOidcAddress;
import edu.iu.oidc.IuOidcClaims;

@SuppressWarnings("javadoc")
public class OidcScopedClaimsTest {

	private static final String SUB = "someone";
	private static final Instant UPDATED = Instant.now().minusSeconds(3600L);
	private static final IuOidcAddress ADDRESS = mock(IuOidcAddress.class);

	private static final Set<String> EVERY_SCOPE = Set.of(OidcScopedClaims.PROFILE, OidcScopedClaims.EMAIL,
			OidcScopedClaims.ADDRESS, OidcScopedClaims.PHONE);

	/** Holds every standard claim, so anything suppressed is visibly suppressed. */
	private static IuOidcClaims everything() {
		final var claims = mock(IuOidcClaims.class);
		when(claims.getSub()).thenReturn("some-other-sub");
		when(claims.getName()).thenReturn("Some One");
		when(claims.getGivenName()).thenReturn("Some");
		when(claims.getFamilyName()).thenReturn("One");
		when(claims.getMiddleName()).thenReturn("O");
		when(claims.getNickname()).thenReturn("Somebody");
		when(claims.getPreferredUsername()).thenReturn("someone");
		when(claims.getProfile()).thenReturn(URI.create("https://example.iu.edu/someone"));
		when(claims.getPicture()).thenReturn(URI.create("https://example.iu.edu/someone.png"));
		when(claims.getWebsite()).thenReturn(URI.create("https://someone.example.iu.edu"));
		when(claims.getGender()).thenReturn("female");
		when(claims.getBirthdate()).thenReturn("1990-01-01");
		when(claims.getZoneinfo()).thenReturn("America/Indiana/Indianapolis");
		when(claims.getLocale()).thenReturn("en-US");
		when(claims.getUpdatedAt()).thenReturn(UPDATED);
		when(claims.getEmail()).thenReturn("someone@iu.edu");
		when(claims.getEmailVerified()).thenReturn(true);
		when(claims.getAddress()).thenReturn(ADDRESS);
		when(claims.getPhoneNumber()).thenReturn("+18128560000");
		when(claims.getPhoneNumberVerified()).thenReturn(false);
		return claims;
	}

	/** Names the claims a scoped view overrides, less the unfiltered sub. */
	private static List<Method> filtered() {
		final List<Method> filtered = new ArrayList<>();
		for (final var method : IuOidcClaims.class.getDeclaredMethods())
			if (Modifier.isPublic(method.getModifiers()) //
					&& !Modifier.isStatic(method.getModifiers()) //
					&& !method.isSynthetic() //
					&& method.getParameterCount() == 0 //
					&& !"getSub".equals(method.getName()))
				filtered.add(method);
		return filtered;
	}

	@Test
	void testTheSubjectComesFromTheGrantNotTheSource() {
		// a relying party matches it against the ID token it holds, so a source that
		// resolves a principal name to some other form must not break the comparison
		assertEquals(SUB, new OidcScopedClaims(SUB, everything(), EVERY_SCOPE).getSub());
	}

	@Test
	void testASourceHoldingNothingLeavesNothingButTheSubject() throws Exception {
		final var claims = new OidcScopedClaims(SUB, null, EVERY_SCOPE);
		assertEquals(SUB, claims.getSub());

		for (final var claim : filtered())
			assertNull(claim.invoke(claims), claim::getName);
	}

	@Test
	void testEveryScopeAdmitsEveryClaim() {
		final var source = everything();
		final var claims = new OidcScopedClaims(SUB, source, EVERY_SCOPE);

		assertEquals("Some One", claims.getName());
		assertEquals("Some", claims.getGivenName());
		assertEquals("One", claims.getFamilyName());
		assertEquals("O", claims.getMiddleName());
		assertEquals("Somebody", claims.getNickname());
		assertEquals("someone", claims.getPreferredUsername());
		assertEquals(URI.create("https://example.iu.edu/someone"), claims.getProfile());
		assertEquals(URI.create("https://example.iu.edu/someone.png"), claims.getPicture());
		assertEquals(URI.create("https://someone.example.iu.edu"), claims.getWebsite());
		assertEquals("female", claims.getGender());
		assertEquals("1990-01-01", claims.getBirthdate());
		assertEquals("America/Indiana/Indianapolis", claims.getZoneinfo());
		assertEquals("en-US", claims.getLocale());
		assertEquals(UPDATED, claims.getUpdatedAt());
		assertEquals("someone@iu.edu", claims.getEmail());
		assertEquals(true, claims.getEmailVerified());
		assertSame(ADDRESS, claims.getAddress());
		assertEquals("+18128560000", claims.getPhoneNumber());
		assertEquals(false, claims.getPhoneNumberVerified());
	}

	@Test
	void testNoScopeAdmitsNothing() throws Exception {
		// deny-by-default: a source that volunteers more than a relying party may see
		// does not thereby disclose it
		final var claims = new OidcScopedClaims(SUB, everything(), Set.of());

		for (final var claim : filtered())
			assertNull(claim.invoke(claims), claim::getName);
	}

	@Test
	void testAnUnrecognizedScopeAdmitsNothing() throws Exception {
		final var claims = new OidcScopedClaims(SUB, everything(), Set.of("openid", "offline_access", "read"));

		for (final var claim : filtered())
			assertNull(claim.invoke(claims), claim::getName);
	}

	/** Answers the claims admitted by one scope alone. */
	private static List<String> admittedBy(String scope) throws Exception {
		final var claims = new OidcScopedClaims(SUB, everything(), Set.of(scope));

		final List<String> admitted = new ArrayList<>();
		for (final var claim : filtered())
			if (claim.invoke(claims) != null)
				admitted.add(claim.getName());

		return admitted;
	}

	@Test
	void testProfileAdmitsTheProfileClaims() throws Exception {
		final var admitted = admittedBy(OidcScopedClaims.PROFILE);
		assertEquals(14, admitted.size(), admitted::toString);
		assertTrue(admitted.containsAll(List.of("getName", "getGivenName", "getFamilyName", "getMiddleName",
				"getNickname", "getPreferredUsername", "getProfile", "getPicture", "getWebsite", "getGender",
				"getBirthdate", "getZoneinfo", "getLocale", "getUpdatedAt")), admitted::toString);
	}

	@Test
	void testEmailAdmitsTheAddressAndWhetherItWasVerified() throws Exception {
		assertEquals(List.of("getEmail", "getEmailVerified"), sorted(admittedBy(OidcScopedClaims.EMAIL)));
	}

	@Test
	void testAddressAdmitsThePostalAddress() throws Exception {
		assertEquals(List.of("getAddress"), admittedBy(OidcScopedClaims.ADDRESS));
	}

	@Test
	void testPhoneAdmitsTheNumberAndWhetherItWasVerified() throws Exception {
		// phone_number_verified is false rather than null here, so it counts as
		// admitted: a provider that asserts nothing answers null instead
		assertEquals(List.of("getPhoneNumber", "getPhoneNumberVerified"), sorted(admittedBy(OidcScopedClaims.PHONE)));
	}

	private static List<String> sorted(List<String> names) {
		final var sorted = new ArrayList<>(names);
		sorted.sort(null);
		return sorted;
	}

}
