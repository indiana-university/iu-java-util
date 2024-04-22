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
package edu.iu.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class AuthExceptionsTest {

	@Test
	public void testChallengeOnly() {
		final var challenge = IdGenerator.generateId();
		assertEquals(challenge, new IuAuthenticationException(challenge).getMessage());
	}

	@Test
	public void testChallengeAndCause() {
		final var challenge = IdGenerator.generateId();
		final var cause = new Throwable();
		final var e = new IuAuthenticationException(challenge, cause);
		assertEquals(challenge, e.getMessage());
		assertSame(cause, e.getCause());
	}

	@Test
	public void testChallengeAndLocation() {
		final var location = mock(URI.class);
		final var e = new IuAuthenticationException("");
		e.setLocation(location);
		assertEquals(location, e.getLocation());
	}

	@Test
	public void testExpiredOnly() {
		final var challenge = IdGenerator.generateId();
		assertEquals(challenge, new IuExpiredCredentialsException(challenge).getMessage());
	}

	@Test
	public void testExpiredAndCause() {
		final var challenge = IdGenerator.generateId();
		final var cause = new Throwable();
		final var e = new IuExpiredCredentialsException(challenge, cause);
		assertEquals(challenge, e.getMessage());
		assertSame(cause, e.getCause());
	}


}
