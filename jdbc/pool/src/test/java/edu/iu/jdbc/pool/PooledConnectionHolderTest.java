package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import javax.sql.PooledConnection;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class PooledConnectionHolderTest {

	@Test
	public void testUsage() {
		final var pooledConnection = mock(PooledConnection.class);
		final var initTime = Instant.now();
		final var holder = new PooledConnectionHolder(pooledConnection, initTime);
		assertEquals(0, holder.getUsageCount());
		assertNull(holder.getLastUse());

		final var reaperTask = mock(ScheduledFuture.class);
		holder.usageStarted(reaperTask);

		holder.usageComplete();
		assertEquals(1, holder.getUsageCount());
		assertFalse(holder.getLastUse().isBefore(initTime));
		verify(reaperTask).cancel(false);
	}

	@Test
	public void testValidateSuccess() throws SQLException {
		final var validationQuery = IdGenerator.generateId();
		final var pooledConnection = mock(PooledConnection.class);
		final var connection = mock(Connection.class);
		final var statement = mock(Statement.class);
		final var resultSet = mock(ResultSet.class);
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getObject(1)).thenReturn(new Object());
		when(statement.executeQuery(validationQuery)).thenReturn(resultSet);
		when(connection.createStatement()).thenReturn(statement);
		when(pooledConnection.getConnection()).thenReturn(connection);

		final var initTime = Instant.now();
		final var holder = new PooledConnectionHolder(pooledConnection, initTime);
		assertDoesNotThrow(() -> holder.validate(validationQuery));
	}

	@Test
	public void testValidateEmptyResultSet() throws SQLException {
		final var validationQuery = IdGenerator.generateId();
		final var pooledConnection = mock(PooledConnection.class);
		final var connection = mock(Connection.class);
		final var statement = mock(Statement.class);
		final var resultSet = mock(ResultSet.class);
		when(statement.executeQuery(validationQuery)).thenReturn(resultSet);
		when(connection.createStatement()).thenReturn(statement);
		when(pooledConnection.getConnection()).thenReturn(connection);

		final var initTime = Instant.now();
		final var holder = new PooledConnectionHolder(pooledConnection, initTime);
		assertThrows(SQLException.class, () -> holder.validate(validationQuery));
	}

	@Test
	public void testValidateNullColumn() throws SQLException {
		final var validationQuery = IdGenerator.generateId();
		final var pooledConnection = mock(PooledConnection.class);
		final var connection = mock(Connection.class);
		final var statement = mock(Statement.class);
		final var resultSet = mock(ResultSet.class);
		when(resultSet.next()).thenReturn(true, false);
		when(statement.executeQuery(validationQuery)).thenReturn(resultSet);
		when(connection.createStatement()).thenReturn(statement);
		when(pooledConnection.getConnection()).thenReturn(connection);

		final var initTime = Instant.now();
		final var holder = new PooledConnectionHolder(pooledConnection, initTime);
		assertThrows(SQLException.class, () -> holder.validate(validationQuery));
	}

}
