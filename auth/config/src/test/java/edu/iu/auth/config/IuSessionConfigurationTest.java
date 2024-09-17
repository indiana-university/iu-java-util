package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuSessionConfigurationTest {

	@Test
	public void testDefault() {
		final var config = mock(IuSessionConfiguration.class, CALLS_REAL_METHODS);
		assertEquals(Duration.ofMinutes(15L), config.getInActiveTtl());
		assertEquals(Duration.ofHours(12L), config.getMaxSessionTtl());
	}
}
