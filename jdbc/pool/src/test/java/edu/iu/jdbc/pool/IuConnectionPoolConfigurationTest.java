package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuConnectionPoolConfigurationTest {

	@Test
	public void testDefaults() {
		final var config = mock(IuConnectionPoolConfiguration.class, CALLS_REAL_METHODS);
		assertEquals(Duration.ofSeconds(15L), config.getLoginTimeout());
		assertEquals(16, config.getMaxSize());
		assertEquals(1, config.getMaxRetry());
		assertEquals(100, config.getMaxConnectionReuseCount());
		assertEquals(Duration.ofMinutes(15L), config.getMaxConnectionReuseTime());
		assertEquals(Duration.ofMinutes(30L), config.getAbandonedConnectionTimeout());
		assertEquals(4, config.getAbandonedConnectionThreads());
		assertEquals(Duration.ofSeconds(30L), config.getShutdownTimeout());
		assertNull(config.getValidationQuery());
		assertEquals(Duration.ofSeconds(15L), config.getValidationInterval());
		assertDoesNotThrow(config::onShutdown);
	}

}
