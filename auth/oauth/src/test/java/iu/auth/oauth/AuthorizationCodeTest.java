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
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

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
public class AuthorizationCodeTest {

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
				() -> grant.verify(
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
		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var tokenEndpointUri = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		final var principal = new MockPrincipal();
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(Map.of("foo", "bar"));
		when(client.getScope()).thenReturn(List.of("foo", "bar"));
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.verify(any())).thenReturn(new Subject(true, Set.of(principal), Set.of(), Set.of()));
		when(client.verify(any(), any())).thenReturn(new Subject(true, Set.of(principal), Set.of(), Set.of()));
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
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var hr = mock(HttpRequest.class);
			final var hrb = mock(HttpRequest.Builder.class);
			final var hrb2 = mock(HttpRequest.Builder.class);
			final var hrb3 = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=authorization_code&code=" + code
					+ "&scope=foo+bar&redirect_uri=foo%3A%2Fbaz";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);
			when(hrb.build()).thenReturn(hr);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(tokenEndpointUri)).thenReturn(hrb, hrb2, hrb3);

			final var accessToken = IdGenerator.generateId();
			final var refreshToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).add("refresh_token", refreshToken).add("expires_in", 1).build();
			mockHttpUtils.when(() -> HttpUtils.read(hr)).thenReturn(tokenResponse);

			assertEquals(resourceUri, grant.authorize(code, state));

			final var cred = grant.authorize(resourceUri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload));
			verify(hrb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb).POST(bp);
			verify(clientCredentials).applyTo(hrb);
			assertInstanceOf(IuBearerAuthCredentials.class, cred);
			assertEquals(principal.getName(), cred.getName());
			assertSame(cred, grant.authorize(resourceUri));
			assertEquals(accessToken, ((IuBearerAuthCredentials) cred).getAccessToken());

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
			mockHttpUtils.when(() -> HttpUtils.read(hr2)).thenReturn(tokenResponse2);

			final var cred2 = grant.authorize(resourceUri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload2));
			verify(hrb2).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb2).POST(bp2);
			verify(clientCredentials).applyTo(hrb2);
			assertInstanceOf(IuBearerAuthCredentials.class, cred2);
			assertEquals(principal.getName(), cred2.getName());
			assertSame(cred2, grant.authorize(resourceUri));
			assertNotEquals(cred, cred2);
			assertNotEquals(cred.hashCode(), cred2.hashCode());
			assertEquals(accessToken2, ((IuBearerAuthCredentials) cred2).getAccessToken());

			Thread.sleep(1001L);
			final var hr3 = mock(HttpRequest.class);
			final var payload3 = "grant_type=refresh_token&refresh_token=" + refreshToken2 + "&scope=foo+bar";
			final var bp3 = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload3)).thenReturn(bp3);
			when(hrb3.POST(bp3)).thenReturn(hrb3);
			when(hrb3.build()).thenReturn(hr3);
			final var accessToken3 = IdGenerator.generateId();
			final var tokenResponse3 = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken3).add("expires_in", 1).build();
			mockHttpUtils.when(() -> HttpUtils.read(hr3)).thenReturn(tokenResponse3);

			final var cred3 = grant.authorize(resourceUri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload3));
			verify(hrb3).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb3).POST(bp3);
			verify(clientCredentials).applyTo(hrb3);
			assertInstanceOf(IuBearerAuthCredentials.class, cred3);
			assertEquals(principal.getName(), cred3.getName());
			assertSame(cred3, grant.authorize(resourceUri));
			assertEquals(accessToken3, ((IuBearerAuthCredentials) cred3).getAccessToken());
		}
	}

	@Test
	public void testAuthNoRefresh() throws URISyntaxException, IuAuthenticationException, InterruptedException {
		final var realm = IdGenerator.generateId();
		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var tokenEndpointUri = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		final var principal = new MockPrincipal();
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(Map.of("foo", "bar"));
		when(client.getScope()).thenReturn(List.of("foo", "bar"));
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.verify(any())).thenReturn(new Subject(true, Set.of(principal), Set.of(), Set.of()));
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
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var hr = mock(HttpRequest.class);
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=authorization_code&code=" + code
					+ "&scope=foo+bar&redirect_uri=foo%3A%2Fbaz";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);
			when(hrb.build()).thenReturn(hr);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(tokenEndpointUri)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var refreshToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).add("refresh_token", refreshToken).add("expires_in", 1).build();
			mockHttpUtils.when(() -> HttpUtils.read(hr)).thenReturn(tokenResponse);

			assertEquals(resourceUri, grant.authorize(code, state));

			final var cred = grant.authorize(resourceUri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload));
			verify(hrb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb).POST(bp);
			verify(clientCredentials).applyTo(hrb);
			assertInstanceOf(IuBearerAuthCredentials.class, cred);
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
		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var tokenEndpointUri = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		final var principal = new MockPrincipal();
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(Map.of("foo", "bar"));
		when(client.getScope()).thenReturn(List.of("foo", "bar"));
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.verify(any())).thenReturn(new Subject(true, Set.of(principal), Set.of(), Set.of()));
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
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			final var hr = mock(HttpRequest.class);
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=authorization_code&code=" + code
					+ "&scope=foo+bar&redirect_uri=foo%3A%2Fbaz";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);
			when(hrb.build()).thenReturn(hr);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(tokenEndpointUri)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).add("expires_in", 1).build();
			mockHttpUtils.when(() -> HttpUtils.read(hr)).thenReturn(tokenResponse);

			assertEquals(resourceUri, grant.authorize(code, state));

			final var cred = grant.authorize(resourceUri);
			mockBodyPublishers.verify(() -> BodyPublishers.ofString(payload));
			verify(hrb).header("Content-Type", "application/x-www-form-urlencoded");
			verify(hrb).POST(bp);
			verify(clientCredentials).applyTo(hrb);
			assertInstanceOf(IuBearerAuthCredentials.class, cred);
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
