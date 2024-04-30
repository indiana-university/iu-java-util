package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import iu.auth.principal.PrincipalVerifierRegistry;

@SuppressWarnings("javadoc")
public class BearerTokenVerifierTest extends IuOAuthTestCase {

	@Test
	public void testNoScope() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var verifier = new BearerTokenVerifier(realm, null);
		PrincipalVerifierRegistry.registerVerifier(verifier);
		final var client = mock(IuAuthorizationClient.class);
		try (final var mockSpi = mockStatic(OAuthSpi.class)) {
			mockSpi.when(() -> OAuthSpi.getClient(realm)).thenReturn(client);
			final var id = new BearerToken(realm, null, Set.of(), null, Instant.now().plusSeconds(1L));
			assertEquals(
					"Bearer realm=\"" + realm
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for principal realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id, realm)).getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testVerificationFailures() throws IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var scope = IdGenerator.generateId();
		final var verifier = new BearerTokenVerifier(realm, scope);
		PrincipalVerifierRegistry.registerVerifier(verifier);

		final var client = mock(IuAuthorizationClient.class);
		try (final var mockSpi = mockStatic(OAuthSpi.class)) {
			mockSpi.when(() -> OAuthSpi.getClient(realm)).thenReturn(client);
			final var id = new BearerToken(realm, null, Set.of(scope), null, Instant.now().plusSeconds(1L));
			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for principal realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id, realm)).getMessage());

			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id, IdGenerator.generateId()))
							.getMessage());

			final var idrealm = IdGenerator.generateId();
			when(client.getPrincipalRealms()).thenReturn(Set.of(idrealm), Set.of(idrealm), Set.of(realm));
			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for principal realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id, realm)).getMessage());

			final var id3 = new BearerToken(idrealm, null, Set.of(scope), null, Instant.now().plusSeconds(1L));
			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Invalid token for principal realm\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id3, realm)).getMessage());

			final var id4 = new BearerToken(realm, id, Set.of(scope), null, Instant.now().plusSeconds(120L));
			final var id5 = new BearerToken(realm, id4, Set.of(scope), null, Instant.now().plusSeconds(120L));
			assertEquals("illegal principal reference",
					assertThrows(IllegalStateException.class, () -> verifier.verify(id5, realm)).getMessage());

			final var id2 = new BearerToken(realm, null, Set.of(scope), null,
					Instant.now().truncatedTo(ChronoUnit.SECONDS));
			assertEquals(
					"Bearer realm=\"" + realm + "\" scope=\"" + scope
							+ "\" error=\"invalid_token\" error_description=\"Token is expired\"",
					assertThrows(IuAuthenticationException.class, () -> verifier.verify(id2, realm)).getMessage());
		}
	}

}
