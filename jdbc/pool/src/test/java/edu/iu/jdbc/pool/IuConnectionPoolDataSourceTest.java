package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import edu.iu.IuObject;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuConnectionPoolDataSourceTest {

	@Test
	public void testGetConnection() throws SQLException {
		final var c = mock(Connection.class);
		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenReturn(c);

		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenReturn(pc);

		final var ds = new IuConnectionPoolDataSource(f::getPooledConnection);
		assertThrows(SQLFeatureNotSupportedException.class, () -> ds.getPooledConnection(null, null));

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		final var p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c + "; IuPooledConnection .*");
		p.getConnection();
	}

	@Test
	public void testCloseConnection() throws SQLException {
		class Box {
			ConnectionEventListener listener;
		}
		final var box = new Box();

		final var mc = mock(Connection.class);
		final var pc = mock(PooledConnection.class);
		doAnswer(a -> {
			box.listener = a.getArgument(0, ConnectionEventListener.class);
			return null;
		}).when(pc).addConnectionEventListener(any());
		when(pc.getConnection()).thenReturn(mc);

		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenReturn(pc);

		final var ds = new IuConnectionPoolDataSource(f::getPooledConnection);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		final var p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:" + mc + "; .*");
		p.getConnection();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-close:" + mc + "; .*");
		box.listener.connectionClosed(new ConnectionEvent(pc));

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		p.close();
	}

	@Test
	public void testCloseConnectionOnError() throws SQLException {
		class Box {
			ConnectionEventListener listener;
		}
		final var box = new Box();

		final var c = mock(Connection.class);
		final var pc = mock(PooledConnection.class);
		doAnswer(a -> {
			box.listener = a.getArgument(0, ConnectionEventListener.class);
			return null;
		}).when(pc).addConnectionEventListener(any());
		when(pc.getConnection()).thenReturn(c);

		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenReturn(pc);

		final var ds = new IuConnectionPoolDataSource(f::getPooledConnection);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		final var p = ds.getPooledConnection();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:" + c + "; .*");
		p.getConnection();

		final var e = new SQLException();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-logical-close:" + c + "; .*",
				SQLException.class, a -> a == e);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
				SQLException.class, a -> a == e);
		box.listener.connectionErrorOccurred(new ConnectionEvent(pc, e));
		// no-op
		box.listener.connectionErrorOccurred(new ConnectionEvent(pc, e));
	}

	@Test
	public void testGetConnectionError() throws SQLException {
		final var e = new SQLException();
		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenThrow(e);

		final var ds = new IuConnectionPoolDataSource(f::getPooledConnection);
		assertThrows(SQLException.class, ds::getPooledConnection);
	}

	@Test
	public void testGetConnectionTimeout() throws SQLException, InterruptedException, TimeoutException {
		class Box {
			boolean interruped;
			volatile boolean done;
		}
		final var box = new Box();

		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenAnswer(a -> {
			try {
				Thread.sleep(1500L);
			} catch (InterruptedException e) {
				Thread.sleep(50L); // ensure timeout is handled first
				// for consistent test results
				box.interruped = true;
				return null;
			} finally {
				synchronized (box) {
					box.done = true;
					box.notifyAll();
				}
			}
			throw new AssertionFailedError();
		});

		final var ds = new IuConnectionPoolDataSource(f::getPooledConnection);
		ds.setLoginTimeout(1);
		assertInstanceOf(TimeoutException.class, assertThrows(SQLException.class, ds::getPooledConnection).getCause());

		IuObject.waitFor(box, () -> box.done, Duration.ofSeconds(1L));
		assertTrue(box.interruped);
	}

}
