package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;

@SuppressWarnings("javadoc")
public class ClientSecretVerifierTest {

	private MockedStatic<ClientSecretVerifier> mockClientSecretVerifier;

	@BeforeEach
	public void setup() {
		mockClientSecretVerifier = mockStatic(ClientSecretVerifier.class);
	}

	@AfterEach
	public void teardown() {
		mockClientSecretVerifier.close();
	}

	@SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
	@Test
	public void testVerifyClient() {
		final var clientSecret = IdGenerator.generateId();
		final var client = mock(IuAuthorizationClient.class);
		final var credentials = mock(IuAuthorizationCredentials.class);
		when(credentials.getTokenEndpointAuthMethod()).thenReturn(AuthMethod.CLIENT_SECRET_BASIC);
		when(client.getCredentials()).thenReturn((Iterable) List.of(credentials));
		mockClientSecretVerifier
				.when(() -> ClientSecretVerifier.verify(client, AuthMethod.CLIENT_SECRET_BASIC, clientSecret))
				.thenCallRealMethod();

		assertDoesNotThrow(() -> ClientSecretVerifier.verify(client, AuthMethod.CLIENT_SECRET_BASIC, clientSecret));

		mockClientSecretVerifier.verify(() -> ClientSecretVerifier.verify(credentials, clientSecret));
	}

	@SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
	@Test
	public void testVerifyClientFailure() {
		final var clientSecret = IdGenerator.generateId();
		final var client = mock(IuAuthorizationClient.class);
		final var credentials = mock(IuAuthorizationCredentials.class);
		when(credentials.getTokenEndpointAuthMethod()).thenReturn(AuthMethod.CLIENT_SECRET_POST);

		final var credentialsForDifferentAuthType = mock(IuAuthorizationCredentials.class);
		when(credentialsForDifferentAuthType.getTokenEndpointAuthMethod()).thenReturn(AuthMethod.CLIENT_SECRET_BASIC);
		when(client.getCredentials()).thenReturn((Iterable) List.of(credentialsForDifferentAuthType, credentials));

		mockClientSecretVerifier
				.when(() -> ClientSecretVerifier.verify(client, AuthMethod.CLIENT_SECRET_POST, clientSecret))
				.thenCallRealMethod();

		final var error = new IllegalArgumentException();
		mockClientSecretVerifier.when(() -> ClientSecretVerifier.verify(credentials, clientSecret)).thenThrow(error);

		final var authError = assertThrows(IllegalArgumentException.class,
				() -> ClientSecretVerifier.verify(client, AuthMethod.CLIENT_SECRET_POST, clientSecret));
		mockClientSecretVerifier
				.verify(() -> ClientSecretVerifier.verify(credentialsForDifferentAuthType, clientSecret), never());

		assertEquals("Invalid client credentails for authentication method " + AuthMethod.CLIENT_SECRET_POST,
				authError.getMessage(), () -> IuException.trace(authError));
		final var suppressed = authError.getSuppressed();
		assertNotNull(suppressed, () -> IuException.trace(authError));
		assertEquals(1, suppressed.length, () -> IuException.trace(authError));
		assertSame(error, suppressed[0], () -> IuException.trace(authError));
	}

	@Test
	public void testVerifyClientSecret() {
		final var clientSecret = IdGenerator.generateId();
		final var credentials = mock(IuAuthorizationCredentials.class);

		final var password = IuText.utf8(clientSecret);
		final var saltedPassword = new byte[clientSecret.length() + 4];
		new SecureRandom().nextBytes(saltedPassword);
		System.arraycopy(password, 0, saltedPassword, 4, password.length);

		final var key = new byte[36];
		System.arraycopy(saltedPassword, 0, key, 0, 4);
		System.arraycopy(IuDigest.sha256(saltedPassword), 0, key, 4, 32);
		final var jwk = WebKey.builder(Type.RAW).key(key).build();
		when(credentials.getJwk()).thenReturn(jwk);

		mockClientSecretVerifier.when(() -> ClientSecretVerifier.verify(credentials, clientSecret))
				.thenCallRealMethod();

		assertDoesNotThrow(() -> ClientSecretVerifier.verify(credentials, clientSecret));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testVerifyClientSecretWrongKeyType() {
		final var clientSecret = IdGenerator.generateId();
		final var credentials = mock(IuAuthorizationCredentials.class);
		when(credentials.getTokenEndpointAuthMethod()).thenReturn(AuthMethod.BASIC);
		final var jwk = WebKey.ephemeral(Algorithm.EDDSA);
		when(credentials.getJwk()).thenReturn(jwk);

		mockClientSecretVerifier.when(() -> ClientSecretVerifier.verify(credentials, clientSecret))
				.thenCallRealMethod();

		final var error = assertThrows(IllegalStateException.class,
				() -> ClientSecretVerifier.verify(credentials, clientSecret));
		assertEquals("Invalid key type ED25519 for auth method BASIC", error.getMessage());
	}

	@Test
	public void testVerifyClientSecretInvalidHash() {
		final var clientSecret = IdGenerator.generateId();
		final var credentials = mock(IuAuthorizationCredentials.class);
		final var jwk = WebKey.ephemeral(Encryption.A128GCM);
		when(credentials.getJwk()).thenReturn(jwk);

		mockClientSecretVerifier.when(() -> ClientSecretVerifier.verify(credentials, clientSecret))
				.thenCallRealMethod();

		final var error = assertThrows(IllegalStateException.class,
				() -> ClientSecretVerifier.verify(credentials, clientSecret));
		assertEquals("Invalid password hash", error.getMessage());
	}

	@Test
	public void testVerifyClientSecretInvalidSecret() {
		final var clientSecret = IdGenerator.generateId();
		final var credentials = mock(IuAuthorizationCredentials.class);

		final var hashThatWontMatch = new byte[36];
		new SecureRandom().nextBytes(hashThatWontMatch);
		final var jwk = WebKey.builder(Type.RAW).key(hashThatWontMatch).build();
		when(credentials.getJwk()).thenReturn(jwk);

		mockClientSecretVerifier.when(() -> ClientSecretVerifier.verify(credentials, clientSecret))
				.thenCallRealMethod();

		final var error = assertThrows(IllegalArgumentException.class,
				() -> ClientSecretVerifier.verify(credentials, clientSecret));
		assertEquals("Invalid client secret", error.getMessage());
	}
}
