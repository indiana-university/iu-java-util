package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuBadRequestException;
import edu.iu.IuIterable;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuRequestAttributes;
import edu.iu.auth.config.IuOidcClient;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.auth.session.IuSession;
import edu.iu.auth.session.IuSessionHandler;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OidcAuthorizatonResourceTest {

	static {
		AuthConfig.registerInterface(IuOpenIdProviderMetadata.class);
	}

	private IuOidcClient oidcClient;
	private IuSessionHandler sessionHandler;
	private OidcPostAuthSession postAuthHandled;
	private String clientId;
	private URI oidcMetadataUri;
	private URI resourceUri;
	private URI redirectUri;
	private URI issuer;
	private URI jwksUri;
	private URI authorizationEndpoint;
	private URI tokenEndpoint;
	private URI userinfoEndpoint;
	private MockedStatic<IuHttp> http;

	private OidcAuthorizationResource resource = new OidcAuthorizationResource() {
		@Override
		protected void handlePostAuth(OidcPostAuthSession postAuth) {
			postAuthHandled = postAuth;
		}

		@Override
		protected IuSessionHandler getSessionHandler() {
			return sessionHandler;
		}

		@Override
		protected IuOidcClient getOidcClient() {
			return oidcClient;
		}
	};

	@BeforeEach
	void setup() {
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
		IuTestLogger.allow("iu.crypt", Level.FINE);

		issuer = URI.create(IdGenerator.generateId());
		jwksUri = URI.create(IdGenerator.generateId());
		authorizationEndpoint = URI.create(IdGenerator.generateId());
		tokenEndpoint = URI.create(IdGenerator.generateId());
		userinfoEndpoint = URI.create(IdGenerator.generateId());

		clientId = IdGenerator.generateId();
		oidcMetadataUri = URI.create(IdGenerator.generateId());
		resourceUri = URI.create(IdGenerator.generateId());
		redirectUri = URI.create(IdGenerator.generateId());
		oidcClient = mock(IuOidcClient.class);
		when(oidcClient.getClientId()).thenReturn(clientId);
		when(oidcClient.getResourceUri()).thenReturn(resourceUri);
		when(oidcClient.getRedirectUri()).thenReturn(redirectUri);
		when(oidcClient.getMetadataUri()).thenReturn(oidcMetadataUri);
		when(oidcClient.getMetadataRefreshInterval()).thenReturn(Duration.ofSeconds(1L));

		http = mockStatic(IuHttp.class);
		http.when(() -> IuHttp.get(oidcMetadataUri, IuHttp.READ_JSON_OBJECT))
				.thenReturn(IuJson.object().add("issuer", issuer.toString()) //
						.add("jwks_uri", jwksUri.toString()) //
						.add("authorization_endpoint", authorizationEndpoint.toString()) //
						.add("token_endpoint", tokenEndpoint.toString()) //
						.add("userinfo_endpoint", userinfoEndpoint.toString()) //
						.build());

		sessionHandler = mock(IuSessionHandler.class);

		postAuthHandled = null;
	}

	@AfterEach
	void teardown() {
		http.close();
	}

	@Test
	void testOidcMetadata() {
		http.when(() -> IuHttp.get(oidcMetadataUri, IuHttp.READ_JSON_OBJECT)).thenThrow(IllegalStateException.class);
		assertThrows(IllegalStateException.class, () -> resource.oidcProviderMetadata());

		http.when(() -> IuHttp.get(oidcMetadataUri, IuHttp.READ_JSON_OBJECT))
				.thenReturn(IuJson.object().add("issuer", issuer.toString()) //
						.build());
		http.clearInvocations();
		assertEquals(issuer, resource.oidcProviderMetadata().getIssuer());
		assertEquals(issuer, resource.oidcProviderMetadata().getIssuer());
		http.verify(() -> IuHttp.get(oidcMetadataUri, IuHttp.READ_JSON_OBJECT));

		assertDoesNotThrow(() -> Thread.sleep(1000L));
		assertEquals(issuer, resource.oidcProviderMetadata().getIssuer());
		http.verify(() -> IuHttp.get(oidcMetadataUri, IuHttp.READ_JSON_OBJECT), times(2));

		assertDoesNotThrow(() -> Thread.sleep(1000L));
		http.when(() -> IuHttp.get(oidcMetadataUri, IuHttp.READ_JSON_OBJECT)).thenThrow(IllegalStateException.class);
		IuTestLogger.expect(OidcAuthorizationResource.class.getName(), Level.INFO,
				"OIDC provider metadata lookup failure " + oidcMetadataUri + "; using last good version",
				IllegalStateException.class);
		assertEquals(issuer, resource.oidcProviderMetadata().getIssuer());
	}

	@Test
	void testInit() {
		final var preAuth = mock(OidcPreAuthSession.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);
		when(sessionHandler.create()).thenReturn(session);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var redirect = resource.init(null, null);
		assertEquals(setCookie, redirect.getSetCookie());

		class StringListener implements ArgumentMatcher<String> {
			String value;

			@Override
			public boolean matches(String argument) {
				value = argument;
				return true;
			}
		}
		final var nonceVerifier = new StringListener();
		final var stateVerifier = new StringListener();
		verify(preAuth).setNonce(argThat(nonceVerifier));
		verify(preAuth).setState(argThat(stateVerifier));

		assertEquals(URI.create(authorizationEndpoint + "?response_type=code&client_id=" + clientId
				+ "&scope=openid&nonce=" + nonceVerifier.value + "&resource=" + resourceUri + "&redirect_uri="
				+ redirectUri + "&state=" + stateVerifier.value), redirect.getLocation());
	}

	@Test
	void testInitImpersonatedAndDelegated() {
		final var preAuth = mock(OidcPreAuthSession.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);
		when(sessionHandler.create()).thenReturn(session);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var impersonated = IdGenerator.generateId();
		final var delegating = IdGenerator.generateId();
		final var redirect = resource.init(delegating, impersonated);
		assertEquals(setCookie, redirect.getSetCookie());

		class StringListener implements ArgumentMatcher<String> {
			String value;

			@Override
			public boolean matches(String argument) {
				value = argument;
				return true;
			}
		}
		final var nonceVerifier = new StringListener();
		final var stateVerifier = new StringListener();
		verify(preAuth).setNonce(argThat(nonceVerifier));
		verify(preAuth).setState(argThat(stateVerifier));

		assertEquals(URI.create(authorizationEndpoint + "?response_type=code&client_id=" + clientId
				+ "&scope=openid&nonce=" + nonceVerifier.value + "&resource=" + resourceUri + "&redirect_uri="
				+ redirectUri + "&state=" + stateVerifier.value + "&delegating_principal=" + delegating
				+ "&impersonated_principal=" + impersonated), redirect.getLocation());
	}

	@Test
	void testAuthorizeMissingPreAuth() {
		final var attributes = mock(IuRequestAttributes.class);
		final var code = IdGenerator.generateId();
		final var state = IdGenerator.generateId();
		assertEquals("missing or expired preAuth session",
				assertThrows(IllegalStateException.class, () -> resource.authorize(attributes, code, state))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeStateMismatch() {
		final var cookies = mock(Iterable.class);
		final var attributes = mock(IuRequestAttributes.class);
		when(attributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();
		final var state = IdGenerator.generateId();

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		assertEquals("state mismatch " + state + " preAuth=" + preAuth,
				assertThrows(IllegalStateException.class, () -> resource.authorize(attributes, code, state))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorize() {
		final var cookies = mock(Iterable.class);
		final var requestUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var userAgent = IdGenerator.generateId();
		final var attributes = mock(IuRequestAttributes.class);
		when(attributes.getRequestUri()).thenReturn(requestUri);
		when(attributes.getRemoteAddr()).thenReturn(remoteAddr);
		when(attributes.getUserAgent()).thenReturn(userAgent);
		when(attributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();
		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(preAuth.getNonce()).thenReturn(nonce);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var akid = IdGenerator.generateId();
		final var assertKey = WebKey.builder(Algorithm.EDDSA).keyId(akid).ephemeral().build();
		when(oidcClient.getAssertionJwk()).thenReturn(assertKey);
		when(oidcClient.getAssertionTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getTokenTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getMaxAge()).thenReturn(Duration.ofHours(1L));

		final var dkid = IdGenerator.generateId();
		final var decryptKey = WebKey.builder(WebKey.Type.X25519).algorithm(Algorithm.ECDH_ES).keyId(dkid).ephemeral()
				.build();
		when(oidcClient.getDecryptJwk()).thenReturn(IuIterable.iter(decryptKey));

		final var kid = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();

		final var accessToken = IdGenerator.generateId();
		final var refreshToken = IdGenerator.generateId();
		final var authnPrincipal = IdGenerator.generateId();
		final var authTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5L);
		final var exp = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(15L);
		final var idTokenBuilder = OidcIdToken.builder(Algorithm.EDDSA, clientId, Duration.ofHours(12L)) //
				.jti() //
				.iss(issuer) //
				.aud(URI.create(clientId)) //
				.sub(authnPrincipal) //
				.authTime(authTime) //
				.iat() //
				.exp(exp) //
				.nonce(nonce) //
				.accessToken(accessToken) //
		;

		final var idClaims = idTokenBuilder.build();
		final var idToken = idClaims.sign("JWT", Algorithm.EDDSA, issuerKey);
		final var encryptedIdToken = WebEncryption.builder(Encryption.A256GCM).compact().addRecipient(Algorithm.ECDH_ES)
				.keyId(dkid).key(decryptKey.wellKnown()).contentType("JWT").encrypt(idToken).compact();

		http.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(b -> {
					final var params = IuWebUtils.parseQueryString(b);
					assertEquals("authorization_code", params.get("grant_type").iterator().next());
					assertEquals(code, params.get("code").iterator().next());
					assertEquals(nonce, params.get("nonce").iterator().next());
					assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
							params.get("client_assertion_type").iterator().next());

					final var assertion = new SelfIssuedAccessToken(assertKey.wellKnown(), URI.create(clientId),
							tokenEndpoint, oidcClient.getAssertionTtl(),
							params.get("client_assertion").iterator().next());
					assertEquals(clientId, assertion.getName());

					return true;
				}))).thenReturn(bp);
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Content-Type", "application/x-www-form-urlencoded");
				verify(rb).POST(bp);
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("id_token", encryptedIdToken) //
				.add("access_token", accessToken) //
				.add("refresh_token", refreshToken) //
				.add("expires_in", 15) //
				.build());

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		try (final var mockWebKey = mockStatic(WebKey.class, CALLS_REAL_METHODS)) {
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).thenReturn(IuIterable.iter(issuerKey));
			final var redirect = resource.authorize(attributes, code, state);
			verify(postAuth).setIdToken(idToken);
			verify(postAuth).setAccessToken(accessToken);
			verify(postAuth).setRefreshToken(refreshToken);
			verify(postAuth).setNotAfter(any(Instant.class));
			assertEquals(setCookie, redirect.getSetCookie());
			assertEquals(resourceUri, redirect.getLocation());
			assertEquals(postAuth, postAuthHandled);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeUnencrypted() {
		final var cookies = mock(Iterable.class);
		final var requestUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var userAgent = IdGenerator.generateId();
		final var attributes = mock(IuRequestAttributes.class);
		when(attributes.getRequestUri()).thenReturn(requestUri);
		when(attributes.getRemoteAddr()).thenReturn(remoteAddr);
		when(attributes.getUserAgent()).thenReturn(userAgent);
		when(attributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();
		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(preAuth.getNonce()).thenReturn(nonce);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var akid = IdGenerator.generateId();
		final var assertKey = WebKey.builder(Algorithm.EDDSA).keyId(akid).ephemeral().build();
		when(oidcClient.getAssertionJwk()).thenReturn(assertKey);
		when(oidcClient.getAssertionTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getTokenTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getMaxAge()).thenReturn(Duration.ofHours(1L));

		when(oidcClient.getDecryptJwk()).thenReturn(null);

		final var kid = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();

		final var accessToken = IdGenerator.generateId();
		final var refreshToken = IdGenerator.generateId();
		final var authnPrincipal = IdGenerator.generateId();
		final var authTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5L);
		final var exp = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(15L);
		final var idTokenBuilder = OidcIdToken.builder(Algorithm.EDDSA, clientId, Duration.ofHours(12L)) //
				.jti() //
				.iss(issuer) //
				.aud(URI.create(clientId)) //
				.sub(authnPrincipal) //
				.authTime(authTime) //
				.iat() //
				.exp(exp) //
				.nonce(nonce) //
				.accessToken(accessToken) //
		;

		final var idClaims = idTokenBuilder.build();
		final var idToken = idClaims.sign("JWT", Algorithm.EDDSA, issuerKey);

		http.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(b -> {
					final var params = IuWebUtils.parseQueryString(b);
					assertEquals("authorization_code", params.get("grant_type").iterator().next());
					assertEquals(code, params.get("code").iterator().next());
					assertEquals(nonce, params.get("nonce").iterator().next());
					assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
							params.get("client_assertion_type").iterator().next());

					final var assertion = new SelfIssuedAccessToken(assertKey.wellKnown(), URI.create(clientId),
							tokenEndpoint, oidcClient.getAssertionTtl(),
							params.get("client_assertion").iterator().next());
					assertEquals(clientId, assertion.getName());

					return true;
				}))).thenReturn(bp);
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Content-Type", "application/x-www-form-urlencoded");
				verify(rb).POST(bp);
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("id_token", idToken) //
				.add("access_token", accessToken) //
				.add("refresh_token", refreshToken) //
				.add("expires_in", 15) //
				.build());

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		try (final var mockWebKey = mockStatic(WebKey.class, CALLS_REAL_METHODS)) {
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).thenReturn(IuIterable.iter(issuerKey));
			final var redirect = resource.authorize(attributes, code, state);
			verify(postAuth).setIdToken(idToken);
			verify(postAuth).setAccessToken(accessToken);
			verify(postAuth).setRefreshToken(refreshToken);
			verify(postAuth).setNotAfter(any(Instant.class));
			assertEquals(setCookie, redirect.getSetCookie());
			assertEquals(resourceUri, redirect.getLocation());
			assertEquals(postAuth, postAuthHandled);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testAuthorizeIssMismatch() {
		final var cookies = mock(Iterable.class);
		final var requestUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var userAgent = IdGenerator.generateId();
		final var attributes = mock(IuRequestAttributes.class);
		when(attributes.getRequestUri()).thenReturn(requestUri);
		when(attributes.getRemoteAddr()).thenReturn(remoteAddr);
		when(attributes.getUserAgent()).thenReturn(userAgent);
		when(attributes.getCookies()).thenReturn(cookies);

		final var code = IdGenerator.generateId();
		final var state = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(preAuth.getState()).thenReturn(state);
		when(preAuth.getNonce()).thenReturn(nonce);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var akid = IdGenerator.generateId();
		final var assertKey = WebKey.builder(Algorithm.EDDSA).keyId(akid).ephemeral().build();
		when(oidcClient.getAssertionJwk()).thenReturn(assertKey);
		when(oidcClient.getAssertionTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getTokenTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getMaxAge()).thenReturn(Duration.ofHours(1L));

		when(oidcClient.getDecryptJwk()).thenReturn(null);

		final var kid = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();

		final var accessToken = IdGenerator.generateId();
		final var refreshToken = IdGenerator.generateId();
		final var authnPrincipal = IdGenerator.generateId();
		final var authTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5L);
		final var exp = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(15L);
		final var idTokenBuilder = OidcIdToken.builder(Algorithm.EDDSA, clientId, Duration.ofHours(12L)) //
				.jti() //
				.iss(tokenEndpoint) //
				.aud(URI.create(clientId)) //
				.sub(authnPrincipal) //
				.authTime(authTime) //
				.iat() //
				.exp(exp) //
				.nonce(nonce) //
				.accessToken(accessToken) //
		;

		final var idClaims = idTokenBuilder.build();
		final var idToken = idClaims.sign("JWT", Algorithm.EDDSA, issuerKey);

		http.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(b -> {
					final var params = IuWebUtils.parseQueryString(b);
					assertEquals("authorization_code", params.get("grant_type").iterator().next());
					assertEquals(code, params.get("code").iterator().next());
					assertEquals(nonce, params.get("nonce").iterator().next());
					assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
							params.get("client_assertion_type").iterator().next());

					final var assertion = new SelfIssuedAccessToken(assertKey.wellKnown(), URI.create(clientId),
							tokenEndpoint, oidcClient.getAssertionTtl(),
							params.get("client_assertion").iterator().next());
					assertEquals(clientId, assertion.getName());

					return true;
				}))).thenReturn(bp);
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Content-Type", "application/x-www-form-urlencoded");
				verify(rb).POST(bp);
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("id_token", idToken) //
				.add("access_token", accessToken) //
				.add("refresh_token", refreshToken) //
				.add("expires_in", 15) //
				.build());

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		try (final var mockWebKey = mockStatic(WebKey.class, CALLS_REAL_METHODS)) {
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).thenReturn(IuIterable.iter(issuerKey));
			assertEquals("iss mismatch in id token",
					assertThrows(IuBadRequestException.class, () -> resource.authorize(attributes, code, state))
							.getMessage());
		}
	}

	@Test
	void testGetPrincipalMissingOrExpired() {
		final var attributes = mock(IuRequestAttributes.class);
		assertEquals("missing or expired authorization session",
				assertThrows(IllegalStateException.class, () -> resource.getAuthorizedPrincipal(attributes))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalMissingNonce() {
		final var attributes = mock(IuRequestAttributes.class);
		final var cookies = mock(Iterable.class);
		when(attributes.getCookies()).thenReturn(cookies);

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		assertEquals("missing pre-auth nonce",
				assertThrows(IllegalStateException.class, () -> resource.getAuthorizedPrincipal(attributes))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalMissingAccessToken() {
		final var attributes = mock(IuRequestAttributes.class);
		final var cookies = mock(Iterable.class);
		when(attributes.getCookies()).thenReturn(cookies);

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var nonce = IdGenerator.generateId();
		when(preAuth.getNonce()).thenReturn(nonce);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var notAfter = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1L);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		assertEquals("missing post-auth access token",
				assertThrows(IllegalStateException.class, () -> resource.getAuthorizedPrincipal(attributes))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalMissingIdToken() {
		final var attributes = mock(IuRequestAttributes.class);
		final var cookies = mock(Iterable.class);
		when(attributes.getCookies()).thenReturn(cookies);

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var nonce = IdGenerator.generateId();
		when(preAuth.getNonce()).thenReturn(nonce);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var notAfter = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1L);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		final var accessToken = IdGenerator.generateId();
		when(postAuth.getAccessToken()).thenReturn(accessToken);

		assertEquals("missing post-auth ID token",
				assertThrows(IllegalStateException.class, () -> resource.getAuthorizedPrincipal(attributes))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalMissingNotAfter() {
		final var attributes = mock(IuRequestAttributes.class);
		final var cookies = mock(Iterable.class);
		when(attributes.getCookies()).thenReturn(cookies);

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var nonce = IdGenerator.generateId();
		when(preAuth.getNonce()).thenReturn(nonce);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var accessToken = IdGenerator.generateId();
		when(postAuth.getAccessToken()).thenReturn(accessToken);

		final var idToken = IdGenerator.generateId();
		when(postAuth.getIdToken()).thenReturn(idToken);

		assertEquals("missing post-auth not-after date",
				assertThrows(IllegalStateException.class, () -> resource.getAuthorizedPrincipal(attributes))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalExpiredNoRefresh() {
		final var attributes = mock(IuRequestAttributes.class);
		final var cookies = mock(Iterable.class);
		when(attributes.getCookies()).thenReturn(cookies);

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var nonce = IdGenerator.generateId();
		when(preAuth.getNonce()).thenReturn(nonce);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var accessToken = IdGenerator.generateId();
		when(postAuth.getAccessToken()).thenReturn(accessToken);

		final var idToken = IdGenerator.generateId();
		when(postAuth.getIdToken()).thenReturn(idToken);

		final var notAfter = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(1L);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		assertEquals("Session expired with no refresh token",
				assertThrows(IllegalStateException.class, () -> resource.getAuthorizedPrincipal(attributes))
						.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipalWithRefresh() {
		final var requestUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var userAgent = IdGenerator.generateId();
		final var cookies = mock(Iterable.class);
		final var attributes = mock(IuRequestAttributes.class);
		when(attributes.getRequestUri()).thenReturn(requestUri);
		when(attributes.getRemoteAddr()).thenReturn(remoteAddr);
		when(attributes.getUserAgent()).thenReturn(userAgent);
		when(attributes.getCookies()).thenReturn(cookies);

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var nonce = IdGenerator.generateId();
		when(preAuth.getNonce()).thenReturn(nonce);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var notAfter = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(1L);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		final var akid = IdGenerator.generateId();
		final var assertKey = WebKey.builder(Algorithm.EDDSA).keyId(akid).ephemeral().build();
		when(oidcClient.getAssertionJwk()).thenReturn(assertKey);
		when(oidcClient.getAssertionTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getTokenTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getMaxAge()).thenReturn(Duration.ofHours(1L));

		when(oidcClient.getDecryptJwk()).thenReturn(null);

		final var kid = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();

		final var refreshToken = IdGenerator.generateId();
		final var newAccessToken = IdGenerator.generateId();
		final var authnPrincipal = IdGenerator.generateId();
		final var authTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5L);
		final var exp = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(15L);
		final var idTokenBuilder = OidcIdToken.builder(Algorithm.EDDSA, clientId, Duration.ofHours(12L)) //
				.jti() //
				.iss(issuer) //
				.aud(URI.create(clientId)) //
				.sub(authnPrincipal) //
				.authTime(authTime) //
				.iat() //
				.exp(exp) //
				.nonce(nonce) //
				.accessToken(newAccessToken) //
		;

		final var idClaims = idTokenBuilder.build();
		final var newIdToken = idClaims.sign("JWT", Algorithm.EDDSA, issuerKey);

		final var newRefreshToken = IdGenerator.generateId();
		http.when(() -> IuHttp.send(eq(tokenEndpoint), argThat(a -> {
			final var bp = mock(BodyPublisher.class);
			try (final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
				mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(b -> {
					final var params = IuWebUtils.parseQueryString(b);
					assertEquals("refresh_token", params.get("grant_type").iterator().next());
					assertEquals(refreshToken, params.get("refresh_token").iterator().next());
					assertEquals(nonce, params.get("nonce").iterator().next());
					assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
							params.get("client_assertion_type").iterator().next());

					final var assertion = new SelfIssuedAccessToken(assertKey.wellKnown(), URI.create(clientId),
							tokenEndpoint, oidcClient.getAssertionTtl(),
							params.get("client_assertion").iterator().next());
					assertEquals(clientId, assertion.getName());

					return true;
				}))).thenReturn(bp);
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Content-Type", "application/x-www-form-urlencoded");
				verify(rb).POST(bp);
			}
			return true;
		}), eq(IuHttp.READ_JSON_OBJECT))).thenReturn(IuJson.object() //
				.add("id_token", newIdToken) //
				.add("access_token", newAccessToken) //
				.add("refresh_token", newRefreshToken) //
				.add("expires_in", 15) //
				.build());

		http.when(() -> IuHttp.send(eq(userinfoEndpoint), argThat(a -> {
			final var rb = mock(HttpRequest.Builder.class);
			assertDoesNotThrow(() -> a.accept(rb));
			verify(rb).header("Authorization", "Bearer " + newAccessToken);
			return true;
		}), eq(IuHttp.READ_UTF8))).thenReturn(IuJson.object() //
				.add("sub", authnPrincipal) //
				.build().toString());

		when(postAuth.getAccessToken()).thenReturn(newAccessToken);
		when(postAuth.getIdToken()).thenReturn(newIdToken);
		when(postAuth.getRefreshToken()).thenReturn(refreshToken, newRefreshToken);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);
		try (final var mockWebKey = mockStatic(WebKey.class, CALLS_REAL_METHODS)) {
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).thenReturn(IuIterable.iter(issuerKey));
			final var principal = resource.getAuthorizedPrincipal(attributes);
			verify(postAuth).setAccessToken(newAccessToken);
			verify(postAuth).setIdToken(newIdToken);
			verify(postAuth).setRefreshToken(newRefreshToken);
			verify(postAuth).setNotAfter(any(Instant.class));
			assertEquals(setCookie, principal.getSetCookie());
			assertEquals(authnPrincipal, principal.getPrincipal().getName());
			assertEquals(postAuth, postAuthHandled);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipal() {
		final var attributes = mock(IuRequestAttributes.class);
		final var cookies = mock(Iterable.class);
		when(attributes.getCookies()).thenReturn(cookies);

		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(OidcPreAuthSession.class);
		when(session.getDetail(OidcPreAuthSession.class)).thenReturn(preAuth);

		final var nonce = IdGenerator.generateId();
		when(preAuth.getNonce()).thenReturn(nonce);

		final var postAuth = mock(OidcPostAuthSession.class);
		when(session.getDetail(OidcPostAuthSession.class)).thenReturn(postAuth);

		final var accessToken = IdGenerator.generateId();
		when(postAuth.getAccessToken()).thenReturn(accessToken);

		final var notAfter = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1L);
		when(postAuth.getNotAfter()).thenReturn(notAfter);

		final var kid = IdGenerator.generateId();
		final var issuerKey = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();

		final var authnPrincipal = IdGenerator.generateId();
		final var authTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5L);
		final var exp = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(15L);
		final var idTokenBuilder = OidcIdToken.builder(Algorithm.EDDSA, clientId, Duration.ofHours(12L)) //
				.jti() //
				.iss(issuer) //
				.aud(URI.create(clientId)) //
				.sub(authnPrincipal) //
				.authTime(authTime) //
				.iat() //
				.exp(exp) //
				.nonce(nonce) //
				.accessToken(accessToken) //
		;

		final var idClaims = idTokenBuilder.build();
		final var idToken = idClaims.sign("JWT", Algorithm.EDDSA, issuerKey);
		when(postAuth.getIdToken()).thenReturn(idToken);

		when(oidcClient.getTokenTtl()).thenReturn(Duration.ofSeconds(15L));
		when(oidcClient.getMaxAge()).thenReturn(Duration.ofHours(1L));

		final var dkid = IdGenerator.generateId();
		final var decryptKey = WebKey.builder(WebKey.Type.X25519).algorithm(Algorithm.ECDH_ES).keyId(dkid).ephemeral()
				.build();
		when(oidcClient.getDecryptJwk()).thenReturn(IuIterable.iter(decryptKey));

		final var encryptedUserinfo = WebEncryption.builder(Encryption.A256GCM).compact()
				.addRecipient(Algorithm.ECDH_ES).keyId(dkid).key(decryptKey.wellKnown()).contentType("application/json")
				.encrypt(IuJson.object() //
						.add("sub", authnPrincipal) //
						.build().toString())
				.compact();

		http.when(() -> IuHttp.send(eq(userinfoEndpoint), argThat(a -> {
			final var rb = mock(HttpRequest.Builder.class);
			assertDoesNotThrow(() -> a.accept(rb));
			verify(rb).header("Authorization", "Bearer " + accessToken);
			return true;
		}), eq(IuHttp.READ_UTF8))).thenReturn(encryptedUserinfo);

		try (final var mockWebKey = mockStatic(WebKey.class, CALLS_REAL_METHODS)) {
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).thenReturn(IuIterable.iter(issuerKey));
			final var principal = resource.getAuthorizedPrincipal(attributes);
			assertNull(null, principal.getSetCookie());
			assertEquals(authnPrincipal, principal.getPrincipal().getName());
		}
	}

}
