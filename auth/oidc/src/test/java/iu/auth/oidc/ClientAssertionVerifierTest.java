package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import iu.auth.config.AuthConfig;
import iu.auth.pki.PkiPrincipal;

@SuppressWarnings("javadoc")
public class ClientAssertionVerifierTest {

	@Test
	public void testPurgeUsedAssertions() {
		final var iss = URI.create(IdGenerator.generateId());
		final var jti = IdGenerator.generateId();
		assertDoesNotThrow(() -> ClientAssertionVerifier.validateAssertionJti(jti, iss));

		final var error = assertThrows(IllegalArgumentException.class,
				() -> ClientAssertionVerifier.validateAssertionJti(jti, iss));
		assertEquals("Replayed assertion jti claim", error.getMessage());

		assertDoesNotThrow(() -> Thread.sleep(1000L));
		ClientAssertionVerifier.purgeUsedAssertions(Duration.ZERO);
		assertDoesNotThrow(() -> ClientAssertionVerifier.validateAssertionJti(jti, iss));
	}

	@Test
	public void testInvalidJti() {
		final var error = assertThrows(IllegalArgumentException.class,
				() -> ClientAssertionVerifier.validateAssertionJti("", null));
		assertEquals("Invalid assertion jti claim, must be at least 16 printable ASCII characters", error.getMessage());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testVerifyTrustedIssuer() {
		final var iss = URI.create(IdGenerator.generateId());
		final var issuerRealm = IdGenerator.generateId();
		final var jwk = mock(WebKey.class);
		final var jose = mock(WebCryptoHeader.class);

		final var sig = mock(WebSignature.class);
		when(sig.getHeader()).thenReturn(jose);

		final var jws = mock(WebSignedPayload.class);
		when(jws.getSignatures()).thenReturn((Iterable) IuIterable.iter(sig));

		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);

		try (final var mockTrustedIssuerCredentials = mockConstruction(TrustedIssuerCredentials.class, (a, ctx) -> {
			assertEquals(jose, ctx.arguments().get(0));
			when(a.getJwk()).thenReturn(jwk);
		}); final var mockPkiPrincipal = mockConstruction(PkiPrincipal.class, (a, ctx) -> {
			assertEquals(mockTrustedIssuerCredentials.constructed().get(0), ctx.arguments().get(0));
			when(a.getName()).thenReturn(iss.toString());
		}); final var mockPrincipalIdentity = mockStatic(IuPrincipalIdentity.class)) {
			final var credentials = ClientAssertionVerifier.verifyAssertionIssuerTrust(jws, token, issuerRealm);
			assertSame(credentials, mockTrustedIssuerCredentials.constructed().get(0));
			mockPrincipalIdentity
					.verify(() -> IuPrincipalIdentity.verify(mockPkiPrincipal.constructed().get(0), issuerRealm));
			verify(jws).verify(jwk);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testVerifyTrustedIssuerMismatch() {
		final var iss = URI.create(IdGenerator.generateId());
		final var jose = mock(WebCryptoHeader.class);

		final var sig = mock(WebSignature.class);
		when(sig.getHeader()).thenReturn(jose);

		final var jws = mock(WebSignedPayload.class);
		when(jws.getSignatures()).thenReturn((Iterable) IuIterable.iter(sig));

		final var token = mock(IuWebToken.class);
		when(token.getIssuer()).thenReturn(iss);

		try (final var mockTrustedIssuerCredentials = mockConstruction(TrustedIssuerCredentials.class, (a, ctx) -> {
			assertEquals(jose, ctx.arguments().get(0));
		}); final var mockPkiPrincipal = mockConstruction(PkiPrincipal.class, (a, ctx) -> {
			assertEquals(mockTrustedIssuerCredentials.constructed().get(0), ctx.arguments().get(0));
			when(a.getName()).thenReturn(IdGenerator.generateId());
		}); final var mockPrincipalIdentity = mockStatic(IuPrincipalIdentity.class)) {
			final var error = assertThrows(IllegalArgumentException.class,
					() -> ClientAssertionVerifier.verifyAssertionIssuerTrust(jws, token, null));
			assertEquals("Token issuer doesn't match subject principal", error.getMessage());
		}
	}

	@Test
	public void testVerifyClientAssertionMissingSubject() {
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var assertion = IdGenerator.generateId();
		final var issuerRealm = IdGenerator.generateId();
		final var jws = mock(WebSignedPayload.class);
		final var token = mock(IuWebToken.class);

		try (final var mockWebSignedPayload = mockStatic(WebSignedPayload.class);
				final var mockOpenIdConnectProvider = mockStatic(OpenIdConnectProvider.class);
				final var mockClientAssertionVerifier = mockStatic(ClientAssertionVerifier.class)) {
			mockWebSignedPayload.when(() -> WebSignedPayload.parse(assertion)).thenReturn(jws);
			mockOpenIdConnectProvider.when(() -> OpenIdConnectProvider.parseTokenClaims(jws)).thenReturn(token);
			mockClientAssertionVerifier
					.when(() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, issuerRealm))
					.thenCallRealMethod();

			final var error = assertThrows(NullPointerException.class,
					() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, issuerRealm));
			assertEquals("Missing sub claim", error.getMessage());
		}
	}

	@Test
	public void testVerifyClientAssertionInvalidSubject() {
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var assertion = IdGenerator.generateId();
		final var issuerRealm = IdGenerator.generateId();
		final var jws = mock(WebSignedPayload.class);
		final var token = mock(IuWebToken.class);
		when(token.getSubject()).thenReturn("");

		try (final var mockWebSignedPayload = mockStatic(WebSignedPayload.class);
				final var mockOpenIdConnectProvider = mockStatic(OpenIdConnectProvider.class);
				final var mockClientAssertionVerifier = mockStatic(ClientAssertionVerifier.class)) {
			mockWebSignedPayload.when(() -> WebSignedPayload.parse(assertion)).thenReturn(jws);
			mockOpenIdConnectProvider.when(() -> OpenIdConnectProvider.parseTokenClaims(jws)).thenReturn(token);
			mockClientAssertionVerifier
					.when(() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, issuerRealm))
					.thenCallRealMethod();

			final var error = assertThrows(IllegalArgumentException.class,
					() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, issuerRealm));
			assertEquals("Invalid client_id in sub claim", error.getMessage());
		}
	}

	@Test
	public void testVerifyClientAssertionIssuerTrust() {
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var assertion = IdGenerator.generateId();
		final var issuerRealm = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var jws = mock(WebSignedPayload.class);
		final var iss = URI.create(IdGenerator.generateId());

		final var jti = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var token = mock(IuWebToken.class);
		when(token.getSubject()).thenReturn(clientId);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getTokenId()).thenReturn(jti);
		when(token.getNonce()).thenReturn(nonce);

		final var client = mock(IuAuthorizationClient.class);
		final var credentials = mock(Credentials.class);

		try (final var mockWebSignedPayload = mockStatic(WebSignedPayload.class);
				final var mockOpenIdConnectProvider = mockStatic(OpenIdConnectProvider.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockClientAssertionVerifier = mockStatic(ClientAssertionVerifier.class)) {
			mockWebSignedPayload.when(() -> WebSignedPayload.parse(assertion)).thenReturn(jws);
			mockOpenIdConnectProvider.when(() -> OpenIdConnectProvider.parseTokenClaims(jws)).thenReturn(token);
			mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizationClient.class, clientId)).thenReturn(client);
			mockClientAssertionVerifier
					.when(() -> ClientAssertionVerifier.verifyAssertionIssuerTrust(jws, token, issuerRealm))
					.thenReturn(credentials);

			mockClientAssertionVerifier
					.when(() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, issuerRealm))
					.thenCallRealMethod();

			final var authenticatedClient = assertDoesNotThrow(
					() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, issuerRealm));

			assertEquals(clientId, authenticatedClient.clientId());
			assertSame(client, authenticatedClient.client());
			assertSame(credentials, authenticatedClient.credentials());
			assertEquals(jti, authenticatedClient.jti());
			assertEquals(nonce, authenticatedClient.nonce());
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testVerifyClientAssertionInvalidCredentials() {
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var assertion = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var jws = mock(WebSignedPayload.class);
		final var iss = URI.create(clientId);

		final var jti = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var token = mock(IuWebToken.class);
		when(token.getSubject()).thenReturn(clientId);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getTokenId()).thenReturn(jti);
		when(token.getNonce()).thenReturn(nonce);

		final var client = mock(IuAuthorizationClient.class);
		final var credentials = mock(Credentials.class);
		final var jwk = mock(WebKey.class);
		when(credentials.getTokenEndpointAuthMethod()).thenReturn(AuthMethod.CLIENT_SECRET_JWT);
		when(credentials.getJwk()).thenReturn(jwk);
		when(client.getCredentials()).thenReturn((Iterable) IuIterable.iter(credentials));

		final var signatureVerificationFailure = new IllegalArgumentException();
		doThrow(signatureVerificationFailure).when(jws).verify(jwk);

		try (final var mockWebSignedPayload = mockStatic(WebSignedPayload.class);
				final var mockOpenIdConnectProvider = mockStatic(OpenIdConnectProvider.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockClientAssertionVerifier = mockStatic(ClientAssertionVerifier.class)) {
			mockWebSignedPayload.when(() -> WebSignedPayload.parse(assertion)).thenReturn(jws);
			mockOpenIdConnectProvider.when(() -> OpenIdConnectProvider.parseTokenClaims(jws)).thenReturn(token);
			mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizationClient.class, clientId)).thenReturn(client);

			mockClientAssertionVerifier.when(() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, null))
					.thenCallRealMethod();

			final var error = assertThrows(IllegalArgumentException.class,
					() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, null));
			assertEquals("Invalid client assertion signature", error.getMessage(), () -> IuException.trace(error));
			final var suppressed = error.getSuppressed();
			assertNotNull(suppressed, () -> IuException.trace(error));
			assertEquals(1, suppressed.length, () -> IuException.trace(error));
			assertSame(signatureVerificationFailure, suppressed[0], () -> IuException.trace(error));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
	@Test
	public void testVerifyClientAssertionSuccess() {
		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var assertion = IdGenerator.generateId();
		final var clientId = IdGenerator.generateId();
		final var jws = mock(WebSignedPayload.class);
		final var iss = URI.create(clientId);

		final var jti = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var token = mock(IuWebToken.class);
		when(token.getSubject()).thenReturn(clientId);
		when(token.getIssuer()).thenReturn(iss);
		when(token.getTokenId()).thenReturn(jti);
		when(token.getNonce()).thenReturn(nonce);

		final var client = mock(IuAuthorizationClient.class);
		final var credentialToIgnore = mock(Credentials.class);
		when(credentialToIgnore.getTokenEndpointAuthMethod()).thenReturn(AuthMethod.CLIENT_SECRET_BASIC);
		final var credentials = mock(Credentials.class);
		when(credentials.getTokenEndpointAuthMethod()).thenReturn(AuthMethod.CLIENT_SECRET_JWT);
		when(client.getCredentials()).thenReturn((Iterable) IuIterable.iter(credentialToIgnore, credentials));

		try (final var mockWebSignedPayload = mockStatic(WebSignedPayload.class);
				final var mockOpenIdConnectProvider = mockStatic(OpenIdConnectProvider.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockClientAssertionVerifier = mockStatic(ClientAssertionVerifier.class)) {
			mockWebSignedPayload.when(() -> WebSignedPayload.parse(assertion)).thenReturn(jws);
			mockOpenIdConnectProvider.when(() -> OpenIdConnectProvider.parseTokenClaims(jws)).thenReturn(token);
			mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizationClient.class, clientId)).thenReturn(client);

			mockClientAssertionVerifier.when(() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, null))
					.thenCallRealMethod();

			final var authenticatedClient = assertDoesNotThrow(
					() -> ClientAssertionVerifier.verify(tokenEndpoint, assertion, null));

			assertEquals(clientId, authenticatedClient.clientId());
			assertSame(client, authenticatedClient.client());
			assertSame(credentials, authenticatedClient.credentials());
			assertEquals(jti, authenticatedClient.jti());
			assertEquals(nonce, authenticatedClient.nonce());
		}
	}

}
