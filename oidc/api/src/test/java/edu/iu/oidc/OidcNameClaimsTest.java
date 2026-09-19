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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class OidcNameClaimsTest {

	/** Names the claims an interface declares a default for. */
	private static List<Method> defaults(Class<?> claimType) {
		final List<Method> defaults = new ArrayList<>();
		for (final var method : claimType.getDeclaredMethods())
			if (method.isDefault())
				defaults.add(method);
		return defaults;
	}

	/**
	 * Asserts that every claim a type defaults answers null, so an implementation
	 * declares only what its source holds and a consumer omits the rest.
	 */
	private static void assertEveryClaimDefaultsToNull(Class<?> claimType) throws Exception {
		final var claims = mock(claimType, CALLS_REAL_METHODS);

		final var defaults = defaults(claimType);
		assertTrue(defaults.size() > 1, claimType::getName);

		for (final var claim : defaults)
			assertNull(claim.invoke(claims), claim::getName);
	}

	@Test
	void testEveryClaimIsOptional() throws Exception {
		assertEveryClaimDefaultsToNull(IuOidcNameClaims.class);
	}

	@Test
	void testRenderingTheDocumentIsTheImplementationsToDo() throws Exception {
		// declared rather than defaulted, since only an implementation knows how its
		// claims print and a provider publishes the result without parsing it
		final var toString = IuOidcClaims.class.getDeclaredMethod("toString");
		assertFalse(toString.isDefault(), "toString");
		assertTrue(Modifier.isAbstract(toString.getModifiers()), "toString");

		// but redeclaring a method of Object obliges nobody: this compiles, and
		// answers an identity hash where a claims document was meant to be
		assertNotNull(new IuOidcNameClaims() {
		}.toString());
	}

}
