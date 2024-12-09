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
package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuSessionConfiguration;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class SessionHandlerTest {
	private URI resourceUri;
	private IuSessionConfiguration configuration;
	private WebKey issuerKey;
	private Algorithm algorithm;
	private SessionHandler sessionHandler;

	@Nested
	class SessionHandlerTest_1 {
		@BeforeEach
		public void setup() {
			resourceUri = URI.create("http://" + IdGenerator.generateId());
			configuration = mock(IuSessionConfiguration.class, CALLS_REAL_METHODS);
			when(configuration.getResourceUris()).thenReturn(Arrays.asList(resourceUri));
			issuerKey = WebKey.ephemeral(Algorithm.HS256);
			algorithm = Algorithm.HS256;
			sessionHandler = new SessionHandler(resourceUri, configuration, () -> issuerKey, algorithm);
			IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		}

		@Test
		public void testSessionHandlerConstructorWithValidParameters() {
			assertDoesNotThrow(() -> new SessionHandler(resourceUri, configuration, () -> issuerKey, algorithm));
		}

		@Test
		public void testSessionCreationWithValidParameters() {
			when(configuration.getMaxSessionTtl()).thenReturn(Duration.ofHours(12L));
			assertNotNull(sessionHandler.create());
		}

		@Test
		public void testActivateSessionNull() {
			assertNull(sessionHandler.activate(null));
		}

		@Test
		public void testActivateSessionWithNoSecretKey() {
			Iterable<HttpCookie> cookies = Arrays
					.asList(new HttpCookie(IdGenerator.generateId(), IdGenerator.generateId()));
			assertNull(sessionHandler.activate(cookies));
		}

		@Test
		public void testActivateSessionWithInvalidSecretKey() {
			Iterable<HttpCookie> cookies = Arrays
					.asList(new HttpCookie(sessionHandler.getSessionCookieName(), "invalidSecretKey"));
			assertNull(sessionHandler.activate(cookies));
		}

		@Test
		public void testActivateSessionWithCorruptSecretKey() {
			Iterable<HttpCookie> cookies = Arrays
					.asList(new HttpCookie(sessionHandler.getSessionCookieName(), "Q@#$%^SecretKey"));
			IuTestLogger.expect(SessionHandler.class.getName(), Level.INFO, "Invalid session cookie value",
					IllegalArgumentException.class);
			assertNull(sessionHandler.activate(cookies));
		}

		@Test
		public void testRemoveInvalidSecretKey() {
			Iterable<HttpCookie> cookies = Arrays
					.asList(new HttpCookie(sessionHandler.getSessionCookieName(), "invalidSecretKey"));
			assertDoesNotThrow(() -> sessionHandler.remove(cookies));
		}

		@Test
		public void testRemoveCorruptSecretKey() {
			Iterable<HttpCookie> cookies = Arrays
					.asList(new HttpCookie(sessionHandler.getSessionCookieName(), "Q@#$%^SecretKey"));
			IuTestLogger.expect(SessionHandler.class.getName(), Level.INFO, "Invalid session cookie value",
					IllegalArgumentException.class);
			assertDoesNotThrow(() -> sessionHandler.remove(cookies));
		}

		@Test
		public void testRemoveIgnoreNullCookies() {
			assertDoesNotThrow(() -> sessionHandler.remove(null));
		}

		@Test
		public void testRemoveIgnoreOtherCookies() {
			Iterable<HttpCookie> cookies = Arrays
					.asList(new HttpCookie(IdGenerator.generateId(), IdGenerator.generateId()));
			assertDoesNotThrow(() -> sessionHandler.remove(cookies));
		}

		@Test
		public void testStoreSessionAndActivateSessionSuccess() {
			Session session = new Session(resourceUri, Duration.ofHours(12L));
			when(configuration.getInactiveTtl()).thenCallRealMethod();
			session.setStrict(false);
			
			String cookie = sessionHandler.store(session);
			assertNotNull(cookie);
			final var cookieMatcher = Pattern
					.compile(sessionHandler.getSessionCookieName() + "=([^;]+); Path=/; HttpOnly").matcher(cookie);
			assertTrue(cookieMatcher.matches(), cookie);

			final var key = cookieMatcher.group(1);
			final var cookies = Arrays.asList(new HttpCookie(sessionHandler.getSessionCookieName(), key));
			assertNotNull(sessionHandler.activate(cookies));
			assertNotNull(sessionHandler.activate(cookies));

			assertDoesNotThrow(() -> sessionHandler.remove(cookies));
			assertNull(sessionHandler.activate(cookies));
		}

		@Test
		public void testStoreSessionAndActivateSessionSuccessWithInvalidCookieFirst() {
			Session session = new Session(resourceUri, Duration.ofHours(12L));
			session.setStrict(false);
			when(configuration.getInactiveTtl()).thenCallRealMethod();

			String cookie = sessionHandler.store(session);
			assertNotNull(cookie);
			final var cookieMatcher = Pattern
					.compile(sessionHandler.getSessionCookieName() + "=([^;]+); Path=/; HttpOnly").matcher(cookie);
			assertTrue(cookieMatcher.matches(), cookie);

			final var key = cookieMatcher.group(1);
			final var cookies = Arrays.asList(
					new HttpCookie(sessionHandler.getSessionCookieName(), IdGenerator.generateId()),
					new HttpCookie(sessionHandler.getSessionCookieName(), key));
			assertNotNull(sessionHandler.activate(cookies));
			assertNotNull(sessionHandler.activate(cookies));

			assertDoesNotThrow(() -> sessionHandler.remove(cookies));
			assertNull(sessionHandler.activate(cookies));
		}

		@Test
		public void testStoreSessionAndActivateSessionSuccessSubpath() {
			final var path = "/" + IdGenerator.generateId();
			final var uriWithPath = URI.create(resourceUri + path);
			sessionHandler = new SessionHandler(uriWithPath, configuration, () -> issuerKey, algorithm);
			Session session = new Session(uriWithPath, Duration.ofHours(12L));
			session.setStrict(false);
			
			when(configuration.getInactiveTtl()).thenCallRealMethod();

			String cookie = sessionHandler.store(session);
			assertNotNull(cookie);
			final var cookieMatcher = Pattern
					.compile(sessionHandler.getSessionCookieName() + "=([^;]+); Path=" + path + "; HttpOnly")
					.matcher(cookie);
			assertTrue(cookieMatcher.matches(), cookie);

			final var key = cookieMatcher.group(1);
			final var cookies = Arrays.asList(new HttpCookie(sessionHandler.getSessionCookieName(), key));
			assertNotNull(sessionHandler.activate(cookies));
			assertNotNull(sessionHandler.activate(cookies));

			assertDoesNotThrow(() -> sessionHandler.remove(cookies));
			assertNull(sessionHandler.activate(cookies));
		}

		@Test
		public void testPurgeStoredSessionWhenExpire() {
			when(configuration.getInactiveTtl()).thenReturn(Duration.ofMillis(250L));
			Session session = new Session(resourceUri, Duration.ofHours(12L));
			session.setStrict(false);
			
			String cookie = sessionHandler.store(session);
			assertNotNull(cookie);
			final var cookieMatcher = Pattern
					.compile(sessionHandler.getSessionCookieName() + "=([^;]+); Path=/; HttpOnly").matcher(cookie);
			assertTrue(cookieMatcher.matches(), cookie);
			assertDoesNotThrow(() -> Thread.sleep(250L));
			assertNull(sessionHandler.activate(
					Arrays.asList(new HttpCookie(sessionHandler.getSessionCookieName(), cookieMatcher.group(1)))));
		}

		@Test
		public void testPurgeTask() {
			final var purgeTask = new SessionHandler.PurgeTask();
			assertDoesNotThrow(purgeTask::run);
			Session session = new Session(resourceUri, Duration.ofHours(12L));
			sessionHandler.store(session);
			assertDoesNotThrow(purgeTask::run);
		}

		@Test
		public void testStoreWithNoPurgeTask() {
			final var purgeTask = new SessionHandler.PurgeTask();
			Session session = new Session(resourceUri, Duration.ofHours(12L));
			sessionHandler.store(session);
			assertDoesNotThrow(purgeTask::run);
		}

		@Test
		public void testStoreWithPurgeTaskAndActivate() {
			final var purgeTask = new SessionHandler.PurgeTask();
			when(configuration.getInactiveTtl()).thenReturn(Duration.ofMillis(250L));
			Session session = new Session(resourceUri, Duration.ofHours(12L));
			String cookie = sessionHandler.store(session);
			final var cookieMatcher = Pattern
					.compile(sessionHandler.getSessionCookieName() + "=([^;]+); Path=/; HttpOnly; SameSite=Strict")
					.matcher(cookie);
			assertTrue(cookieMatcher.matches(), cookie);
			assertDoesNotThrow(() -> Thread.sleep(250L));
			assertDoesNotThrow(purgeTask::run);
			assertNull(sessionHandler.activate(
					Arrays.asList(new HttpCookie(sessionHandler.getSessionCookieName(), cookieMatcher.group(1)))));
		}
	}

	@Nested
	class SessionHandlerTest_2 {
		@BeforeEach
		public void setup() {
			resourceUri = URI.create("https://" + IdGenerator.generateId());
			configuration = mock(IuSessionConfiguration.class, CALLS_REAL_METHODS);
			when(configuration.getResourceUris()).thenReturn(Arrays.asList(resourceUri));
			issuerKey = WebKey.ephemeral(Algorithm.HS256);
			algorithm = Algorithm.HS256;
			sessionHandler = new SessionHandler(resourceUri, configuration, () -> issuerKey, algorithm);
			IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		}

		@Test
		public void testStoreWithHttpsResourceUrl() {
			final var purgeTask = new SessionHandler.PurgeTask();
			Session session = new Session(resourceUri, Duration.ofHours(12L));
			sessionHandler.store(session);
			assertDoesNotThrow(purgeTask::run);
		}
	}
}
