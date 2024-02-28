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
package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpRequest;
import java.util.Set;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class BearerAuthCredentialsTest {

	@Test
	public void testAccessToken() {
		final var accessToken = IdGenerator.generateId();
		final var subject = mock(Subject.class);
		final var principal = new MockPrincipal();
		when(subject.getPrincipals()).thenReturn(Set.of(principal));

		final var auth = new BearerAuthCredentials(subject, accessToken);
		assertNotNull(auth.toString());
		assertEquals(accessToken, auth.getAccessToken());
		assertSame(subject, auth.getSubject());
		assertEquals(principal.getName(), auth.getName());

		final var req = mock(HttpRequest.Builder.class);
		auth.applyTo(req);
		verify(req).header("Authorization", "Bearer " + accessToken);
	}

	@Test
	public void testEquals() {
		final var accessToken = IdGenerator.generateId();
		final var principal = new MockPrincipal();
		final var subject = new Subject(true, Set.of(principal), Set.of(), Set.of());
		final var auth = new BearerAuthCredentials(subject, accessToken);
		final var auth2 = new BearerAuthCredentials(subject, accessToken);
		assertEquals(auth, auth2);
		assertEquals(auth2, auth);
		assertNotEquals(auth, new Object());

		final var principal2 = new MockPrincipal();
		final var subject2 = new Subject(true, Set.of(principal2), Set.of(), Set.of());
		final var auth3 = new BearerAuthCredentials(subject2, accessToken);
		assertNotEquals(auth, auth3);
		assertNotEquals(auth3, auth);

		final var subject3 = new Subject(true, Set.of(principal), Set.of(new Object()), Set.of());
		final var auth4 = new BearerAuthCredentials(subject3, accessToken);
		assertNotEquals(auth, auth4);
		assertNotEquals(auth4, auth);
	}

}
