package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.sql.Connection;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuDataSourceIntegrationTest {

	@Test
	public void testDefaults() {
		final var integration = mock(IuDataSourceIntegration.class, CALLS_REAL_METHODS);
		final var connection = mock(Connection.class);
		assertSame(connection, integration.initializeConnection(connection));
		assertNull(integration.getTransactionManager());
		assertNull(integration.getTransactionSynchronizationRegistry());
	}

}
