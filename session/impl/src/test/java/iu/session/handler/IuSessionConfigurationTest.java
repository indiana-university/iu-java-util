package iu.session.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import iu.session.config.IuSessionConfiguration;

@SuppressWarnings("javadoc")
public class IuSessionConfigurationTest {

	@Test
	void testDefaults() {
		final var config = mock(IuSessionConfiguration.class, CALLS_REAL_METHODS);
		assertEquals(Duration.ofMinutes(15L), config.getInactiveTtl());
		assertEquals(Duration.ofHours(12L), config.getMaxSessionTtl());
	}

}
