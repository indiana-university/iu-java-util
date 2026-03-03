package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.IuAuthorizationDetails;
import edu.iu.auth.oauth.OAuthClient;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class OidcIdTokenBuilderTest {

	@Test
	public void testIdToken() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final var email = IdGenerator.generateId();
		final var role = IdGenerator.generateId();
		final var authDetailsType = IdGenerator.generateId();
		final var authDetails = mock(IuAuthorizationDetails.class);
		when(authDetails.getType()).thenReturn(authDetailsType);

		final var authTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5L);
		final var exp = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(900L);
		final var accessToken = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var alg = Algorithm.EDDSA;
		final var clientId = IdGenerator.generateId();
		final var client = mock(OAuthClient.class);
		final var maxAge = Duration.ofHours(12L);
		when(client.getClientId()).thenReturn(clientId);

		final var idToken = OidcIdToken.builder(alg, client, maxAge).nonce(nonce) //
				.accessToken(accessToken) //
				.iss(iss) //
				.aud(aud) //
				.sub(sub) //
				.iat() //
				.exp(exp) //
				.fullName(name) //
				.email(email) //
				.authTime(authTime) //
				.roles(role) //
				.authorizationDetails(IuAuthorizationDetails.class, authDetails) //
				.build();

		idToken.validateClaims(aud, Duration.ofMinutes(15L));

		final var rb = mock(HttpRequest.Builder.class);
		assertDoesNotThrow(() -> idToken.getBearerToken().applyTo(rb));
		verify(rb).header("Authorization", "Bearer " + accessToken);

		assertEquals(iss, idToken.getIssuer());
		assertEquals(sub, idToken.getSubject());
		assertNotNull(idToken.getIssuedAt());
		assertEquals(name, idToken.getFullName());
		assertEquals(email, idToken.getEmail());
		assertEquals(authTime, idToken.getAuthTime());
		assertEquals(role, idToken.getRoles().iterator().next());
		assertEquals(authDetailsType, idToken.getAuthorizationDetails(IuAuthorizationDetails.class, authDetailsType)
				.iterator().next().getType());
	}

	@Test
	public void testIdTokenMin() {
		final var iss = URI.create(IdGenerator.generateId());
		final var aud = URI.create(IdGenerator.generateId());
		final var sub = IdGenerator.generateId();

		final var authTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5L);
		final var exp = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(900L);
		final var accessToken = IdGenerator.generateId();
		final var nonce = IdGenerator.generateId();
		final var alg = Algorithm.EDDSA;
		final var clientId = IdGenerator.generateId();
		final var client = mock(OAuthClient.class);
		final var maxAge = Duration.ofHours(12L);
		when(client.getClientId()).thenReturn(clientId);

		final var idToken = OidcIdToken.builder(alg, client, maxAge).nonce(nonce) //
				.accessToken(accessToken) //
				.iss(iss) //
				.aud(aud) //
				.sub(sub) //
				.iat() //
				.exp(exp) //
				.authTime(authTime) //
				.build();

		idToken.validateClaims(aud, Duration.ofMinutes(15L));

		final var rb = mock(HttpRequest.Builder.class);
		assertDoesNotThrow(() -> idToken.getBearerToken().applyTo(rb));
		verify(rb).header("Authorization", "Bearer " + accessToken);

		assertEquals(iss, idToken.getIssuer());
		assertEquals(sub, idToken.getSubject());
		assertNotNull(idToken.getIssuedAt());
	}

}
