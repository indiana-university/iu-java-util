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
package iu.auth.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.basic.IuBasicAuthCredentials;
import edu.iu.auth.basic.IuClientCredentials;

@SuppressWarnings("javadoc")
public class BasicAuthIT {

	@Test
	public void testBasic() {
		final var name = IdGenerator.generateId();
		final var password = IdGenerator.generateId();
		final var basic = IuBasicAuthCredentials.of(name, password);
		assertEquals(name, basic.getName());
		assertEquals(password, basic.getPassword());
	}

	@Test
	public void testRegister() throws IuAuthenticationException {
		final var name = IdGenerator.generateId();
		final var password = IdGenerator.generateId();
		final var now = Instant.now();
		final var client = new IuClientCredentials() {
			@Override
			public String getId() {
				return name;
			}

			@Override
			public String getSecret() {
				return password;
			}

			@Override
			public Instant getNotBefore() {
				return now;
			}

			@Override
			public Instant getExpires() {
				return now.plusSeconds(5L);
			}
		};
		final var clientCredentials = Set.of(client);
		final var realm = IdGenerator.generateId();
		IuClientCredentials.register(clientCredentials, realm, Duration.ofSeconds(5L));
		IuPrincipalIdentity.verify(IuBasicAuthCredentials.of(name, password), realm);
	}

}
