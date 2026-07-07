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
package iu.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.saml.IuSamlAssertion;

@SuppressWarnings("javadoc")
public class SamlPrincipalTest {

	@Test
	void testInvalid() {
		final var postAuth = mock(SamlPostAuthentication.class);
		when(postAuth.isInvalid()).thenReturn(true);
		assertThrows(IllegalStateException.class, () -> SamlPrincipal.from(postAuth));
	}

	@Test
	void testProperties() {
		final var postAuth = mock(SamlPostAuthentication.class);

		final var name = IdGenerator.generateId();
		final var authnAuthority = IdGenerator.generateId();
		final var authnInstant = Instant.now();
		final var expires = authnInstant.plusSeconds(10L);
		final var assertion = mock(IuSamlAssertion.class);
		when(postAuth.getName()).thenReturn(name);
		when(postAuth.getExpires()).thenReturn(expires);
		when(postAuth.getAuthnAuthority()).thenReturn(authnAuthority);
		when(postAuth.getAuthnInstant()).thenReturn(authnInstant);
		when(postAuth.getAssertions()).thenReturn(IuIterable.iter(assertion));

		final var principal = SamlPrincipal.from(postAuth);
		assertEquals(name, principal.getName());
		assertEquals(assertion, principal.getAssertions().iterator().next());
		assertEquals("SamlPrincipal [name=" + name + ", authnAuthority=" + authnAuthority + ", authnInstant="
				+ authnInstant + ", expires=" + expires + ", assertions=[" + assertion + "]]", principal.toString());
	}

}
