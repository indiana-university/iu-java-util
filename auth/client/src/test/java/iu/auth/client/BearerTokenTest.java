package iu.auth.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class BearerTokenTest {

	@Test
	public void testGetAccessToken() {
		final var accessToken = IdGenerator.generateId();
		BearerToken bearerToken = new BearerToken(accessToken);
		assertEquals(accessToken, bearerToken.getAccessToken());
	}

	@Test
	public void testApplyTo() {
		final var accessToken = IdGenerator.generateId();
		BearerToken bearerToken = new BearerToken(accessToken);
		final var builder = mock(HttpRequest.Builder.class);
		assertDoesNotThrow(() -> bearerToken.applyTo(builder));
		verify(builder).header("Authorization", "Bearer " + accessToken);
	}

}
