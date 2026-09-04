package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuPooledConnectionFactoryTest {

	@Test
	public void testDefaults() {
		final var factory = mock(IuPooledConnectionFactory.class, CALLS_REAL_METHODS);
		assertDoesNotThrow(factory::onShutdown);
	}

}
