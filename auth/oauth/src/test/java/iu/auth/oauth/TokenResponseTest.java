package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class TokenResponseTest {

	@Test
	public void testMinimal() {
		final var attr = mock(Map.class);
		final var tr = Json.createObjectBuilder().add("token_type", "bar").add("access_token", "baz").build();
		@SuppressWarnings("unchecked")
		final var tokenResponse = new TokenResponse(List.of("foo"), attr, tr);
		assertEquals("foo", tokenResponse.getScope().iterator().next());
		assertEquals("bar", tokenResponse.getTokenType());
		assertEquals("baz", tokenResponse.getAccessToken());
		assertNull(tokenResponse.getExpires());
		assertSame(attr, tokenResponse.getRequestAttributes());
		assertNull(tokenResponse.getRefreshToken());
		assertTrue(tokenResponse.getTokenAttributes().isEmpty());
		assertFalse(tokenResponse.isExpired());
	}

	@Test
	public void testMissingTokenType() {
		assertThrows(IllegalStateException.class,
				() -> new TokenResponse(List.of("foo"), null, Json.createObjectBuilder().build()));
	}

	@Test
	public void testMissingAccessToken() {
		assertThrows(IllegalStateException.class, () -> new TokenResponse(List.of("foo"), Map.of(),
				Json.createObjectBuilder().add("token_type", "bar").build()));
	}

	@Test
	public void testExpiredInt() throws InterruptedException {
		final var tr = Json.createObjectBuilder().add("token_type", "bar").add("access_token", "baz")
				.add("expires_in", 1).build();
		final var tokenResponse = new TokenResponse(List.of("foo"), null, tr);
		assertFalse(tokenResponse.isExpired());
		Thread.sleep(1001L);
		assertTrue(tokenResponse.isExpired());
	}

	@Test
	public void testExpiredString() throws InterruptedException {
		final var tr = Json.createObjectBuilder().add("token_type", "bar").add("access_token", "baz")
				.add("expires_in", "1").build();
		final var tokenResponse = new TokenResponse(List.of("foo"), null, tr);
		assertFalse(tokenResponse.isExpired());
		Thread.sleep(1001L);
		assertTrue(tokenResponse.isExpired());
	}

	@Test
	public void testError() {
		assertThrows(IllegalStateException.class,
				() -> new TokenResponse(List.of("foo"), null, Json.createObjectBuilder().add("error", "").build()));
	}

	@Test
	public void testRefreshToken() {
		final var refreshToken = IdGenerator.generateId();
		final var tr = Json.createObjectBuilder().add("token_type", "").add("access_token", "")
				.add("refresh_token", refreshToken).build();
		final var tokenResponse = new TokenResponse(List.of("foo"), null, tr);
		assertEquals(refreshToken, tokenResponse.getRefreshToken());
	}

	@Test
	public void testScope() {
		final var tr = Json.createObjectBuilder().add("token_type", "").add("access_token", "").add("scope", "foo bar")
				.build();
		final var tokenResponse = new TokenResponse(List.of("foo"), null, tr);
		assertEquals(List.of("foo", "bar"), tokenResponse.getScope());
	}

	@Test
	public void testExtraString() {
		final var tr = Json.createObjectBuilder().add("token_type", "").add("access_token", "").add("foo", "bar")
				.build();
		final var tokenResponse = new TokenResponse(List.of("foo"), null, tr);
		assertEquals("bar", tokenResponse.getTokenAttributes().get("foo"));
	}

	@Test
	public void testExtraValue() {
		final var tr = Json.createObjectBuilder().add("token_type", "").add("access_token", "").addNull("foo")
				.build();
		final var tokenResponse = new TokenResponse(List.of("foo"), null, tr);
		assertEquals("null", tokenResponse.getTokenAttributes().get("foo"));
	}

}
