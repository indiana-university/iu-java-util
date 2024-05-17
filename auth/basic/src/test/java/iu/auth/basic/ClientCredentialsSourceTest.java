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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.basic.IuBasicAuthCredentials;
import edu.iu.auth.basic.IuClientCredentials;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class ClientCredentialsSourceTest {

	private static IuClientCredentials clientCredentials(String name, String secret, Instant notBefore,
			Instant expires) {
		return new IuClientCredentials() {
			@Override
			public String getId() {
				return name;
			}

			@Override
			public String getSecret() {
				return secret;
			}

			@Override
			public Instant getNotBefore() {
				return notBefore;
			}

			@Override
			public Instant getExpires() {
				return expires;
			}
		};
	}

	private String realm;
	private String id;
	private String secret;
	private Instant now;
	private Instant expires;
	private IuClientCredentials client;
	private Queue<IuClientCredentials> credentials;

	@BeforeEach
	public void setup() {
		realm = IdGenerator.generateId();
		id = IdGenerator.generateId();
		secret = IdGenerator.generateId();
		now = Instant.now();
		expires = now.plus(Duration.ofMinutes(2L));
		client = clientCredentials(id, secret, now, expires);
		credentials = new ArrayDeque<>();
		credentials.offer(client);
	}

	@Test
	public void testExpirationPolicy() {
		assertThrows(IllegalArgumentException.class,
				() -> ClientCredentialSource.of(realm, credentials, Duration.ofSeconds(-5L)));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testBasicAuth() throws IuAuthenticationException {
		final var cc = (ClientCredentialSource) ClientCredentialSource.of(realm, credentials, Duration.ofSeconds(5L));
		cc.verify(new BasicAuthCredentials(id, secret, "US-ASCII"), realm);
		assertEquals("Basic", cc.getAuthScheme());
		assertNull(cc.getAuthenticationEndpoint());
		assertSame(BasicAuthCredentials.class, cc.getType());
	}

	@Test
	public void testValidate() throws IuAuthenticationException {
		final var cc = ClientCredentialSource.of(realm, credentials, Duration.ofSeconds(5L));
		assertEquals("Client ID must contain only printable ASCII characters",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(clientCredentials("테스트 클라이언트", secret, now, expires))).getMessage());
		assertEquals("Client ID must contain only printable ASCII characters",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(clientCredentials("\f", secret, now, expires))).getMessage());
		assertEquals("Client ID must not be empty", assertThrows(IllegalArgumentException.class,
				() -> cc.validate(clientCredentials("", secret, now, expires))).getMessage());
		assertEquals("Missing client secret",
				assertThrows(NullPointerException.class, () -> cc.validate(clientCredentials(id, null, now, expires)))
						.getMessage());
		assertEquals("Client secret must contain at least 12 characters",
				assertThrows(IllegalArgumentException.class, () -> cc.validate(clientCredentials(id, "", now, expires)))
						.getMessage());
		assertEquals("Client secret must contain only printable ASCII characters",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(clientCredentials(id, secret + "테스트 클라이언트", now, expires))).getMessage());
		assertEquals("Client secret must contain only printable ASCII characters",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(clientCredentials(id, secret + "\f", now, expires))).getMessage());

		cc.validate(clientCredentials(id, secret, now, expires));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testVerify() {
		final var expiredId = IdGenerator.generateId();
		credentials.offer(clientCredentials(expiredId, secret, null, null));
		final var futureId = IdGenerator.generateId();
		credentials.offer(clientCredentials(futureId, secret, now.plus(Period.ofDays(1)), expires));
		final var cc = (ClientCredentialSource) ClientCredentialSource.of(realm, credentials, Duration.ofSeconds(5L));
		assertDoesNotThrow(() -> cc.verify((BasicAuthCredentials) IuBasicAuthCredentials.of(id, secret), realm));

		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials are not valid until"));
		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials expired at"));
		assertEquals("Basic realm=\"" + realm + "\" charset=\"US-ASCII\"",
				assertThrows(IuAuthenticationException.class,
						() -> cc.verify(
								(BasicAuthCredentials) IuBasicAuthCredentials.of(IdGenerator.generateId(), secret),
								realm))
						.getMessage());
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials are not valid until"));
		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials expired at"));
		assertEquals("Basic realm=\"" + realm + "\" charset=\"US-ASCII\"", assertThrows(IuAuthenticationException.class,
				() -> cc.verify((BasicAuthCredentials) IuBasicAuthCredentials.of(id, IdGenerator.generateId()), realm))
				.getMessage());
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials are not valid until"));
		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials expired at"));
		assertEquals("Basic realm=\"" + realm + "\" charset=\"US-ASCII\"",
				assertThrows(IuAuthenticationException.class,
						() -> cc.verify((BasicAuthCredentials) IuBasicAuthCredentials.of(futureId, secret), realm))
						.getMessage());
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials are not valid until"));
		IuTestLogger.expect("iu.auth.basic.ClientCredentialSource", Level.CONFIG,
				"Invalid client credentials entry for realm " + realm, IllegalArgumentException.class,
				e -> e.getMessage().startsWith("Client credentials expired at"));
		assertEquals("Basic realm=\"" + realm + "\" charset=\"US-ASCII\"",
				assertThrows(IuAuthenticationException.class,
						() -> cc.verify((BasicAuthCredentials) IuBasicAuthCredentials.of(expiredId, secret), realm))
						.getMessage());
	}

}
