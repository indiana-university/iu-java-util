package edu.iu.redis;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
@SuppressWarnings("javadoc")
public class IuRedisConfigurationTest {

	@Test
	public void testDefaultConfiguration() {
		final var config = mock(IuRedisConfiguration.class, CALLS_REAL_METHODS);
		assertTrue(config.getSsl());
	}
}
