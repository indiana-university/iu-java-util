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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.Set;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;

@SuppressWarnings("javadoc")
public class JwtAuthorizationGrantTest {

	@Test
	public void testDelegation() {
		final var name = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var subject = mock(Subject.class);

		final var jwt = mock(Jwt.class);
		when(jwt.getName()).thenReturn(name);
		when(jwt.token()).thenReturn(token);
		when(jwt.getSubject()).thenReturn(subject);

		final var jwtAuthorizationGrant = new JwtAuthorizationGrant(jwt, null);
		assertEquals(name, jwtAuthorizationGrant.getName());
		assertSame(subject, jwtAuthorizationGrant.getSubject());
	}

	@Test
	public void testApplyTo() throws IuAuthenticationException {
		final var token = IdGenerator.generateId();
		final var realm = IdGenerator.generateId();
		final var scope = Set.of(IdGenerator.generateId());
		final var jwt = mock(Jwt.class);
		when(jwt.token()).thenReturn(token);
		when(jwt.realm()).thenReturn(realm);
		final var jwtAuthorizationGrant = new JwtAuthorizationGrant(jwt, scope);

		try (final var mockId = mockStatic(IuPrincipalIdentity.class);
				final var mockPub = mockStatic(BodyPublishers.class)) {
			final var pub = mock(BodyPublisher.class);
			mockPub.when(() -> BodyPublishers.ofString(argThat(a -> {
				assertEquals("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + token
						+ "&scope=" + scope.iterator().next(), a);
				return true;
			}))).thenReturn(pub);
			final var rb = mock(HttpRequest.Builder.class);
			jwtAuthorizationGrant.applyTo(rb);
			mockId.verify(() -> IuPrincipalIdentity.verify(jwt, realm));
			verify(rb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(rb).POST(pub);
		}
	}

}
