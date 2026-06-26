package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Level;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import edu.iu.transaction.IuTransactionManager;

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
