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
package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import edu.iu.auth.config.AuthConfig;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;

@SuppressWarnings("javadoc")
public class JwtSpiTest {

	@AfterEach
	public void teardown() throws Exception {
		final var f = JwtSpi.class.getDeclaredField("sealed");
		f.setAccessible(true);
		f.set(null, false);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testIsSigningKey() {
		final var k = mock(WebKey.class);
		assertFalse(JwtSpi.isSigningKey(k));

		final var pk = mock(PrivateKey.class);
		when(k.getPrivateKey()).thenReturn(pk);
		assertFalse(JwtSpi.isSigningKey(k));

		final var ops = Set.of(Operation.SIGN);
		when(k.getOps()).thenReturn(ops, (Set<Operation>) null);
		assertTrue(JwtSpi.isSigningKey(k));
		assertFalse(JwtSpi.isSigningKey(k));

		when(k.getUse()).thenReturn(Use.SIGN);
		assertTrue(JwtSpi.isSigningKey(k));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testIsVerifyKey() {
		final var k = mock(WebKey.class);
		assertFalse(JwtSpi.isVerifyKey(k));

		final var pk = mock(PublicKey.class);
		when(k.getPublicKey()).thenReturn(pk);
		assertFalse(JwtSpi.isVerifyKey(k));

		final var ops = Set.of(Operation.VERIFY);
		when(k.getOps()).thenReturn(ops, (Set<Operation>) null);
		assertTrue(JwtSpi.isVerifyKey(k));
		assertFalse(JwtSpi.isVerifyKey(k));

		when(k.getUse()).thenReturn(Use.SIGN);
		assertTrue(JwtSpi.isVerifyKey(k));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testIsDecryptKey() {
		final var k = mock(WebKey.class);
		assertFalse(JwtSpi.isDecryptKey(k));

		final var pk = mock(PrivateKey.class);
		when(k.getPrivateKey()).thenReturn(pk);
		assertFalse(JwtSpi.isDecryptKey(k));

		final var ops = Set.of(Operation.UNWRAP);
		when(k.getOps()).thenReturn(ops, Set.of(Operation.DERIVE_KEY), (Set<Operation>) null);
		assertTrue(JwtSpi.isDecryptKey(k));
		assertTrue(JwtSpi.isDecryptKey(k));
		assertFalse(JwtSpi.isDecryptKey(k));

		when(k.getUse()).thenReturn(Use.ENCRYPT);
		assertTrue(JwtSpi.isDecryptKey(k));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testIsEncryptKey() {
		final var k = mock(WebKey.class);
		assertFalse(JwtSpi.isEncryptKey(k));

		final var pk = mock(PublicKey.class);
		when(k.getPublicKey()).thenReturn(pk);
		assertFalse(JwtSpi.isEncryptKey(k));

		final var ops = Set.of(Operation.WRAP);
		when(k.getOps()).thenReturn(ops, Set.of(Operation.DERIVE_KEY), (Set<Operation>) null);
		assertTrue(JwtSpi.isEncryptKey(k));
		assertTrue(JwtSpi.isEncryptKey(k));
		assertFalse(JwtSpi.isEncryptKey(k));

		when(k.getUse()).thenReturn(Use.ENCRYPT);
		assertTrue(JwtSpi.isEncryptKey(k));
	}

	@Test
	public void testGetSigningKey() {
		final var k = mock(WebKey.class);
		final var pk = mock(PrivateKey.class);
		when(k.getPrivateKey()).thenReturn(pk);
		final var bk = mock(PublicKey.class);
		when(k.getPublicKey()).thenReturn(bk);
		when(k.getUse()).thenReturn(Use.SIGN);
		final var id = new SimpleId(k);
		AuthConfig.register(new SimpleVerifier(id));
		AuthConfig.seal();

		assertSame(k, JwtSpi.getSigningKey(id));
		assertSame(k, JwtSpi.getVerifyKey(id));
	}

	@Test
	public void testGetVerifyKey() {
		final var k = mock(WebKey.class);
		final var pk = mock(PublicKey.class);
		when(k.getPublicKey()).thenReturn(pk);
		when(k.getUse()).thenReturn(Use.SIGN);
		final var id = new SimpleId(k);
		AuthConfig.register(new SimpleVerifier(id));
		AuthConfig.seal();

		assertNull(JwtSpi.getSigningKey(id));
		assertSame(k, JwtSpi.getVerifyKey(id));
	}

	@Test
	public void testGetDecryptKey() {
		final var k = mock(WebKey.class);
		final var pk = mock(PrivateKey.class);
		when(k.getPrivateKey()).thenReturn(pk);
		final var bk = mock(PublicKey.class);
		when(k.getPublicKey()).thenReturn(bk);
		when(k.getUse()).thenReturn(Use.ENCRYPT);
		final var id = new SimpleId(k);
		AuthConfig.register(new SimpleVerifier(id));
		AuthConfig.seal();

		assertSame(k, JwtSpi.getDecryptKey(id));
		assertSame(k, JwtSpi.getEncryptKey(id));
	}

	@Test
	public void testGetEncryptKey() {
		final var k = mock(WebKey.class);
		final var pk = mock(PublicKey.class);
		when(k.getPublicKey()).thenReturn(pk);
		when(k.getUse()).thenReturn(Use.ENCRYPT);
		final var id = new SimpleId(k);
		AuthConfig.register(new SimpleVerifier(id));
		AuthConfig.seal();

		assertSame(k, JwtSpi.getEncryptKey(id));
		assertNull(JwtSpi.getDecryptKey(id));
	}

	@Test
	public void testRegisterIssuer() {
		final var k = mock(WebKey.class);
		final var pk = mock(PublicKey.class);
		when(k.getPublicKey()).thenReturn(pk);
		when(k.getUse()).thenReturn(Use.SIGN);
		final var id = new SimpleId(k);
		AuthConfig.register(new SimpleVerifier(id));
		AuthConfig.seal();

		assertEquals(id, AuthConfig.<IuPrivateKeyPrincipal>get(id.getName()).getIdentity());
	}

}
