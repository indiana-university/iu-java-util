package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;

@SuppressWarnings("javadoc")
public class OidcIdTokenPrincipalTest {

	@Test
	void testProperties() {
		final var sub = IdGenerator.generateId();
		final var iss = IdGenerator.generateId();
		final var iat = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		final var authTime = iat.minusSeconds(5L);
		final var exp = iat.plusSeconds(5L);

		final var idToken = mock(OidcIdToken.class);
		when(idToken.getSubject()).thenReturn(sub);
		when(idToken.getAuthTime()).thenReturn(authTime);
		when(idToken.getIssuedAt()).thenReturn(iat);
		when(idToken.getExpires()).thenReturn(exp);
		when(idToken.getIssuer()).thenReturn(URI.create(iss));

		final var userInfo = IuJson.object().add("sub", sub).build();
		final var principal = new OidcIdTokenPrincipal(idToken, userInfo);
		assertEquals(iss, principal.getIssuer());
		assertEquals(sub, principal.getName());
		assertEquals(iat, principal.getIssuedAt());
		assertEquals(authTime, principal.getAuthTime());
		assertEquals(exp, principal.getExpires());
		assertEquals(principal, principal.getSubject().getPrincipals().iterator().next());
	}

}
