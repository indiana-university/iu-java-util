package iu.oidc.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuOidcClientTest {

	@Test
	void testDefaults() {
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		assertFalse(client.isUseBasicAuth());
		assertEquals(Duration.ofMinutes(2L), client.getAssertionTtl());
		assertEquals(Duration.ofMinutes(15L), client.getTokenTtl());
		assertEquals(Duration.ofHours(12L), client.getMaxAge());
	}

}
