package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;

@SuppressWarnings("javadoc")
public class IuBasicAuthCredentialsTest {

	@Test
	public void testApplyTo() {
		final var username = IdGenerator.generateId();
		final var password = IdGenerator.generateId();

		final var basic = mock(IuBasicAuthCredentials.class, CALLS_REAL_METHODS);
		when(basic.getUsername()).thenReturn(username);
		when(basic.getPassword()).thenReturn(password);

		final var rb = mock(HttpRequest.Builder.class);
		assertDoesNotThrow(() -> basic.applyTo(rb));
		verify(rb).header("Authorization", "Basic " + IuText.base64(IuText.utf8(username + ":" + password)));
	}

}
