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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import edu.iu.crypt.WebKey;

/**
 * Covers what {@link IuOidcClientAuthorization} derives, using implementations
 * that declare only the properties a registration record holds.
 */
@SuppressWarnings("javadoc")
public class IuOidcClientAuthorizationTest {

	/** Answers an authorization record over one client key. */
	private static IuOidcClientAuthorization authorization(WebKey jwk) {
		return new IuOidcClientAuthorization() {

			@Override
			public WebKey getJwk() {
				return jwk;
			}

			@Override
			public Instant getCreated() {
				return null;
			}

			@Override
			public Instant getUpdated() {
				return null;
			}

			@Override
			public Instant getExpires() {
				return null;
			}

			@Override
			public Iterable<X509CRL> getCrl() {
				return null;
			}
		};
	}

	private static WebKey jwkWithChain(X509Certificate... chain) {
		final var jwk = mock(WebKey.class);
		when(jwk.getCertificateChain()).thenReturn(chain);
		return jwk;
	}

	@Test
	void testAClientAssertionLivesFifteenMinutesByDefault() {
		// a leaked assertion is useful for minutes rather than indefinitely
		assertEquals(Duration.ofMinutes(15L), authorization(null).getAssertionTtl());
	}

	@Test
	void testNoKeyMeansNoCertificate() {
		assertNull(authorization(null).getCertificate());
	}

	@Test
	void testAKeyWithNoChainMeansNoCertificate() {
		assertNull(authorization(jwkWithChain((X509Certificate[]) null)).getCertificate());
	}

	@Test
	void testAKeyWithAnEmptyChainMeansNoCertificate() {
		assertNull(authorization(jwkWithChain()).getCertificate());
	}

	@Test
	void testTheSigningCertificateIsTheFirstInTheChain() {
		// the rest of the chain is what verifies it, not what signs
		final var signing = mock(X509Certificate.class);
		final var issuing = mock(X509Certificate.class);

		assertSame(signing, authorization(jwkWithChain(signing, issuing)).getCertificate());
	}

}
