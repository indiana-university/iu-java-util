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
package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.saml.IuSamlAssertion;

@SuppressWarnings("javadoc")
public class SamlPrincipalTest {

	@Test
	public void testPrincipal() {
		final Queue<IuSamlAssertion> samlAssertions = new ArrayDeque<>();
		final var realm = IdGenerator.generateId();
		final var principalName = IdGenerator.generateId();
		final var issueInstant = Instant.now();
		final var authnInstant = Instant.now().minusSeconds(5L);
		final var expires = authnInstant.plus(Duration.ofHours(12L));

		SamlPrincipal samlPrincipal = new SamlPrincipal(realm, principalName, issueInstant, authnInstant, expires,
				samlAssertions);

		assertEquals(
				"SamlPrincipal [realm=" + realm + ", name=" + principalName + ", issueTime=" + issueInstant
						+ ", authTime=" + authnInstant + ", expires=" + expires + ", assertions=[]]",
				samlPrincipal.toString());

		assertEquals(principalName, samlPrincipal.getName());
		final var subject = samlPrincipal.getSubject();
		assertSame(samlPrincipal, subject.getPrincipals().iterator().next());
		assertEquals(1, subject.getPrincipals().size());
		assertTrue(subject.getPrivateCredentials().isEmpty());
		assertTrue(subject.getPublicCredentials().isEmpty());
		assertEquals(authnInstant, samlPrincipal.getAuthTime());
		assertEquals(issueInstant, samlPrincipal.getIssuedAt());
		assertEquals(expires, samlPrincipal.getExpires());

		assertDoesNotThrow(() -> samlPrincipal.verify(realm));

		assertThrows(IllegalArgumentException.class, () -> samlPrincipal.verify(IdGenerator.generateId()));

	}

	@Test
	public void testPrincipalExpire() {
		final Queue<IuSamlAssertion> samlAssertions = new ArrayDeque<>();
		final var realm = IdGenerator.generateId();
		final var principalName = "foo";
		final var issueInstant = Instant.now();
		final var authnInstant = Instant.now();
		final var expires = Instant.now().minus(Duration.ofSeconds(12L));

		SamlPrincipal samlPrincipal = new SamlPrincipal(realm, principalName, issueInstant, authnInstant, expires,
				samlAssertions);
		final var e = assertThrows(IuAuthenticationException.class, () -> samlPrincipal.verify(realm));
		assertInstanceOf(IllegalStateException.class, e.getCause());
		assertEquals("expired", e.getCause().getMessage());
	}

	@Test
	public void testIsBound() {
		final var details = mock(SamlSessionDetails.class);
		assertFalse(SamlPrincipal.isBound(details));
		when(details.getRealm()).thenReturn(IdGenerator.generateId());
		assertFalse(SamlPrincipal.isBound(details));
		when(details.getName()).thenReturn(IdGenerator.generateId());
		assertFalse(SamlPrincipal.isBound(details));
		when(details.getIssueTime()).thenReturn(Instant.now());
		assertFalse(SamlPrincipal.isBound(details));
		when(details.getAuthTime()).thenReturn(Instant.now());
		assertFalse(SamlPrincipal.isBound(details));
		when(details.getExpires()).thenReturn(Instant.now());
		assertTrue(SamlPrincipal.isBound(details));
		when(details.getAssertions()).thenReturn(null);
		assertFalse(SamlPrincipal.isBound(details));
	}

	@Test
	public void testFromAndBind() {
		final var realm = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final var issueTime = Instant.now();
		final var authTime = Instant.now().minusSeconds(15L);
		final var expires = Instant.now().plusSeconds(15L);

		final var details = mock(SamlSessionDetails.class);
		when(details.getRealm()).thenReturn(realm);
		when(details.getName()).thenReturn(name);
		when(details.getIssueTime()).thenReturn(issueTime);
		when(details.getAuthTime()).thenReturn(authTime);
		when(details.getExpires()).thenReturn(expires);

		final var assertion1 = mock(IuSamlAssertion.class);
		final var assertion2 = mock(IuSamlAssertion.class);
		when(details.getAssertions()).thenReturn(IuIterable.iter(assertion1, assertion2));

		final var principal = SamlPrincipal.from(details);
		assertDoesNotThrow(() -> principal.verify(realm));
		assertEquals(name, principal.getName());
		assertEquals(issueTime, principal.getIssuedAt());
		assertEquals(authTime, principal.getAuthTime());
		assertEquals(expires, principal.getExpires());
		final var assertions = principal.getSubject().getPublicCredentials(IuSamlAssertion.class);
		assertEquals(2, assertions.size());
		assertTrue(assertions.contains(assertion1));
		assertTrue(assertions.contains(assertion2));

		principal.bind(details);
		verify(details).setRealm(realm);
		verify(details).setName(name);
		verify(details).setIssueTime(issueTime);
		verify(details).setAuthTime(authTime);
		verify(details).setExpires(expires);
		verify(details).setAssertions(argThat(a -> {
			final var i = a.iterator();
			return i.next().equals(assertion1) && i.next().equals(assertion2) && !i.hasNext();
		}));
	}

}
