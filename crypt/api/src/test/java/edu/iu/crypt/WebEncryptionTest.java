/*
 * Copyright © 2024 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class WebEncryptionTest extends IuCryptApiTestCase {

	@Test
	public void testEncryptionFrom() {
		for (final var enc : Encryption.values())
			assertSame(enc, Encryption.from(enc.enc));
	}

	@Test
	public void testBuilderDefaultEncryptFromText() {
		final var builder = mock(WebEncryption.Builder.class, CALLS_REAL_METHODS);
		final var text = IdGenerator.generateId();
		try (final var mockByteArrayInputStream = mockConstruction(ByteArrayInputStream.class, (a, ctx) -> {
			assertArrayEquals(IuText.utf8(text), (byte[]) ctx.arguments().get(0));
		})) {
			builder.encrypt(text);
			verify(builder).encrypt(IuText.utf8(text));
			verify(builder).encrypt(mockByteArrayInputStream.constructed().get(0));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testBuilderToEncryptionAndAlgorithm() {
		final var encryption = IuTest.rand(Encryption.class);
		final var algorithm = IuTest.rand(Algorithm.class);
		final var builder = mock(WebEncryption.Builder.class);
		final var recipient = mock(WebEncryptionRecipient.Builder.class);
		when(builder.compact()).thenReturn(builder);
		when(builder.addRecipient(algorithm)).thenReturn(recipient);
		try (final var mockWebEncryption = mockStatic(WebEncryption.class)) {
			mockWebEncryption.when(() -> WebEncryption.to(encryption, algorithm)).thenCallRealMethod();
			mockWebEncryption.when(() -> WebEncryption.builder(encryption, true)).thenReturn(builder);
			assertSame(recipient, WebEncryption.to(encryption, algorithm));
			verify(builder).compact();
		}
	}

	@Test
	public void testBuilderToEncryption() {
		final var encryption = IuTest.rand(Encryption.class);
		WebEncryption.builder(encryption);
		verify(Init.SPI).getJweBuilder(encryption, true);
	}
	
	@Test
	public void testParse() {
		final var jwe = IdGenerator.generateId();
		WebEncryption.parse(jwe);
		verify(Init.SPI).parseJwe(jwe);
	}
	
	@Test
	public void testDecryptText() {
		final var jwe = mock(WebEncryption.class, CALLS_REAL_METHODS);
		final var jwk = mock(WebKey.class);
		try (final var mockByteArrayOutputStream = mockConstruction(ByteArrayOutputStream.class)) {
			jwe.decryptText(jwk);
            verify(jwe).decrypt(jwk);
            verify(jwe).decrypt(jwk, mockByteArrayOutputStream.constructed().get(0));
		}
	}
	
}
