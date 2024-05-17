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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockConstruction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.basic.IuBasicAuthCredentials;
import edu.iu.auth.basic.IuClientCredentials;
import edu.iu.auth.config.AuthConfig;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class BasicAuthSpiTest {

	@Test
	public void testSpi() {
		final var basicSpi = new BasicAuthSpi();
		try (final var mockBasic = mockConstruction(BasicAuthCredentials.class)) {
			final var name = IdGenerator.generateId();
			final var password = IdGenerator.generateId();
			final var basic = basicSpi.createCredentials(name, password, "UTF-8");
			assertEquals(List.of(basic), mockBasic.constructed());
		}
	}

	@Test
	public void testRegistration() throws IuAuthenticationException, InterruptedException {
		final var realm = IdGenerator.generateId();
		final var id = IdGenerator.generateId();
		final var secret = IdGenerator.generateId();
		final var now = Instant.now();
		final var expires = now.plus(Duration.ofMinutes(2L));
		final var client = new IuClientCredentials() {
			@Override
			public String getId() {
				return id;
			}

			@Override
			public String getSecret() {
				return secret;
			}

			@Override
			public Instant getNotBefore() {
				return now;
			}

			@Override
			public Instant getExpires() {
				return expires;
			}
		};
		final var credentials = new ArrayDeque<IuClientCredentials>();
		credentials.offer(client);

		final var config = ClientCredentialSource.of(realm, credentials, Duration.ofMillis(500L));
		AuthConfig.register(config);
		AuthConfig.seal();
		
		IuPrincipalIdentity.verify(IuBasicAuthCredentials.of(id, secret), realm);
		assertThrows(IuAuthenticationException.class,
				() -> IuPrincipalIdentity.verify(IuBasicAuthCredentials.of(id, "wrong password"), realm));
		Thread.sleep(500L);
		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials expired at"));
		assertThrows(IuAuthenticationException.class,
				() -> IuPrincipalIdentity.verify(IuBasicAuthCredentials.of(id, secret), realm));
	}

}
