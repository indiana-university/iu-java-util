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
package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class OidcTokenIssuerIdentityTest {

	@Test
	void testNulls() {
		final var op = mock(IuOpenIdProviderMetadata.class, a -> null);
		final var issuer = new OidcTokenIssuer(op);
		assertNull(issuer.getAlg());
		assertNull(issuer.getEnc());
		assertNull(issuer.getEncryptAlg());
	}

	@Test
	void testAlgAndJwk() {
		final var op = mock(IuOpenIdProviderMetadata.class);
		final var alg = IuTest.rand(Algorithm.class);
		when(op.getIdTokenSigningAlgValuesSupported()).thenReturn(Set.of(alg));
		final var encryptAlg = IuTest.rand(Algorithm.class);
		when(op.getIdTokenEncryptionAlgValuesSupported()).thenReturn(Set.of(encryptAlg));
		final var enc = IuTest.rand(Encryption.class);
		when(op.getIdTokenEncryptionEncValuesSupported()).thenReturn(Set.of(enc));

		final var issuer = new OidcTokenIssuer(op);
		assertEquals(alg, issuer.getAlg());
		assertEquals(encryptAlg, issuer.getEncryptAlg());
		assertEquals(enc, issuer.getEnc());
		
		final var jwk = mock(WebKey.class);
		when(jwk.getUse()).thenReturn(Use.SIGN);

		final var jwks = URI.create(IdGenerator.generateId());
		when(op.getJwksUri()).thenReturn(jwks);

		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.readJwks(jwks)).thenReturn(List.of(jwk));
			assertEquals(jwk, issuer.getJwk());
		}
	}

}
