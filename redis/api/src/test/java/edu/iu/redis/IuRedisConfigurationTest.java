package edu.iu.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuRedisConfigurationTest {

	@Test
	public void testDefaultConfiguration() {
		final var config = mock(IuRedisConfiguration.class, CALLS_REAL_METHODS);
		assertTrue(config.getSsl());
		assertEquals(Duration.ofMinutes(15), config.getKeyExpiration());
	}
}
