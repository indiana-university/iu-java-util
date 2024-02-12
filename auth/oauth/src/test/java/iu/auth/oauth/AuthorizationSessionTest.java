package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
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
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class AuthorizationSessionTest {

	@Test
	public void testEntryPoint() throws URISyntaxException, IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var client = mock(IuAuthorizationClient.class);
		final var resourceUri = new URI("foo:/bar");
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		IuAuthorizationClient.initialize(client);

		final var entryPointUri = new URI("foo:/bar/baz");
		final var session = IuAuthorizationSession.create(realm, entryPointUri);
		final var grant = session.grant();
		assertSame(grant, session.grant());

		final var authException = assertThrows(IuAuthenticationException.class, () -> session.authorize("foo", "bar"));
		assertEquals("Bearer realm=\"" + realm + "\" error=\"invalid_request\" error_description=\"invalid state\"",
				authException.getMessage());
		assertEquals(entryPointUri, authException.getLocation());
	}

	@Test
	public void testAuthorize() throws URISyntaxException, IuAuthenticationException {
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

		final var entryPointUri = new URI("foo:/bar/baz");
		final var session = IuAuthorizationSession.create(realm, entryPointUri);
		final var grant = session.grant();

		IuTestLogger.allow("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE);
		final var authException = assertThrows(IuAuthenticationException.class, () -> grant.authorize(entryPointUri));
		final var matcher = Pattern
				.compile("Bearer realm=\"" + realm + "\" scope=\"foo bar\" state=\"([0-9A-Za-z_\\-]{32})\" foo=\"bar\"")
				.matcher(authException.getMessage());
		assertTrue(matcher.matches(), authException::getMessage);
		final var state = matcher.group(1);
		final var code = IdGenerator.generateId();

	}

}
