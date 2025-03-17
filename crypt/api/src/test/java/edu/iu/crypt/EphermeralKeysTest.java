/*
 * Copyright © 2025 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.NamedParameterSpec;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class EphermeralKeysTest {

	@Test
	public void testRand() {
		final var rand = mock(SecureRandom.class);
		try (final var mockSecureRandom = mockStatic(SecureRandom.class)) {
			mockSecureRandom.when(() -> SecureRandom.getInstanceStrong()).thenReturn(rand);
			final var b = EphemeralKeys.rand(16);
			assertEquals(16, b.length);
			verify(rand).nextBytes(b);
		}
	}

	@Test
	public void testCek() {
		assertEquals(16, EphemeralKeys.contentEncryptionKey(128).length);
		assertEquals(32, EphemeralKeys.contentEncryptionKey("HmacSHA256", 256).length);
	}

	@Test
	public void testIllegalEc() {
		final var spec = mock(AlgorithmParameterSpec.class);
		assertThrows(IllegalArgumentException.class, () -> EphemeralKeys.ec(spec));
	}

	@Test
	public void testInvalidEcName() {
		assertThrows(IllegalArgumentException.class, () -> EphemeralKeys.ec(new NamedParameterSpec("invalid")));
	}

	@Test
	public void testEcNamedKeyPair() {
		final var keyPairGenerator = mock(KeyPairGenerator.class);
		try (final var mockKeyPairGenerator = mockStatic(KeyPairGenerator.class)) {
			mockKeyPairGenerator.when(() -> KeyPairGenerator.getInstance("X448")).thenReturn(keyPairGenerator);
			EphemeralKeys.ec(new NamedParameterSpec("X448"));
			verify(keyPairGenerator).generateKeyPair();
		}
	}

	@Test
	public void testEcParamKeyPair() {
		final var keyPairGenerator = mock(KeyPairGenerator.class);
		final var ecParamSpec = mock(ECParameterSpec.class);
		try (final var mockKeyPairGenerator = mockStatic(KeyPairGenerator.class)) {
			mockKeyPairGenerator.when(() -> KeyPairGenerator.getInstance("EC")).thenReturn(keyPairGenerator);
			EphemeralKeys.ec(ecParamSpec);
			assertDoesNotThrow(() -> verify(keyPairGenerator).initialize(ecParamSpec));
			verify(keyPairGenerator).generateKeyPair();
		}
	}

	@Test
	public void testRSAKeyPair() {
		final var keyPairGenerator = mock(KeyPairGenerator.class);
		try (final var mockKeyPairGenerator = mockStatic(KeyPairGenerator.class)) {
			mockKeyPairGenerator.when(() -> KeyPairGenerator.getInstance("RSA")).thenReturn(keyPairGenerator);
			EphemeralKeys.rsa("RSA", 2048);
			assertDoesNotThrow(() -> verify(keyPairGenerator).initialize(2048));
			verify(keyPairGenerator).generateKeyPair();
		}
	}

}
