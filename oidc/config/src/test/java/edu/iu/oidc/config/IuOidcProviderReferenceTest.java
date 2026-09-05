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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.crypt.WebKey;
import edu.iu.pki.IuCertificateAuthority;

@SuppressWarnings("javadoc")
public class IuOidcProviderReferenceTest {

	/** Binds the one resource with no default, leaving every other unbound. */
	private static IuOidcProviderReference reference() {
		final var configuration = mock(IuOidcProviderConfiguration.class);
		return () -> configuration;
	}

	@Test
	void testAnUnboundResourceRefusesAndSaysWhichOne() {
		// a missing binding fails where it is needed rather than answering something
		// plausible
		final var reference = reference();

		assertEquals("Missing client source",
				assertThrows(UnsupportedOperationException.class, reference::getClientSource).getMessage());
		assertEquals("Missing claims source",
				assertThrows(UnsupportedOperationException.class, reference::getClaimsSource).getMessage());
		assertEquals("Missing session handler",
				assertThrows(UnsupportedOperationException.class, reference::getSessionHandler).getMessage());
		assertEquals("Missing data store",
				assertThrows(UnsupportedOperationException.class, reference::getDataStore).getMessage());
		// which kind of verification a registration warrants is the provider's, so
		// each kind is bound, and refused, separately
		assertEquals("Missing certificate authority verifier",
				assertThrows(UnsupportedOperationException.class,
						() -> reference.getCertificateAuthorityVerifier(mock(IuCertificateAuthority.class)))
						.getMessage());
		assertEquals("Missing self-signed verifier", assertThrows(UnsupportedOperationException.class,
				() -> reference.getSelfSignedVerifier(mock(WebKey.class))).getMessage());
	}

	@Test
	void testNothingIsReleasedUntilSomethingSaysOtherwise() {
		assertNull(reference().getAuthorizationDetailsSource().authorize(List.of(() -> "record"), "someone"));
	}

	@Test
	void testNobodyIsAuthenticatedUntilSomethingSaysOtherwise() {
		assertNull(reference().getAuthenticatedPrincipal(mock(edu.iu.IuRequestAttributes.class)));
	}

	@Test
	void testADeploymentThatSaysNothingIsProduction() {
		// a forgotten binding closes the backdoor rather than opening it
		assertTrue(reference().isProduction());
	}

	@Test
	void testTheConfigurationIsWhatAnImplementationMustBind() {
		assertNull(reference().getConfiguration().getMetadata());
	}

}
