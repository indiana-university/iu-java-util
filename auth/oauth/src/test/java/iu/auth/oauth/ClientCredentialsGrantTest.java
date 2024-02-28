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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.NotSerializableException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuBearerAuthCredentials;
import edu.iu.test.IuTestLogger;
import iu.auth.util.HttpUtils;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class ClientCredentialsGrantTest {

	@Test
	public void testRequiresClient() {
		final var realm = IdGenerator.generateId();
		assertThrows(IllegalStateException.class, () -> new ClientCredentialsGrant(realm));
		try (final var mockSpi = mockStatic(OAuthSpi.class)) {
			final var client = mock(IuAuthorizationClient.class);
			mockSpi.when(() -> OAuthSpi.getClient(realm)).thenReturn(client);
			assertDoesNotThrow(() -> new ClientCredentialsGrant(realm));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testScopes() {
		final var realm = IdGenerator.generateId();
		try (final var mockSpi = mockStatic(OAuthSpi.class)) {
			final var client = mock(IuAuthorizationClient.class);
			when(client.getScope()).thenReturn(List.of("\""), List.of("\\"), List.of("\n"),
					List.of(Character.toString((char) 0x80)), List.of("foo", "bar"), null);
			mockSpi.when(() -> OAuthSpi.getClient(realm)).thenReturn(client);
			assertThrows(IllegalArgumentException.class, () -> new ClientCredentialsGrant(realm));
			assertThrows(IllegalArgumentException.class, () -> new ClientCredentialsGrant(realm));
			assertThrows(IllegalArgumentException.class, () -> new ClientCredentialsGrant(realm));
			assertThrows(IllegalArgumentException.class, () -> new ClientCredentialsGrant(realm));
			assertEquals("foo bar", new ClientCredentialsGrant(realm).validatedScope);
			assertNull(new ClientCredentialsGrant(realm).validatedScope);
		}
	}

	@Test
	public void testAuthorizeRequiresRelative() throws URISyntaxException {
		final var realm = IdGenerator.generateId();
		try (final var mockSpi = mockStatic(OAuthSpi.class)) {
			final var client = mock(IuAuthorizationClient.class);
			when(client.getResourceUri()).thenReturn(new URI("foo:baz"));
			mockSpi.when(() -> OAuthSpi.getClient(realm)).thenReturn(client);
			final var cred = new ClientCredentialsGrant(realm);
			assertThrows(IllegalArgumentException.class, () -> cred.authorize(new URI("foo:bar")));
		}
	}

	@Test
	public void testAuthorizeBare() throws URISyntaxException, IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var uri = new URI("foo:/bar");
		final var tokenEndpoint = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var principal = new MockPrincipal();
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(uri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpoint);
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.getClientCredentialsAttributes()).thenReturn(null);
		when(client.verify(any())).thenReturn(new Subject(true, Set.of(principal), Set.of(), Set.of()));
		IuAuthorizationClient.initialize(client);

		final var grant = new ClientCredentialsGrant(realm);
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var hr = mock(HttpRequest.class);
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=client_credentials";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);
			when(hrb.build()).thenReturn(hr);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(tokenEndpoint)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).build();
			mockHttpUtils.when(() -> HttpUtils.read(hr)).thenReturn(tokenResponse);

			IuTestLogger.expect("iu.auth.oauth.ClientCredentialsGrant", Level.FINE,
					"Authorization required, initiating client credentials flow for " + realm);
			final var cred = grant.authorize(uri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload));
			verify(hrb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb).POST(bp);
			verify(clientCredentials).applyTo(hrb);
			assertInstanceOf(IuBearerAuthCredentials.class, cred);
			assertEquals(principal.getName(), cred.getName());
			assertSame(cred, grant.authorize(uri));

			grant.revoke();
			grant.revoke(); // verify no-op
			verify(client).revoke(cred);

			IuTestLogger.expect("iu.auth.oauth.ClientCredentialsGrant", Level.FINE,
					"Authorization required, initiating client credentials flow for " + realm);
			final var cred2 = grant.authorize(uri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload), times(2));
			verify(hrb, times(2)).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb, times(2)).POST(bp);
			verify(clientCredentials, times(2)).applyTo(hrb);
			assertInstanceOf(IuBearerAuthCredentials.class, cred2);
			assertNotSame(cred, cred2);
			assertEquals(principal.getName(), cred2.getName());
		}
	}

	@Test
	public void testAuthorizeWithScopeExpiresAndExtras()
			throws URISyntaxException, IuAuthenticationException, InterruptedException {
		final var realm = IdGenerator.generateId();
		final var uri = new URI("foo:/bar");
		final var tokenEndpoint = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var principal = new MockPrincipal();

		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(uri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpoint);
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.getScope()).thenReturn(List.of("foobar"));
		when(client.getClientCredentialsAttributes()).thenReturn(Map.of("foo", "bar"));
		when(client.verify(any())).thenReturn(new Subject(true, Set.of(principal), Set.of(), Set.of()));
		IuAuthorizationClient.initialize(client);

		final var grant = new ClientCredentialsGrant(realm);
		assertFalse(grant.isExpired());
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var hr = mock(HttpRequest.class);
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=client_credentials&scope=foobar&foo=bar";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);
			when(hrb.build()).thenReturn(hr);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(tokenEndpoint)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).add("expires_in", 1).build();
			mockHttpUtils.when(() -> HttpUtils.read(hr)).thenReturn(tokenResponse);

			IuTestLogger.expect("iu.auth.oauth.ClientCredentialsGrant", Level.FINE,
					"Authorization required, initiating client credentials flow for " + realm);
			final var cred = grant.authorize(uri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload));
			verify(hrb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb).POST(bp);
			verify(clientCredentials).applyTo(hrb);
			assertInstanceOf(IuBearerAuthCredentials.class, cred);
			assertEquals(principal.getName(), cred.getName());
			assertTrue(new AuthorizedScope("foobar", realm).implies(((IuBearerAuthCredentials) cred).getSubject()));

			assertSame(cred, grant.authorize(uri));
			assertFalse(grant.isExpired());
			Thread.sleep(1001L);
			assertTrue(grant.isExpired());
			IuTestLogger.expect("iu.auth.oauth.ClientCredentialsGrant", Level.FINE,
					"Authorized session has expired, initiating client credentials flow for " + realm);
			final var cred2 = grant.authorize(uri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload), times(2));
			verify(hrb, times(2)).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb, times(2)).POST(bp);
			assertInstanceOf(IuBearerAuthCredentials.class, cred2);
			assertNotSame(cred, cred2);
			assertEquals(principal.getName(), cred2.getName());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAuthorizeRejectsBadAttributes()
			throws URISyntaxException, IuAuthenticationException, InterruptedException {
		final var realm = IdGenerator.generateId();
		final var uri = new URI("foo:/bar");
		final var client = mock(IuAuthorizationClient.class);

		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(uri);
		when(client.getClientCredentialsAttributes()).thenReturn(Map.of("grant_type", "foobar"),
				Map.of("scope", "scoop"));
		IuAuthorizationClient.initialize(client);

		final var grant = new ClientCredentialsGrant(realm);

		IuTestLogger.expect("iu.auth.oauth.ClientCredentialsGrant", Level.FINE,
				"Authorization required, initiating client credentials flow for " + realm);
		assertEquals("Illegal attempt to override standard client credentials attribute grant_type",
				assertThrows(IllegalArgumentException.class, () -> grant.authorize(uri)).getMessage());

		IuTestLogger.expect("iu.auth.oauth.ClientCredentialsGrant", Level.FINE,
				"Authorization required, initiating client credentials flow for " + realm);
		assertEquals("Illegal attempt to override standard client credentials attribute scope",
				assertThrows(IllegalArgumentException.class, () -> grant.authorize(uri)).getMessage());
	}

	@Test
	public void testRequiresBearer() throws URISyntaxException, IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var uri = new URI("foo:/bar");
		final var tokenEndpoint = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(uri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpoint);
		when(client.getCredentials()).thenReturn(clientCredentials);
		IuAuthorizationClient.initialize(client);

		final var grant = new ClientCredentialsGrant(realm);
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var hr = mock(HttpRequest.class);
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=client_credentials";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);
			when(hrb.build()).thenReturn(hr);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(tokenEndpoint)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Foo")
					.add("access_token", accessToken).build();
			mockHttpUtils.when(() -> HttpUtils.read(hr)).thenReturn(tokenResponse);

			IuTestLogger.expect("iu.auth.oauth.ClientCredentialsGrant", Level.FINE,
					"Authorization required, initiating client credentials flow for " + realm);
			assertEquals("Unsupported token type Foo in response",
					assertThrows(IllegalStateException.class, () -> grant.authorize(uri)).getMessage());
		}
	}

	@Test
	public void testRequiresSerializablePrincipal() throws URISyntaxException, IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var uri = new URI("foo:/bar");
		final var tokenEndpoint = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var principal = mock(Principal.class);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(uri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpoint);
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.verify(any())).thenReturn(new Subject(true, Set.of(principal), Set.of(), Set.of()));
		IuAuthorizationClient.initialize(client);

		final var grant = new ClientCredentialsGrant(realm);
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var hr = mock(HttpRequest.class);
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=client_credentials";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);
			when(hrb.build()).thenReturn(hr);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(tokenEndpoint)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).build();
			mockHttpUtils.when(() -> HttpUtils.read(hr)).thenReturn(tokenResponse);

			IuTestLogger.expect("iu.auth.oauth.ClientCredentialsGrant", Level.FINE,
					"Authorization required, initiating client credentials flow for " + realm);
			assertInstanceOf(NotSerializableException.class,
					assertThrows(IllegalStateException.class, () -> grant.authorize(uri)).getCause());
		}
	}

}
