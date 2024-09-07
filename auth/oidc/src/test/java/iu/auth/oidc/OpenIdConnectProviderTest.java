package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.auth.config.IuOpenIdProviderEndpoint;
import edu.iu.auth.oidc.IuAuthorizationRequest;
import iu.auth.config.AuthConfig;

@SuppressWarnings("javadoc")
public class OpenIdConnectProviderTest {

	@Test
	public void testConfig() {
		final var config = mock(IuOpenIdProviderEndpoint.class);
		final var provider = new OpenIdConnectProvider(config);
		assertSame(config, provider.config());
	}

	@Test
	public void testParamSuccess() {
		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var params = Map.of(name, new String[] { value });
		assertEquals(value, OpenIdConnectProvider.param(params, name));
	}

	@Test
	public void testParamMissing() {
		final var name = IdGenerator.generateId();
		final var error = assertThrows(NullPointerException.class, () -> OpenIdConnectProvider.param(Map.of(), name));
		assertEquals("Missing " + name, error.getMessage());
	}

	@Test
	public void testParamInvalid() {
		final var name = IdGenerator.generateId();
		final var error = assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.param(Map.of(name, new String[] { "\0" }), name));
		assertEquals("Invalid " + name, error.getMessage());
	}

	@Test
	public void testParamPolluted() {
		final var name = IdGenerator.generateId();
		final var error = assertThrows(IllegalArgumentException.class, () -> OpenIdConnectProvider
				.param(Map.of(name, new String[] { IdGenerator.generateId(), IdGenerator.generateId() }), name));
		assertEquals("Invalid " + name, error.getMessage());
	}

	@Test
	public void testNoDelayOnSuccess() {
		final var now = Instant.now();
		assertDoesNotThrow(() -> OpenIdConnectProvider.delayOnFailure(() -> null));
		final var elapsed = Duration.between(now, Instant.now());
		assertTrue(elapsed.toMillis() < 100L, elapsed::toString);
	}

	@Test
	public void testDelayOnFailure() {
		final var now = Instant.now();
		final var error = new Throwable();
		assertSame(error, assertThrows(Throwable.class, () -> OpenIdConnectProvider.delayOnFailure(() -> {
			throw error;
		})));
		final var elapsed = Duration.between(now, Instant.now());
		assertTrue(elapsed.toMillis() >= 500L, elapsed::toString);
		assertTrue(elapsed.toMillis() < 600L, elapsed::toString);
	}

	@Test
	public void testValidateTtl() {
		assertDoesNotThrow(() -> OpenIdConnectProvider.validateTtl(Duration.ofMinutes(15L)));

		assertEquals("Invalid ttl",
				assertThrows(IllegalArgumentException.class, () -> OpenIdConnectProvider.validateTtl(Duration.ZERO))
						.getMessage());

		assertEquals("Invalid ttl", assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.validateTtl(Duration.ofMinutes(16L))).getMessage());
	}

	@Test
	public void testValidateExpiration() {
		final var expires = Instant.now();
		assertDoesNotThrow(() -> OpenIdConnectProvider.validateExpiration(expires, Duration.ofSeconds(15L)));
		assertEquals("expired", assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.validateExpiration(expires.minusSeconds(16L), Duration.ofSeconds(15L)))
				.getMessage());
		assertEquals("expired", assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.validateExpiration(expires.plusSeconds(16L), Duration.ofSeconds(15L)))
				.getMessage());
	}

	@Test
	public void testVerifyIpAllowed() {
		final var remoteAddr = IdGenerator.generateId();
		final var clientIp = mock(InetAddress.class);
		final var ipAllow = IdGenerator.generateId();
		final var ipAllowToIgnore = IdGenerator.generateId();
		final var ipAllowList = IuIterable.iter(ipAllowToIgnore, ipAllow);
		try (final var mockIuWebUtils = mockStatic(IuWebUtils.class)) {
			mockIuWebUtils.when(() -> IuWebUtils.getInetAddress(remoteAddr)).thenReturn(clientIp);
			mockIuWebUtils.when(() -> IuWebUtils.isInetAddressInRange(clientIp, ipAllow)).thenReturn(true);
			assertDoesNotThrow(() -> OpenIdConnectProvider.verifyIpAllowed(ipAllowList, remoteAddr));
		}
	}

	@Test
	public void testVerifyIpNotAllowed() {
		final var remoteAddr = IdGenerator.generateId();
		final var clientIp = mock(InetAddress.class);
		final var ipAllow = IdGenerator.generateId();
		final var ipAllowList = IuIterable.iter(ipAllow);
		try (final var mockIuWebUtils = mockStatic(IuWebUtils.class)) {
			mockIuWebUtils.when(() -> IuWebUtils.getInetAddress(remoteAddr)).thenReturn(clientIp);
			final var error = assertThrows(IllegalArgumentException.class,
					() -> OpenIdConnectProvider.verifyIpAllowed(ipAllowList, remoteAddr));
			assertEquals("Remote address not in allow list " + ipAllowList, error.getMessage());
		}
	}

	@Test
	public void testAuthenticateClientSecretBasic() {
		final var encodedCredentials = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();

		final Map<String, String[]> params = new LinkedHashMap<>();
		params.put("grant_type", new String[] { GrantType.CLIENT_CREDENTIALS.parameterValue });
		params.put("nonce", new String[] { nonce });

		final var request = mock(IuAuthorizationRequest.class);
		when(request.getParams()).thenReturn(params);
		when(request.getAuthorizaton()).thenReturn("Basic " + encodedCredentials);

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClient(request)).thenCallRealMethod();

		final var authorizedClient = mock(AuthenticatedClient.class);
		when(provider.authenticateClientBasic(encodedCredentials, nonce)).thenReturn(authorizedClient);

		assertSame(authorizedClient, provider.authenticateClient(request));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testAuthenticateClientSecretPost() {
		final var clientId = IdGenerator.generateId();
		final var clientSecret = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();

		final Map<String, String[]> params = new LinkedHashMap<>();
		params.put("grant_type", new String[] { GrantType.CLIENT_CREDENTIALS.parameterValue });
		params.put("client_id", new String[] { clientId });
		params.put("client_secret", new String[] { clientSecret });
		params.put("nonce", new String[] { nonce });

		final var request = mock(IuAuthorizationRequest.class);
		when(request.getParams()).thenReturn(params);

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClient(request)).thenCallRealMethod();

		final var authorizedClient = mock(AuthenticatedClient.class);
		when(provider.authenticateClientSecret(AuthMethod.CLIENT_SECRET_POST, clientId, clientSecret, nonce))
				.thenReturn(authorizedClient);

		assertSame(authorizedClient, provider.authenticateClient(request));
	}

	@Test
	public void testAuthenticateClientJwtBearerFromParams() {
		final var assertion = IdGenerator.generateId();

		final Map<String, String[]> params = new LinkedHashMap<>();
		params.put("grant_type", new String[] { GrantType.JWT_BEARER.parameterValue });
		params.put("assertion", new String[] { assertion });

		final var request = mock(IuAuthorizationRequest.class);
		when(request.getParams()).thenReturn(params);

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClient(request)).thenCallRealMethod();

		final var authorizedClient = mock(AuthenticatedClient.class);
		when(provider.authenticateClientAssertion(assertion)).thenReturn(authorizedClient);

		assertSame(authorizedClient, provider.authenticateClient(request));
	}

	@Test
	public void testAuthenticateClientJwtBearerFromAuthorization() {
		final var assertion = IdGenerator.generateId();

		final Map<String, String[]> params = new LinkedHashMap<>();
		params.put("grant_type", new String[] { GrantType.JWT_BEARER.parameterValue });

		final var request = mock(IuAuthorizationRequest.class);
		when(request.getParams()).thenReturn(params);
		when(request.getAuthorizaton()).thenReturn("Bearer " + assertion);

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClient(request)).thenCallRealMethod();

		final var authorizedClient = mock(AuthenticatedClient.class);
		when(provider.authenticateClientAssertion(assertion)).thenReturn(authorizedClient);

		assertSame(authorizedClient, provider.authenticateClient(request));
	}

	@Test
	public void testAuthenticateClientJwtBearerInvalidAuthorization() {
		final Map<String, String[]> params = new LinkedHashMap<>();
		params.put("grant_type", new String[] { GrantType.JWT_BEARER.parameterValue });

		final var request = mock(IuAuthorizationRequest.class);
		when(request.getParams()).thenReturn(params);
		when(request.getAuthorizaton()).thenReturn("");

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClient(request)).thenCallRealMethod();

		final var error = assertThrows(UnsupportedOperationException.class, () -> provider.authenticateClient(request));
		assertEquals("Unsupported authorization method", error.getMessage());
	}

	@Test
	public void testAuthenticateClientAssertionFromParams() {
		final var assertion = IdGenerator.generateId();

		final Map<String, String[]> params = new LinkedHashMap<>();
		params.put("grant_type", new String[] { GrantType.CLIENT_CREDENTIALS.parameterValue });
		params.put("client_assertion_type", new String[] { "urn:ietf:params:oauth:client-assertion-type:jwt-bearer" });
		params.put("client_assertion", new String[] { assertion });

		final var request = mock(IuAuthorizationRequest.class);
		when(request.getParams()).thenReturn(params);

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClient(request)).thenCallRealMethod();

		final var authorizedClient = mock(AuthenticatedClient.class);
		when(provider.authenticateClientAssertion(assertion)).thenReturn(authorizedClient);

		assertSame(authorizedClient, provider.authenticateClient(request));
	}

	@Test
	public void testAuthenticateClientInvalidAssertionType() {
		final Map<String, String[]> params = new LinkedHashMap<>();
		params.put("grant_type", new String[] { GrantType.CLIENT_CREDENTIALS.parameterValue });
		params.put("client_assertion_type", new String[] { IdGenerator.generateId() });

		final var request = mock(IuAuthorizationRequest.class);
		when(request.getParams()).thenReturn(params);

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClient(request)).thenCallRealMethod();

		final var error = assertThrows(UnsupportedOperationException.class, () -> provider.authenticateClient(request));
		assertEquals("Unsupported client assertion type", error.getMessage());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testAuthenticateClientBasic() {
		final var clientId = IdGenerator.generateId();
		final var clientSecret = IdGenerator.generateId();
		final var encodedCredentials = IuText.base64(IuText.ascii(clientId + ':' + clientSecret));
		final var nonce = IdGenerator.generateId();

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClientBasic(encodedCredentials, nonce)).thenCallRealMethod();

		final var authorizedClient = mock(AuthenticatedClient.class);
		when(provider.authenticateClientSecret(AuthMethod.CLIENT_SECRET_BASIC, clientId, clientSecret, nonce))
				.thenReturn(authorizedClient);

		assertSame(authorizedClient, provider.authenticateClientBasic(encodedCredentials, nonce));
	}

	@Test
	public void testAuthenticateClientBasicInvalid() {
		final var nonce = IdGenerator.generateId();

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClientBasic("", nonce)).thenCallRealMethod();

		final var error = assertThrows(IllegalArgumentException.class,
				() -> provider.authenticateClientBasic("", nonce));
		assertEquals("Invalid Basic auth credentials", error.getMessage());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testAuthenticateClientSecretIllegalId() {
		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClientSecret(AuthMethod.CLIENT_SECRET_BASIC, "", null, null)).thenCallRealMethod();
		final var error = assertThrows(IllegalArgumentException.class,
				() -> provider.authenticateClientSecret(AuthMethod.CLIENT_SECRET_BASIC, "", null, null));
		assertEquals("Illegal client_id", error.getMessage());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testAuthenticateClientSecretIllegalSecret() {
		final var clientId = IdGenerator.generateId();
		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClientSecret(AuthMethod.CLIENT_SECRET_BASIC, clientId, "", null))
				.thenCallRealMethod();
		final var error = assertThrows(IllegalArgumentException.class,
				() -> provider.authenticateClientSecret(AuthMethod.CLIENT_SECRET_BASIC, clientId, "", null));
		assertEquals("Illegal client_secret", error.getMessage());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testAuthenticateClientSecret() {
		final var clientId = IdGenerator.generateId();
		final var clientSecret = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var client = mock(IuAuthorizationClient.class);
		final var credentials = mock(IuAuthorizationCredentials.class);
		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.authenticateClientSecret(AuthMethod.CLIENT_SECRET_BASIC, clientId, clientSecret, nonce))
				.thenCallRealMethod();

		try (final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockClientSecretVerifier = mockStatic(ClientSecretVerifier.class)) {

			mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizationClient.class, clientId)).thenReturn(client);
			mockClientSecretVerifier
					.when(() -> ClientSecretVerifier.verify(client, AuthMethod.CLIENT_SECRET_BASIC, clientSecret))
					.thenReturn(credentials);

			final var authenticatedClient = assertDoesNotThrow(() -> provider
					.authenticateClientSecret(AuthMethod.CLIENT_SECRET_BASIC, clientId, clientSecret, nonce));

			assertSame(clientId, authenticatedClient.clientId());
			assertSame(client, authenticatedClient.client());
			assertSame(credentials, authenticatedClient.credentials());
			assertSame(nonce, authenticatedClient.nonce());
			assertDoesNotThrow(() -> IdGenerator.verifyId(authenticatedClient.jti(), 15000L));
		}
	}

	@Test
	public void testAuthenticateClientAssertion() {
		final var issuerRealm = IdGenerator.generateId();
		final var assertion = IdGenerator.generateId();
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var config = mock(IuOpenIdProviderEndpoint.class);
		when(config.getTokenEndpoint()).thenReturn(tokenEndpoint);
		when(config.getAssertionIssuerRealm()).thenReturn(issuerRealm);

		final var provider = mock(OpenIdConnectProvider.class);
		when(provider.config()).thenReturn(config);
		when(provider.authenticateClientAssertion(assertion)).thenCallRealMethod();

		final var authenticatedClient = mock(AuthenticatedClient.class);

		try (final var mockClientAssertionVerifier = mockStatic(ClientAssertionVerifier.class)) {
			mockClientAssertionVerifier
					.when(() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, issuerRealm))
					.thenReturn(authenticatedClient);

			assertSame(authenticatedClient, provider.authenticateClientAssertion(assertion));
		}
	}

}
