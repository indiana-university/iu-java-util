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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.basic.IuBasicAuthCredentials;

@SuppressWarnings("javadoc")
public class ClientCredentialsSourceTest {

	private String realm;
	private String id;
	private String secret;
	private Instant now;
	private Instant expires;
	private BasicAuthCredentials client;
	private Queue<IuBasicAuthCredentials> credentials;

	@BeforeEach
	public void setup() {
		realm = IdGenerator.generateId();
		id = IdGenerator.generateId();
		secret = IdGenerator.generateId();
		now = Instant.now();
		expires = now.plus(Duration.ofMinutes(2L));
		client = new BasicAuthCredentials(id, secret, "US-ASCII", now, expires);
		credentials = new ArrayDeque<>();
		credentials.offer(client);
	}

	@Test
	public void testExpirationPolicy() {
		assertThrows(IllegalArgumentException.class,
				() -> new ClientCredentialSource(realm, credentials, Duration.ofSeconds(-5L)));
	}

	@Test
	public void testBasicAuth() throws IuAuthenticationException {
		final var cc = new ClientCredentialSource(realm, credentials, Duration.ofSeconds(5L));
		cc.verify(cc.validate(client), realm);
	}

	@Test
	public void testValidate() throws IuAuthenticationException {
		final var cc = new ClientCredentialSource(realm, credentials, Duration.ofSeconds(5L));
		assertEquals("Client ID must use US-ASCII",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(new BasicAuthCredentials("테스트 클라이언트", secret, "US-ASCII", now, expires)))
						.getMessage());
		assertEquals("Client ID must contain only printable ASCII characters",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(new BasicAuthCredentials("테스트 클라이언트", secret, "EUC-KR", now, expires)))
						.getMessage());
		assertEquals("Client ID must contain only printable ASCII characters",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(new BasicAuthCredentials("\f", secret, "US-ASCII", now, expires)))
						.getMessage());
		assertEquals("Client ID must not be empty",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(new BasicAuthCredentials("", secret, "US-ASCII", now, expires)))
						.getMessage());
		assertEquals("Missing client secret", assertThrows(NullPointerException.class,
				() -> cc.validate(new BasicAuthCredentials(id, null, "US-ASCII", now, expires))).getMessage());
		assertEquals("Client secret must contain at least 12 characters", assertThrows(IllegalArgumentException.class,
				() -> cc.validate(new BasicAuthCredentials(id, "", "US-ASCII", now, expires))).getMessage());
		assertEquals("Client secret must use US-ASCII",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(new BasicAuthCredentials(id, "테스트 클라이언트", "US-ASCII", now, expires)))
						.getMessage());
		assertEquals("Client secret must contain only printable ASCII characters",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(new BasicAuthCredentials(id, secret + "테스트 클라이언트", "EUC-KR", now, expires)))
						.getMessage());
		assertEquals("Client secret must contain only printable ASCII characters",
				assertThrows(IllegalArgumentException.class,
						() -> cc.validate(new BasicAuthCredentials(id, secret + "\f", "US-ASCII", now, expires)))
						.getMessage());

		final var valid = cc.validate(IuApiCredentials.basic(id, secret));
		assertEquals(id, valid.getName());
		assertEquals(secret, valid.getPassword());
		assertEquals("US-ASCII", valid.getCharset());
		assertEquals(Instant.EPOCH, valid.getNotBefore());
		assertEquals(Instant.EPOCH.plus(Duration.ofSeconds(5L)), valid.getExpires());
	}

	@Test
	public void testVerify() {
		final var expiredId = IdGenerator.generateId();
		credentials.offer(IuApiCredentials.basic(expiredId, secret));
		final var futureId = IdGenerator.generateId();
		credentials.offer(new BasicAuthCredentials(futureId, secret, "US-ASCII", now.plus(Period.ofDays(1)), expires));
		final var cc = new ClientCredentialSource(realm, credentials, Duration.ofSeconds(5L));
		assertDoesNotThrow(() -> cc.verify((BasicAuthCredentials) IuApiCredentials.basic(id, secret), realm));
		assertEquals("Basic realm=\"" + realm + "\" charset=\"US-ASCII\"", assertThrows(IuAuthenticationException.class,
				() -> cc.verify((BasicAuthCredentials) IuApiCredentials.basic(IdGenerator.generateId(), secret), realm))
				.getMessage());
		assertEquals("Basic realm=\"" + realm + "\" charset=\"US-ASCII\"", assertThrows(IuAuthenticationException.class,
				() -> cc.verify((BasicAuthCredentials) IuApiCredentials.basic(id, IdGenerator.generateId()), realm))
				.getMessage());
		assertEquals("Basic realm=\"" + realm + "\" charset=\"US-ASCII\"",
				assertThrows(IuAuthenticationException.class,
						() -> cc.verify((BasicAuthCredentials) IuApiCredentials.basic(futureId, secret), realm))
						.getMessage());
		assertEquals("Basic realm=\"" + realm + "\" charset=\"US-ASCII\"",
				assertThrows(IuAuthenticationException.class,
						() -> cc.verify((BasicAuthCredentials) IuApiCredentials.basic(expiredId, secret), realm))
						.getMessage());
	}

}
