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
package iu.auth.principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Set;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;

@SuppressWarnings("javadoc")
public class PrincipalVerifierRegistryTest {

	private static final class TestId implements IuPrincipalIdentity {
		private static final long serialVersionUID = 1L;

		private final String realm;
		private final String name = IdGenerator.generateId();

		private TestId(String realm) {
			this.realm = realm;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Subject getSubject() {
			return new Subject(true, Set.of(this), Set.of(), Set.of());
		}

		@Override
		public void revoke() {
		}
	}

	private static final class Verifier implements PrincipalVerifier<TestId> {
		private final String realm;
		private final boolean authoritative;

		private Verifier(String realm, boolean authoritative) {
			this.realm = realm;
			this.authoritative = authoritative;
		}

		@Override
		public Class<TestId> getType() {
			return TestId.class;
		}

		@Override
		public String getRealm() {
			return realm;
		}

		@Override
		public boolean isAuthoritative() {
			return authoritative;
		}

		@Override
		public void verify(TestId id, String realm) {
			assertEquals(realm, id.realm);
		}
	}

	@Test
	public void testFinalImpl() {
		assertThrows(IllegalArgumentException.class,
				() -> PrincipalVerifierRegistry.requireFinalImpl(IuPrincipalIdentity.class));
	}

	@Test
	public void testRegisterVerfier() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		assertFalse(PrincipalVerifierRegistry.isAuthoritative(realm));

		PrincipalVerifierRegistry.registerVerifier(new Verifier(realm, true));
		assertTrue(PrincipalVerifierRegistry.isAuthoritative(realm));

		assertThrows(IllegalStateException.class,
				() -> PrincipalVerifierRegistry.registerVerifier(new Verifier(realm, false)));
		assertTrue(IuPrincipalIdentity.verify(new TestId(realm), realm));

		assertThrows(ClassCastException.class,
				() -> IuPrincipalIdentity.verify(mock(IuPrincipalIdentity.class), realm));
	}

}
