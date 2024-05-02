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
package iu.auth.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.UnsupportedEncodingException;
import java.net.http.HttpRequest;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.auth.basic.IuBasicAuthCredentials;

@SuppressWarnings("javadoc")
public class BasicAuthCredentialsTest {

	@Test
	public void testBasicAuth() throws UnsupportedEncodingException {
		final var name = IdGenerator.generateId();
		final var password = IdGenerator.generateId();
		final var auth = new BasicAuthCredentials(name, password, "US-ASCII");
		assertEquals(name, auth.getName());
		assertEquals(password, auth.getPassword());
		assertEquals("US-ASCII", auth.getCharset());

		final var req = mock(HttpRequest.Builder.class);
		auth.applyTo(req);
		verify(req).header("Authorization",
				"Basic " + Base64.getEncoder().encodeToString(IuText.ascii(name + ":" + password)));
	}

	@Test
	public void testSubject() throws UnsupportedEncodingException {
		final var name = IdGenerator.generateId();
		final var password = IdGenerator.generateId();
		final var auth = IuBasicAuthCredentials.of(name, password);
		final var sub = auth.getSubject();
		assertEquals(sub, auth.getSubject());
		assertEquals(Set.of(auth), sub.getPrincipals());
		assertEquals("BasicAuthCredentials [" + name + "]", auth.toString());
	}

}