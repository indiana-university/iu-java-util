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
package iu.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuDataStore;
import edu.iu.IuDigest;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.test.IuTestLogger;
import iu.session.config.IuSessionConfiguration;

@SuppressWarnings("javadoc")
public class SessionHandlerTest {

	@BeforeEach
	void setup() {
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
	}

	@Test
	void testCreate() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var config = mock(IuSessionConfiguration.class);
		final var handler = new SessionHandler(resourceUri, () -> config, null);
		try (final var mockSession = mockConstruction(Session.class, (a, ctx) -> {
			assertEquals(resourceUri, ctx.arguments().get(0));
			assertEquals(config, ctx.arguments().get(1));
		})) {
			final var session = handler.create();
			assertEquals(session, mockSession.constructed().get(0));
		}
	}

	@Test
	void testSessionCookieName() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var handler = new SessionHandler(resourceUri, null, null);
		assertEquals("iu-sk_" + IuText.base64Url(IuDigest.sha256(IuText.utf8(resourceUri.toString()))),
				handler.getSessionCookieName());
	}

	@Test
	void testHashKey() {
		final var secretKey = EphemeralKeys.secret("AES", 256);
		assertArrayEquals(IuDigest.sha256(secretKey), SessionHandler.hashKey(secretKey));
	}

	@Test
	void testActivateNoCookies() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var handler = new SessionHandler(resourceUri, null, null);
		assertNull(handler.activate(null));
		assertNull(handler.activate(IuIterable.iter()));
	}

	@Test
	void testActivateNotStored() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, null, store);
		final var secretKey = EphemeralKeys.secret("AES", 256);
		final var cookie = new HttpCookie(handler.getSessionCookieName(), IuText.base64Url(secretKey));
		assertNull(handler.activate(IuIterable.iter(cookie)));
	}

	@Test
	void testActivateDifferentCookie() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, null, store);
		final var secretKey = EphemeralKeys.secret("AES", 256);
		final var cookie = new HttpCookie(IdGenerator.generateId(), IuText.base64Url(secretKey));
		assertNull(handler.activate(IuIterable.iter(cookie)));
	}

	@Test
	void testActivateCorrupt() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, null, store);
		final var cookie = new HttpCookie(handler.getSessionCookieName(), "!@#$%^");
		IuTestLogger.expect(SessionHandler.class.getName(), Level.INFO, "Invalid session cookie value",
				IllegalArgumentException.class);
		assertNull(handler.activate(IuIterable.iter(cookie)));
	}

	@Test
	void testActivateSuccess() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var config = mock(IuSessionConfiguration.class);
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, () -> config, store);
		final var secretKey = EphemeralKeys.secret("AES", 256);
		final var hashKey = SessionHandler.hashKey(secretKey);
		final var activated = IdGenerator.generateId();
		when(store.get(hashKey)).thenReturn(IuText.utf8(activated));
		final var cookie = new HttpCookie(handler.getSessionCookieName(), IuText.base64Url(secretKey));
		try (final var mockSession = mockConstruction(Session.class, (a, ctx) -> {
			assertEquals(resourceUri, ctx.arguments().get(0));
			assertEquals(activated, ctx.arguments().get(1));
			assertEquals(WebKey.builder(WebKey.Type.RAW).key(secretKey).build(), ctx.arguments().get(2));
			assertEquals(config, ctx.arguments().get(3));
		})) {
			final var session = handler.activate(IuIterable.iter(cookie));
			assertEquals(session, mockSession.constructed().get(0));
		}
	}

	@Test
	void testStore() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var config = mock(IuSessionConfiguration.class, CALLS_REAL_METHODS);
		when(config.getEnc()).thenReturn(Encryption.A256GCM);
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, () -> config, store);
		final var stored = IdGenerator.generateId();
		final var session = mock(Session.class);
		when(session.tokenize(any(), eq(config))).thenReturn(stored);
		final var setCookie = handler.store(session);
		final var prefix = handler.getSessionCookieName() + "=";
		assertTrue(setCookie.startsWith(prefix), setCookie);
		final var suffix = "; Path=" + resourceUri.getPath() + "; HttpOnly";
		assertTrue(setCookie.endsWith(suffix), setCookie);
		final var secretKey = IuText
				.base64Url(setCookie.substring(prefix.length(), setCookie.length() - suffix.length()));
		verify(session).tokenize(WebKey.builder(WebKey.Type.RAW).key(secretKey).build(), config);
		verify(store).put(SessionHandler.hashKey(secretKey), IuText.utf8(stored), Duration.ofMinutes(15L));
	}

	@Test
	void testStoreStrictAndSecure() {
		final var resourceUri = URI.create("https://" + IdGenerator.generateId());
		final var config = mock(IuSessionConfiguration.class, CALLS_REAL_METHODS);
		when(config.getEnc()).thenReturn(Encryption.A256GCM);
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, () -> config, store);
		final var stored = IdGenerator.generateId();
		final var session = mock(Session.class);
		when(session.tokenize(any(), eq(config))).thenReturn(stored);
		when(session.isStrict()).thenReturn(true);
		final var setCookie = handler.store(session);
		final var prefix = handler.getSessionCookieName() + "=";
		assertTrue(setCookie.startsWith(prefix), setCookie);
		final var suffix = "; Path=/; Secure; HttpOnly; SameSite=Strict";
		assertTrue(setCookie.endsWith(suffix), setCookie);
		final var secretKey = IuText
				.base64Url(setCookie.substring(prefix.length(), setCookie.length() - suffix.length()));
		verify(session).tokenize(WebKey.builder(WebKey.Type.RAW).key(secretKey).build(), config);
		verify(store).put(SessionHandler.hashKey(secretKey), IuText.utf8(stored), Duration.ofMinutes(15L));
	}

	@Test
	void testRemoveNoCookies() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var handler = new SessionHandler(resourceUri, null, null);
		handler.remove(null);
		handler.remove(IuIterable.iter());
	}

	@Test
	void testRemoveSuccess() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, null, store);
		final var secretKey = EphemeralKeys.secret("AES", 256);
		final var cookie = new HttpCookie(handler.getSessionCookieName(), IuText.base64Url(secretKey));
		handler.remove(IuIterable.iter(cookie));
		verify(store).put(SessionHandler.hashKey(secretKey), null);
	}

	@Test
	void testRemoveDifferentCookie() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, null, store);
		final var secretKey = EphemeralKeys.secret("AES", 256);
		final var cookie = new HttpCookie(IdGenerator.generateId(), IuText.base64Url(secretKey));
		handler.remove(IuIterable.iter(cookie));
	}

	@Test
	void testRemoveCorrupt() {
		final var resourceUri = URI.create(IdGenerator.generateId());
		final var store = mock(IuDataStore.class);
		final var handler = new SessionHandler(resourceUri, null, store);
		final var cookie = new HttpCookie(handler.getSessionCookieName(), "!@#$%^");
		IuTestLogger.expect(SessionHandler.class.getName(), Level.INFO, "Invalid session cookie value",
				IllegalArgumentException.class);
		handler.remove(IuIterable.iter(cookie));
	}

}
