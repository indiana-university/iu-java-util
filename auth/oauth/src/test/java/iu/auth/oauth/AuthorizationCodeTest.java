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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuBearerToken;
import edu.iu.client.IuHttp;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class AuthorizationCodeTest extends IuOAuthTestCase {

	@Test
	public void testRequiresValidClientAndResourceUri() throws URISyntaxException {
		final var realm = IdGenerator.generateId();
		final var resourceUri = new URI("foo:/bar");
		assertThrows(IllegalStateException.class, () -> new AuthorizationCodeGrant(realm, resourceUri));
		final var client = mock(IuAuthorizationClient.class);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		IuAuthorizationClient.initialize(client);
		assertThrows(IllegalArgumentException.class, () -> new AuthorizationCodeGrant(realm, new URI("foo:/baz")));
		assertDoesNotThrow(() -> new AuthorizationCodeGrant(realm, resourceUri));
		assertThrows(IllegalArgumentException.class,
				() -> new AuthorizationCodeGrant(realm, resourceUri).authorize(new URI("foo:/baz")));
	}

	@Test
	public void testValidRequiresBearer() throws URISyntaxException {
		final var realm = IdGenerator.generateId();
		final var resourceUri = new URI("foo:/bar");
		assertThrows(IllegalStateException.class, () -> new AuthorizationCodeGrant(realm, resourceUri));
		final var client = mock(IuAuthorizationClient.class);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		IuAuthorizationClient.initialize(client);

		final var grant = new AuthorizationCodeGrant(realm, resourceUri);
		assertThrows(IllegalArgumentException.class,
				() -> grant.authorize(
						new TokenResponse(null, null,
								Json.createObjectBuilder().add("token_type", "Foo").add("access_token", "bar").build()),
						null));
	}

	@Test
	public void testStateMismatch() throws URISyntaxException, IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var resourceUri = new URI("foo:/bar");
		final var client = mock(IuAuthorizationClient.class);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		IuAuthorizationClient.initialize(client);
		final var grant = new AuthorizationCodeGrant(realm, resourceUri);
		assertNull(grant.authorize("foo", "bar"));
	}

	@Test
	public void testCantOverrideStandardAttribute() throws URISyntaxException, InterruptedException {
		final var realm = IdGenerator.generateId();
		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(Map.of("response_type", "foobar"));
		when(client.getAuthenticationTimeout()).thenReturn(Duration.ofMillis(1L));
		when(client.getCredentials()).thenReturn(clientCredentials);
		IuAuthorizationClient.initialize(client);

		final var grant = new AuthorizationCodeGrant(realm, resourceUri);
		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorization required, initiating authorization code flow for " + realm);
		assertThrows(IllegalArgumentException.class, () -> grant.authorize(resourceUri));
	}

	@Test
	public void testInitialRedirect() throws URISyntaxException, InterruptedException {
		final var realm = IdGenerator.generateId();
		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(null);
		when(client.getAuthenticationTimeout()).thenReturn(Duration.ofMillis(1L));
		when(client.getCredentials()).thenReturn(clientCredentials);
		IuAuthorizationClient.initialize(client);

		final var grant = new AuthorizationCodeGrant(realm, resourceUri);
		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorization required, initiating authorization code flow for " + realm);
		assertThrows(IuAuthenticationException.class, () -> grant.authorize(resourceUri));
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorization required, initiating authorization code flow for " + realm);
		final var authException = assertThrows(IuAuthenticationException.class, () -> grant.authorize(resourceUri));
		final var matcher = Pattern.compile("Bearer realm=\"" + realm + "\" state=\"([0-9A-Za-z_\\-]{32})\"")
				.matcher(authException.getMessage());
		assertTrue(matcher.matches(), authException::getMessage);
		final var state = matcher.group(1);
		assertEquals(new URI("foo:/authorize?client_id=" + clientId
				+ "&response_type=code&redirect_uri=foo%3A%2Fbaz&state=" + state), authException.getLocation());

		Thread.sleep(2L);

		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINER, "Pruning invalid/expired state .*",
				IllegalArgumentException.class);
		final var timeoutChallenge = assertThrows(IuAuthenticationException.class, () -> grant.authorize("foo", state));
		assertEquals(
				"Bearer realm=\"" + realm
						+ "\" error=\"invalid_request\" error_description=\"invalid or expired state\"",
				timeoutChallenge.getMessage());
		assertEquals(resourceUri, timeoutChallenge.getLocation());
	}

	@Test
	public void testFullAuthLifecycle() throws URISyntaxException, IuAuthenticationException, InterruptedException,
			IOException, ClassNotFoundException {
		final var realm = IdGenerator.generateId();
		MockPrincipal.registerVerifier(realm);

		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var tokenEndpointUri = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		final var principal = new MockPrincipal(realm);
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(Map.of("foo", "bar"));
		when(client.getScope()).thenReturn(List.of("foo", "bar"));
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.verify(any())).thenReturn(principal);
		when(client.verify(any(), any())).thenReturn(principal);
		when(client.getAuthorizationTimeToLive()).thenReturn(Duration.ofSeconds(15L));
		IuAuthorizationClient.initialize(client);

		final var grant = new AuthorizationCodeGrant(realm, resourceUri);
		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorization required, initiating authorization code flow for " + realm);
		assertThrows(IuAuthenticationException.class, () -> grant.authorize(resourceUri));
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorization required, initiating authorization code flow for " + realm);
		final var authException = assertThrows(IuAuthenticationException.class, () -> grant.authorize(resourceUri));
		final var matcher = Pattern
				.compile("Bearer realm=\"" + realm + "\" scope=\"foo bar\" state=\"([0-9A-Za-z_\\-]{32})\" foo=\"bar\"")
				.matcher(authException.getMessage());
		assertTrue(matcher.matches(), authException::getMessage);
		final var state = matcher.group(1);
		assertEquals(
				new URI("foo:/authorize?client_id=" + clientId
						+ "&response_type=code&redirect_uri=foo%3A%2Fbaz&scope=foo+bar&state=" + state + "&foo=bar"),
				authException.getLocation());

		final var code = IdGenerator.generateId();
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttp = mockStatic(IuHttp.class)) {

			final var hrb = mock(HttpRequest.Builder.class);
			final var hrb2 = mock(HttpRequest.Builder.class);
			final var hrb3 = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=authorization_code&code=" + code
					+ "&scope=foo+bar&redirect_uri=foo%3A%2Fbaz";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var refreshToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).add("refresh_token", refreshToken).add("expires_in", 1).build();
			mockHttp.when(() -> IuHttp.send(eq(IuAuthenticationException.class), eq(tokenEndpointUri), argThat(a -> {
				IuException.unchecked(() -> a.accept(hrb));
				return true;
			}), eq(AbstractGrant.JSON_OBJECT_NOCACHE))).thenReturn(tokenResponse);

			assertEquals(resourceUri, grant.authorize(code, state));

			final var cred = assertInstanceOf(BearerToken.class, grant.authorize(resourceUri));
			IuPrincipalIdentity.verify(cred, realm);

			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload));
			verify(hrb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb).POST(bp);
			verify(clientCredentials).applyTo(hrb);
			assertInstanceOf(IuBearerToken.class, cred);
			assertEquals(principal.getName(), cred.getName());
			assertSame(cred, grant.authorize(resourceUri));
			assertEquals(accessToken, ((IuBearerToken) cred).getAccessToken());

			// emulate session storage
			final var serialized = new ByteArrayOutputStream();
			try (final var o = new ObjectOutputStream(serialized)) {
				o.writeObject(cred);
			}
			try (final var i = new ObjectInputStream(new ByteArrayInputStream(serialized.toByteArray()))) {
				assertEquals(cred, i.readObject());
			}

			Thread.sleep(1001L);
			final var hr2 = mock(HttpRequest.class);
			final var payload2 = "grant_type=refresh_token&refresh_token=" + refreshToken + "&scope=foo+bar";
			final var bp2 = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload2)).thenReturn(bp2);
			when(hrb2.POST(bp2)).thenReturn(hrb2);
			when(hrb2.build()).thenReturn(hr2);
			final var accessToken2 = IdGenerator.generateId();
			final var refreshToken2 = IdGenerator.generateId();
			final var tokenResponse2 = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken2).add("refresh_token", refreshToken2).add("expires_in", 1).build();
			mockHttp.when(() -> IuHttp.send(eq(IuAuthenticationException.class), eq(tokenEndpointUri), argThat(a -> {
				IuException.unchecked(() -> a.accept(hrb2));
				return true;
			}), eq(AbstractGrant.JSON_OBJECT_NOCACHE))).thenReturn(tokenResponse2);

			final var cred2 = assertInstanceOf(IuBearerToken.class, grant.authorize(resourceUri));
			IuPrincipalIdentity.verify(cred2, realm);

			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload2));
			verify(hrb2).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb2).POST(bp2);
			verify(clientCredentials).applyTo(hrb2);
			assertSame(cred2, grant.authorize(resourceUri));
			assertEquals(principal.getName(), cred2.getName());
			assertEquals(cred.getName(), cred2.getName());
			assertEquals(cred.getSubject(), cred2.getSubject());
			assertEquals(cred.getScope(), cred2.getScope());
			assertEquals(accessToken2, ((IuBearerToken) cred2).getAccessToken());

			Thread.sleep(1001L);
			final var hr3 = mock(HttpRequest.class);
			final var payload3 = "grant_type=refresh_token&refresh_token=" + refreshToken2 + "&scope=foo+bar";
			final var bp3 = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload3)).thenReturn(bp3);
			when(hrb3.POST(bp3)).thenReturn(hrb3);
			when(hrb3.build()).thenReturn(hr3);
			final var accessToken3 = IdGenerator.generateId();
			final var tokenResponse3 = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken3).add("expires_in", 0).build();
			mockHttp.when(() -> IuHttp.send(eq(IuAuthenticationException.class), eq(tokenEndpointUri), argThat(a -> {
				IuException.unchecked(() -> a.accept(hrb3));
				return true;
			}), eq(AbstractGrant.JSON_OBJECT_NOCACHE))).thenReturn(tokenResponse3);

			final var cred3 = grant.authorize(resourceUri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload3));
			verify(hrb3).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb3).POST(bp3);
			verify(clientCredentials).applyTo(hrb3);
			assertInstanceOf(IuBearerToken.class, cred3);
			assertEquals(principal.getName(), cred3.getName());
			assertSame(cred3, grant.authorize(resourceUri));
			assertEquals(accessToken3, ((IuBearerToken) cred3).getAccessToken());
		}
	}

	@Test
	public void testAuthNoRefresh() throws URISyntaxException, IuAuthenticationException, InterruptedException {
		final var realm = IdGenerator.generateId();
		MockPrincipal.registerVerifier(realm);

		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var tokenEndpointUri = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		final var principal = new MockPrincipal(realm);
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(Map.of("foo", "bar"));
		when(client.getScope()).thenReturn(List.of("foo", "bar"));
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.verify(any())).thenReturn(principal);
		when(client.getAuthorizationTimeToLive()).thenReturn(Duration.ofSeconds(15L));
		IuAuthorizationClient.initialize(client);

		final var grant = new AuthorizationCodeGrant(realm, resourceUri);
		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorization required, initiating authorization code flow for " + realm);
		final var authException = assertThrows(IuAuthenticationException.class, () -> grant.authorize(resourceUri));
		final var matcher = Pattern
				.compile("Bearer realm=\"" + realm + "\" scope=\"foo bar\" state=\"([0-9A-Za-z_\\-]{32})\" foo=\"bar\"")
				.matcher(authException.getMessage());
		assertTrue(matcher.matches(), authException::getMessage);
		final var state = matcher.group(1);
		assertEquals(
				new URI("foo:/authorize?client_id=" + clientId
						+ "&response_type=code&redirect_uri=foo%3A%2Fbaz&scope=foo+bar&state=" + state + "&foo=bar"),
				authException.getLocation());

		final var code = IdGenerator.generateId();
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttp = mockStatic(IuHttp.class)) {
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=authorization_code&code=" + code
					+ "&scope=foo+bar&redirect_uri=foo%3A%2Fbaz";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var refreshToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).add("refresh_token", refreshToken).add("expires_in", 1).build();
			mockHttp.when(() -> IuHttp.send(eq(IuAuthenticationException.class), eq(tokenEndpointUri), argThat(a -> {
				IuException.unchecked(() -> a.accept(hrb));
				return true;
			}), eq(AbstractGrant.JSON_OBJECT_NOCACHE))).thenReturn(tokenResponse);

			assertEquals(resourceUri, grant.authorize(code, state));

			final var cred = grant.authorize(resourceUri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload));
			verify(hrb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb).POST(bp);
			verify(clientCredentials).applyTo(hrb);
			assertInstanceOf(IuBearerToken.class, cred);
			assertEquals(principal.getName(), cred.getName());
			assertSame(cred, grant.authorize(resourceUri));
		}

		Thread.sleep(1001L);
		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.INFO, "Refresh token failed",
				IllegalArgumentException.class);
		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorized session has expired, initiating authorization code flow for " + realm);
		final var authException2 = assertThrows(IuAuthenticationException.class, () -> grant.authorize(resourceUri));
		final var matcher2 = Pattern.compile("Bearer realm=\"" + realm
				+ "\" error=\"invalid_token\" error_description=\"expired access token, refresh attempt failed\" scope=\"foo bar\" state=\"([0-9A-Za-z_\\-]{32})\" foo=\"bar\"")
				.matcher(authException2.getMessage());
		assertTrue(matcher2.matches(), authException2::getMessage);
		final var state2 = matcher2.group(1);
		assertNotEquals(state, state2);
		assertEquals(
				new URI("foo:/authorize?client_id=" + clientId
						+ "&response_type=code&redirect_uri=foo%3A%2Fbaz&scope=foo+bar&state=" + state2 + "&foo=bar"),
				authException2.getLocation());

	}

	@Test
	public void testAuthRefreshFails() throws URISyntaxException, IuAuthenticationException, InterruptedException {
		final var realm = IdGenerator.generateId();
		MockPrincipal.registerVerifier(realm);

		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var tokenEndpointUri = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		final var principal = new MockPrincipal(realm);
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(Map.of("foo", "bar"));
		when(client.getScope()).thenReturn(List.of("foo", "bar"));
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.verify(any())).thenReturn(principal);
		when(client.getAuthorizationTimeToLive()).thenReturn(Duration.ofSeconds(15L));
		IuAuthorizationClient.initialize(client);

		final var grant = new AuthorizationCodeGrant(realm, resourceUri);
		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorization required, initiating authorization code flow for " + realm);
		final var authException = assertThrows(IuAuthenticationException.class, () -> grant.authorize(resourceUri));
		final var matcher = Pattern
				.compile("Bearer realm=\"" + realm + "\" scope=\"foo bar\" state=\"([0-9A-Za-z_\\-]{32})\" foo=\"bar\"")
				.matcher(authException.getMessage());
		assertTrue(matcher.matches(), authException::getMessage);
		final var state = matcher.group(1);
		assertEquals(
				new URI("foo:/authorize?client_id=" + clientId
						+ "&response_type=code&redirect_uri=foo%3A%2Fbaz&scope=foo+bar&state=" + state + "&foo=bar"),
				authException.getLocation());

		final var code = IdGenerator.generateId();
		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttp = mockStatic(IuHttp.class)) {
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=authorization_code&code=" + code
					+ "&scope=foo+bar&redirect_uri=foo%3A%2Fbaz";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).add("expires_in", 1).build();
			mockHttp.when(() -> IuHttp.send(eq(IuAuthenticationException.class), eq(tokenEndpointUri), argThat(a -> {
				IuException.unchecked(() -> a.accept(hrb));
				return true;
			}), eq(AbstractGrant.JSON_OBJECT_NOCACHE))).thenReturn(tokenResponse);

			assertEquals(resourceUri, grant.authorize(code, state));

			final var cred = grant.authorize(resourceUri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload));
			verify(hrb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb).POST(bp);
			verify(clientCredentials).applyTo(hrb);
			assertInstanceOf(IuBearerToken.class, cred);
			assertEquals(principal.getName(), cred.getName());
			assertSame(cred, grant.authorize(resourceUri));
		}

		Thread.sleep(1001L);
		IuTestLogger.expect("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE,
				"Authorized session has expired, initiating authorization code flow for " + realm);
		final var authException2 = assertThrows(IuAuthenticationException.class, () -> grant.authorize(resourceUri));
		final var matcher2 = Pattern.compile("Bearer realm=\"" + realm
				+ "\" error=\"invalid_token\" error_description=\"expired access token\" scope=\"foo bar\" state=\"([0-9A-Za-z_\\-]{32})\" foo=\"bar\"")
				.matcher(authException2.getMessage());
		assertTrue(matcher2.matches(), authException2::getMessage);
		final var state2 = matcher2.group(1);
		assertNotEquals(state, state2);
		assertEquals(
				new URI("foo:/authorize?client_id=" + clientId
						+ "&response_type=code&redirect_uri=foo%3A%2Fbaz&scope=foo+bar&state=" + state2 + "&foo=bar"),
				authException2.getLocation());

	}

}
