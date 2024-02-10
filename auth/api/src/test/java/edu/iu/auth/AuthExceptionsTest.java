package edu.iu.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class AuthExceptionsTest {

	@Test
	public void testChallengeException() {
		final var cause = new Throwable();
		final var message = IdGenerator.generateId();
		assertEquals(message, new IuAuthenticationChallengeException(message).getMessage());
		final var challenge = new IuAuthenticationChallengeException(message, cause);
		assertEquals(message, challenge.getMessage());
		assertSame(cause, challenge.getCause());
	}

	@Test
	public void testRedirectException() {
		final var cause = new Throwable();
		final var message = IdGenerator.generateId();
		assertEquals(message, new IuAuthenticationRedirectException(message).getMessage());
		final var redirect = new IuAuthenticationRedirectException(message, cause);
		assertEquals(message, redirect.getMessage());
		assertSame(cause, redirect.getCause());
	}

}
