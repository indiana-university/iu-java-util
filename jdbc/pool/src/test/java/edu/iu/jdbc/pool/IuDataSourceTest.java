package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import edu.iu.transaction.IuTransactionManager;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;

@SuppressWarnings("javadoc")
@ExtendWith(TestDatabase.class)
public class IuDataSourceTest {

	private DataSource dataSource;
	private IuDataSourceIntegration integration;
	private IuConnectionPoolConfiguration config;
	private String descr;

	@BeforeEach
	public void setup() {
		IuTestLogger.allow(IuConnectionPool.class.getName(), Level.CONFIG);
		descr = IdGenerator.generateId();
		config = mock(IuConnectionPoolConfiguration.class, CALLS_REAL_METHODS);
		when(config.getDescription()).thenReturn(descr);
		integration = mock(IuDataSourceIntegration.class, CALLS_REAL_METHODS);
		dataSource = new IuDataSource(integration, config);
	}

	@AfterEach
	public void teardown() throws SQLException {
		((IuDataSource) dataSource).close();
	}

	@Test
	public void testUnsupportedMethods() throws SQLException {
		assertThrows(SQLFeatureNotSupportedException.class, () -> dataSource.getConnection(null, null));
		assertNull(dataSource.getLogWriter());
		assertThrows(SQLFeatureNotSupportedException.class, () -> dataSource.setLogWriter(null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> dataSource.setLoginTimeout(0));
		assertEquals(15, dataSource.getLoginTimeout());
		assertEquals(getClass().getPackageName(), dataSource.getParentLogger().getName());
		assertFalse(dataSource.isWrapperFor(DataSource.class));
		assertThrows(SQLFeatureNotSupportedException.class, () -> dataSource.unwrap(DataSource.class));
	}

	@Test
	public void testConnection() throws SQLException {
		when(integration.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		final var val = IdGenerator.generateId();
		try (final var c = dataSource.getConnection(); final var s = c.prepareStatement("select ?")) {
			s.setString(1, val);
			final var rs = s.executeQuery();
			assertTrue(rs.next());
			assertEquals(val, rs.getString(1));
		}
	}

	@Test
	public void testConnectionInitializationFailureReturnsConnectionToPool() throws SQLException {
		final var listener = new AtomicReference<ConnectionEventListener>();
		final var pooledConnection = mock(PooledConnection.class);
		final var connection = mock(Connection.class);
		final var error = new RuntimeException("initialize connection");
		when(pooledConnection.getConnection()).thenReturn(connection);
		when(integration.createPooledConnection()).thenReturn(pooledConnection);
		when(integration.initializeConnection(connection)).thenThrow(error).thenReturn(connection);
		doAnswer(i -> {
			listener.set(i.getArgument(0));
			return null;
		}).when(pooledConnection).addConnectionEventListener(any());
		doAnswer(i -> {
			listener.get().connectionClosed(new ConnectionEvent(pooledConnection));
			return null;
		}).when(connection).close();

		assertSame(error, assertThrows(RuntimeException.class, dataSource::getConnection));
		verify(connection).close();

		try (final var reused = dataSource.getConnection()) {
			assertSame(connection, reused);
		}
		verify(integration).createPooledConnection();
		verify(pooledConnection).addConnectionEventListener(listener.get());
	}

	@Test
	public void testConnectionInitializationFailureSuppressesCloseFailure() throws SQLException {
		final var listener = new AtomicReference<ConnectionEventListener>();
		final var pooledConnection = mock(PooledConnection.class);
		final var connection = mock(Connection.class);
		final var error = new AssertionError("initialize connection");
		final var closeError = new SQLException("close connection");
		when(pooledConnection.getConnection()).thenReturn(connection);
		when(integration.createPooledConnection()).thenReturn(pooledConnection);
		when(integration.initializeConnection(connection)).thenThrow(error);
		doAnswer(i -> {
			listener.set(i.getArgument(0));
			return null;
		}).when(pooledConnection).addConnectionEventListener(any());
		doThrow(closeError).when(connection).close();

		assertSame(error, assertThrows(AssertionError.class, dataSource::getConnection));
		assertEquals(1, error.getSuppressed().length);
		assertSame(closeError, error.getSuppressed()[0]);
		verify(pooledConnection).addConnectionEventListener(listener.get());

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.INFO, "jdbc-pool-error:" + descr + ":.*",
				SQLException.class);
		listener.get().connectionErrorOccurred(new ConnectionEvent(pooledConnection, closeError));
	}

	@Test
	public void testTransactionalInitializationFailureReturnsPooledConnection() throws SQLException {
		final var registry = mock(TransactionSynchronizationRegistry.class);
		final var pooledConnection = mock(PooledConnection.class);
		final var connection = mock(Connection.class);
		final var error = new RuntimeException("initialize connection");
		when(registry.getTransactionStatus()).thenReturn(Status.STATUS_ACTIVE);
		when(integration.getTransactionSynchronizationRegistry()).thenReturn(registry);
		when(integration.createPooledConnection()).thenReturn(pooledConnection);
		when(pooledConnection.getConnection()).thenReturn(connection);
		when(integration.initializeConnection(connection)).thenThrow(error);

		assertSame(error, assertThrows(RuntimeException.class, dataSource::getConnection));
		assertSame(error, assertThrows(RuntimeException.class, dataSource::getConnection));

		verify(integration).createPooledConnection();
		verify(pooledConnection, times(2)).getConnection();
	}

	@Test
	public void testTransactionCommit() throws Exception {
		when(integration.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());

		final var val = IdGenerator.generateId();
		try (final var tm = new IuTransactionManager()) {
			when(integration.getTransactionManager()).thenReturn(tm);
			when(integration.getTransactionSynchronizationRegistry()).thenReturn(tm);

			IuTestLogger.allow(IuTransactionManager.class.getPackageName(), Level.CONFIG);
			tm.begin();
			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("insert into iu_jdbc_pool_test values(?)")) {
				s.setString(1, val);
				s.executeUpdate();
			}
			final var tx = tm.suspend();

			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
				s.setString(1, val);
				final var rs = s.executeQuery();
				assertFalse(rs.next());
			}

			tm.resume(tx);
			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
				s.setString(1, val);
				final var rs = s.executeQuery();
				assertTrue(rs.next());
				assertEquals(val, rs.getString(1));
			}
			tm.commit();
		}
		try (final var c = dataSource.getConnection();
				final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
			s.setString(1, val);
			final var rs = s.executeQuery();
			assertTrue(rs.next());
			assertEquals(val, rs.getString(1));
		}
		try (final var c = dataSource.getConnection();
				final var s = c.prepareStatement("delete from iu_jdbc_pool_test where value = ?")) {
			s.setString(1, val);
			s.executeUpdate();
		}
	}

	@Test
	public void testTransactionRollback() throws Exception {
		when(integration.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());

		final var val = IdGenerator.generateId();
		try (final var tm = new IuTransactionManager()) {
			when(integration.getTransactionManager()).thenReturn(tm);
			when(integration.getTransactionSynchronizationRegistry()).thenReturn(tm);

			IuTestLogger.allow(IuTransactionManager.class.getPackageName(), Level.CONFIG);
			tm.begin();
			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("insert into iu_jdbc_pool_test values(?)")) {
				s.setString(1, val);
				s.executeUpdate();
			}
			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
				s.setString(1, val);
				final var rs = s.executeQuery();
				assertTrue(rs.next());
				assertEquals(val, rs.getString(1));
			}
			tm.rollback();
		}
		try (final var c = dataSource.getConnection();
				final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
			s.setString(1, val);
			final var rs = s.executeQuery();
			assertFalse(rs.next());
		}
	}

	@Test
	public void testTransactionRollbackError() throws Exception {
		final var l = new ArgumentMatcher<ConnectionEventListener>() {
			ConnectionEventListener listener;

			@Override
			public boolean matches(ConnectionEventListener argument) {
				listener = argument;
				return true;
			}
		};
		final var pc = mock(PooledConnection.class);
		final var mc = mock(Connection.class);
		when(pc.getConnection()).thenReturn(mc);
		when(integration.createPooledConnection()).thenReturn(pc);

		try (final var tm = new IuTransactionManager()) {
			when(integration.getTransactionManager()).thenReturn(tm);
			when(integration.getTransactionSynchronizationRegistry()).thenReturn(tm);

			IuTestLogger.allow(IuTransactionManager.class.getPackageName(), Level.CONFIG);
			tm.begin();
			try (final var c = dataSource.getConnection()) {
				verify(pc).addConnectionEventListener(argThat(l));
			}
			doThrow(SQLException.class).when(mc).rollback();
			doThrow(SQLException.class).when(mc).close();
			IuTestLogger.expect(IuDataSource.class.getName(), Level.WARNING, "rollback failed in afterCompletion",
					SQLException.class);
			IuTestLogger.expect(IuDataSource.class.getName(), Level.WARNING, "close failed in afterCompletion",
					SQLException.class);
			tm.rollback();
		} finally {
			l.listener.connectionClosed(new ConnectionEvent(pc));
		}
	}

	@Test
	public void testXACommit() throws Exception {
		when(integration.createPooledConnection()).then(i -> TestDatabase.xaDataSource.getXAConnection());

		final var val = IdGenerator.generateId();
		try (final var tm = new IuTransactionManager()) {
			when(integration.getTransactionManager()).thenReturn(tm);
			when(integration.getTransactionSynchronizationRegistry()).thenReturn(tm);

			IuTestLogger.allow(IuTransactionManager.class.getPackageName(), Level.CONFIG);
			tm.begin();
			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("insert into iu_jdbc_pool_test values(?)")) {
				s.setString(1, val);
				s.executeUpdate();
			}
			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
				s.setString(1, val);
				final var rs = s.executeQuery();
				assertTrue(rs.next());
				assertEquals(val, rs.getString(1));
			}
			tm.commit();
		}
		try (final var c = dataSource.getConnection();
				final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
			s.setString(1, val);
			final var rs = s.executeQuery();
			assertTrue(rs.next());
			assertEquals(val, rs.getString(1));
		}
		try (final var c = dataSource.getConnection();
				final var s = c.prepareStatement("delete from iu_jdbc_pool_test where value = ?")) {
			s.setString(1, val);
			s.executeUpdate();
		}
	}

	@Test
	public void testXARollback() throws Exception {
		when(integration.createPooledConnection()).then(i -> TestDatabase.xaDataSource.getXAConnection());

		final var val = IdGenerator.generateId();
		try (final var tm = new IuTransactionManager()) {
			when(integration.getTransactionManager()).thenReturn(tm);
			when(integration.getTransactionSynchronizationRegistry()).thenReturn(tm);

			IuTestLogger.allow(IuTransactionManager.class.getPackageName(), Level.CONFIG);
			tm.begin();
			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("insert into iu_jdbc_pool_test values(?)")) {
				s.setString(1, val);
				s.executeUpdate();
			}
			try (final var c = dataSource.getConnection();
					final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
				s.setString(1, val);
				final var rs = s.executeQuery();
				assertTrue(rs.next());
				assertEquals(val, rs.getString(1));
			}
			tm.rollback();
		}
		try (final var c = dataSource.getConnection();
				final var s = c.prepareStatement("select value from iu_jdbc_pool_test where value = ?")) {
			s.setString(1, val);
			final var rs = s.executeQuery();
			assertFalse(rs.next());
		}
	}

}
