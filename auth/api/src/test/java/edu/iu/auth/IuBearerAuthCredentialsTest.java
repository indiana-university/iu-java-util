package edu.iu.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.auth.oauth.IuBearerAuthCredentials;

@SuppressWarnings("javadoc")
public class IuBearerAuthCredentialsTest {

	@Test
	public void testImpliesSubject() {
		final var bearer = mock(IuBearerAuthCredentials.class, CALLS_REAL_METHODS);
		final var subject = mock(Subject.class);
		assertFalse(bearer.implies(subject));
		
		when(subject.getPrincipals()).thenReturn(Set.of(bearer));
		assertTrue(bearer.implies(subject));

		when(bearer.getSubject()).thenReturn(subject);
		assertTrue(bearer.implies(subject));
	}

}
