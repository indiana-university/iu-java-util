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
package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;

@SuppressWarnings("javadoc")
public class OidcPrincipalVerifierTest extends IuOidcTestCase {

	@Test
	public void testVerify() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var verifier = new OidcPrincipalVerifier(true, realm);
		assertTrue(verifier.isAuthoritative());
		assertEquals(realm, verifier.getRealm());
		assertEquals(OidcPrincipal.class, verifier.getType());

		final var id = mock(OidcPrincipal.class);
		when(id.realm()).thenReturn(realm);
		verifier.verify(id, realm);
	}

	@Test
	public void testWrongRealm() {
		final var realm = IdGenerator.generateId();
		final var verifier = new OidcPrincipalVerifier(true, realm);
		assertTrue(verifier.isAuthoritative());
		assertEquals(realm, verifier.getRealm());
		assertEquals(OidcPrincipal.class, verifier.getType());

		final var id = mock(OidcPrincipal.class);
		when(id.realm()).thenReturn(IdGenerator.generateId());
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(id, realm));
	}

//	@Test
//	public void testRevoked() throws IuAuthenticationException {
//		final var realm = IdGenerator.generateId();
//		final var verifier = new OidcPrincipalVerifier(true, realm);
//		assertTrue(verifier.isAuthoritative());
//		assertEquals(realm, verifier.getRealm());
//		assertEquals(OidcPrincipal.class, verifier.getType());
//
//		final var id = mock(OidcPrincipal.class);
//		when(id.realm()).thenReturn(realm);
//		when(id.revoked()).thenReturn(true);
//		assertThrows(IllegalStateException.class, () -> verifier.verify(id, realm));
//	}

}