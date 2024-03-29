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
package iu.auth.util;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import edu.iu.IdGenerator;
import edu.iu.auth.IuPrincipalIdentity;

@SuppressWarnings("javadoc")
public class PrincipalVerifierRegistryTest {

	@Test
	public void testRegisterVerfier() {
		final var realm = IdGenerator.generateId();
		final var id = mock(IuPrincipalIdentity.class);
		assertThrows(NullPointerException.class, () -> IuPrincipalIdentity.verify(id, realm));

		PrincipalVerifierRegistry.registerVerifier(realm, a -> assertSame(a, id), true);
		assertThrows(IllegalStateException.class, () -> PrincipalVerifierRegistry.registerVerifier(realm, a -> {
		}, true));
		IuPrincipalIdentity.verify(id, realm);

		assertThrows(AssertionFailedError.class,
				() -> IuPrincipalIdentity.verify(mock(IuPrincipalIdentity.class), realm));
	}

	@Test
	public void testRegisterDelegate() {
		final var realm = IdGenerator.generateId();
		final var id = mock(IuPrincipalIdentity.class);
		assertThrows(NullPointerException.class, () -> IuPrincipalIdentity.verify(id, realm));

		class TestId implements IuPrincipalIdentity {
			private static final long serialVersionUID = 1L;

			@Override
			public String getName() {
				return id.getName();
			}
		}

		PrincipalVerifierRegistry.registerVerifier(realm, a -> assertSame(a, id), true);
		PrincipalVerifierRegistry.registerDelegate(TestId.class, a -> id);
		assertThrows(IllegalStateException.class,
				() -> PrincipalVerifierRegistry.registerDelegate(TestId.class, a -> id));
		IuPrincipalIdentity.verify(new TestId(), realm);

	}

}
