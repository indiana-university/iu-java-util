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
package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class WebTokenTest extends IuCryptApiTestCase {

	@Test
	public void testBuilder() {
		WebToken.builder();
		verify(Init.SPI).getJwtBuilder();
	}

	@Test
	public void testIsEncrypted() {
		final var token = IdGenerator.generateId();
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.ECDH_ES);
		try (final var mockWebCryptoHeader = mockStatic(WebCryptoHeader.class)) {
			mockWebCryptoHeader.when(() -> WebCryptoHeader.getProtectedHeader(token)).thenReturn(header);
			assertTrue(WebToken.isEncrypted(token));
		}
	}

	@Test
	public void testIsNotEncrypted() {
		final var token = IdGenerator.generateId();
		final var header = mock(WebCryptoHeader.class);
		when(header.getAlgorithm()).thenReturn(Algorithm.ES256);
		try (final var mockWebCryptoHeader = mockStatic(WebCryptoHeader.class)) {
			mockWebCryptoHeader.when(() -> WebCryptoHeader.getProtectedHeader(token)).thenReturn(header);
			assertFalse(WebToken.isEncrypted(token));
		}
	}

	@Test
	public void testDecryptAndVerify() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		final var audienceKey = mock(WebKey.class);
		WebToken.decryptAndVerify(jwt, issuerKey, audienceKey);
		verify(Init.SPI).decryptAndVerifyJwt(jwt, issuerKey, audienceKey);
	}

	@Test
	public void testVerify() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		WebToken.verify(jwt, issuerKey);
		verify(Init.SPI).verifyJwt(jwt, issuerKey);
	}

}
