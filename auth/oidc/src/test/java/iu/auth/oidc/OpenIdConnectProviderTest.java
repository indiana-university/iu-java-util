package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.auth.config.IuOpenIdProviderEndpoint;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.oidc.IuAuthorizationRequest;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebSignedPayload;
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
	public void testParseTokenClaims() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var jti = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var nbf = iat.minusSeconds(1L);
		final var exp = iat.plusSeconds(1L);

		final var jws = mock(WebSignedPayload.class);
		when(jws.getPayload()).thenReturn(IuText.utf8(IuJson.object() //
				.add("iss", iss.toString()) //
				.add("aud", aud.toString()) //
				.add("sub", sub) //
				.add("jti", jti) //
				.add("nonce", nonce) //
				.add("iat", iat.getEpochSecond()) //
				.add("nbf", nbf.getEpochSecond()) //
				.add("exp", exp.getEpochSecond()) //
				.build().toString()));

		final var token = OpenIdConnectProvider.parseTokenClaims(jws);
		assertEquals(iss, token.getIssuer());
		assertEquals(aud, token.getAudience().iterator().next());
		assertEquals(sub, token.getSubject());
		assertEquals(jti, token.getTokenId());
		assertEquals(nonce, token.getNonce());
		assertEquals(iat, token.getIssuedAt());
		assertEquals(nbf, token.getNotBefore());
		assertEquals(exp, token.getExpires());
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
	public void testValidateClaimsMissingIss() {
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(NullPointerException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Missing iss claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingSub() {
		final var iss = URI.create(IdGenerator.generateId());
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(NullPointerException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Missing sub claim", error.getMessage());
	}

	@Test
	public void testValidateClaimsMissingAud() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		final var audience = URI.create(IdGenerator.generateId());
		final var error = assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Token aud claim doesn't include " + audience, error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsMissingIat() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(NullPointerException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Missing iat claim", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsInvalidIat() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().plusSeconds(30L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Token iat claim must be no more than PT15S in the future", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsMissingExp() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(NullPointerException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Missing exp claim", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsInvalidNbf() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var nbf = iat.plusSeconds(30L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		when(token.getNotBefore()).thenReturn(nbf);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Token nbf claim must be no more than PT15S in the future", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsInvalidExp() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now();
		final var exp = iat.plusSeconds(60L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		when(token.getExpires()).thenReturn(exp);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Token exp claim must be no more than PT30S in the future", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaimsExpired() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().minusSeconds(60L);
		final var exp = iat.plusSeconds(30L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		when(token.getExpires()).thenReturn(exp);
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audience));
		final var error = assertThrows(IllegalArgumentException.class,
				() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
		assertEquals("Token is expired", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValidateClaims() {
		final var iss = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var nbf = iat.minusSeconds(1L);
		final var exp = iat.plusSeconds(1L);
		final var ttl = Duration.ofSeconds(30L);
		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getSubject()).thenReturn(sub);
		when(token.getIssuedAt()).thenReturn(iat);
		when(token.getNotBefore()).thenReturn(nbf);
		when(token.getExpires()).thenReturn(exp);
		final var audToIgnore = URI.create(IdGenerator.generateId());
		final var audience = URI.create(IdGenerator.generateId());
		when(token.getAudience()).thenReturn((Iterable) IuIterable.iter(audToIgnore, audience));
		assertDoesNotThrow(() -> OpenIdConnectProvider.validateClaims(audience, token, ttl));
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
		final var credentials = mock(Credentials.class);
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
}
