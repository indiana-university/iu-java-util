package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import edu.iu.IuObject;
import edu.iu.IuUtilityTaskController;
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
	public void testGetAndValidateConnection() throws SQLException {
		final var r = mock(ResultSet.class);
		when(r.next()).thenReturn(true, false);
		when(r.getObject(1)).thenReturn(1);

		final var s = mock(Statement.class);
		when(s.executeQuery("")).thenReturn(r);

		final var c = mock(Connection.class);
		when(c.createStatement()).thenReturn(s);

		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenReturn(c);

		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenReturn(pc);

		final var ds = new IuConnectionPoolDataSource(f::getPooledConnection);
		ds.setValidationQuery("");

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c + "; IuPooledConnection .*");
		final var p = ds.getPooledConnection();

		p.getConnection();
	}

	@Test
	public void testValidateConnectionMissingRow() throws SQLException {
		final var r = mock(ResultSet.class);
		when(r.next()).thenReturn(false);

		final var s = mock(Statement.class);
		when(s.executeQuery("")).thenReturn(r);

		final var c = mock(Connection.class);
		when(c.createStatement()).thenReturn(s);

		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenReturn(c);

		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenReturn(pc);

		final var ds = new IuConnectionPoolDataSource(f::getPooledConnection);
		ds.setValidationQuery("");
		ds.setMaxRetry(0);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c + "; IuPooledConnection .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO,
				"jdbc-pool-logical-close:" + c + "; IuPooledConnection .*", SQLException.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
				SQLException.class);
		
		final var e = assertThrows(SQLException.class, ds::getPooledConnection);
		assertInstanceOf(SQLException.class, e.getCause(), () -> {
			e.printStackTrace();
			return e.toString();
		});
	}

	@Test
	public void testValidateConnectionMissingValue() throws SQLException {
		final var r = mock(ResultSet.class);
		when(r.next()).thenReturn(true, false);

		final var s = mock(Statement.class);
		when(s.executeQuery("")).thenReturn(r);

		final var c = mock(Connection.class);
		when(c.createStatement()).thenReturn(s);

		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenReturn(c);

		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenReturn(pc);

		final var ds = new IuConnectionPoolDataSource(f::getPooledConnection);
		ds.setValidationQuery("");
		ds.setMaxRetry(0);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c + "; IuPooledConnection .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO,
				"jdbc-pool-logical-close:" + c + "; IuPooledConnection .*", SQLException.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
				SQLException.class);
		final var e = assertThrows(SQLException.class, ds::getPooledConnection);
		assertInstanceOf(SQLException.class, e.getCause(), () -> {
			e.printStackTrace();
			return e.toString();
		});
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
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + mc + "; .*");
		p.getConnection();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-close:" + mc + "; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER,
				"jdbc-pool-reusable; IuPooledConnection .*");
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
		final var f = mock(ConnectionPoolDataSource.class);
		when(f.getPooledConnection()).thenThrow(SQLException.class);

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
		final var e = assertThrows(SQLException.class, ds::getPooledConnection);
		assertInstanceOf(SQLException.class, e.getCause());
		assertInstanceOf(TimeoutException.class, e.getCause().getCause());

		IuObject.waitFor(box, () -> box.done, Duration.ofSeconds(1L));
		assertTrue(box.interruped);
	}

	@Test
	public void testWaitForReuse() throws TimeoutException, Throwable {
		final var ds = new IuConnectionPoolDataSource(() -> {
			class Box {
				ConnectionEventListener listener;
			}
			final var box = new Box();

			final var pc = mock(PooledConnection.class);
			doAnswer(a -> {
				box.listener = a.getArgument(0);
				return null;
			}).when(pc).addConnectionEventListener(any());

			final var c = mock(Connection.class);
			doAnswer(a -> {
				box.listener.connectionClosed(new ConnectionEvent(pc));
				return null;
			}).when(c).close();
			when(pc.getConnection()).thenReturn(c);

			return pc;
		});
		ds.setMaxSize(1);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");

		final var task = new IuUtilityTaskController<>(() -> {
			final var p = ds.getPooledConnection();
			final var c = p.getConnection();
			Thread.sleep(250L);
			c.close();
			return p;
		}, Instant.now().plusSeconds(5L));

		final var p = ds.getPooledConnection();
		final var c = p.getConnection();
		Thread.sleep(250L);

		c.close();

		assertSame(p, task.get());

		p.close();
	}

	@Test
	public void testRetireReused() throws TimeoutException, Throwable {
		final var ds = new IuConnectionPoolDataSource(() -> {
			class Box {
				ConnectionEventListener listener;
			}
			final var box = new Box();

			final var pc = mock(PooledConnection.class);
			doAnswer(a -> {
				box.listener = a.getArgument(0);
				return null;
			}).when(pc).addConnectionEventListener(any());

			final var c = mock(Connection.class);
			doAnswer(a -> {
				box.listener.connectionClosed(new ConnectionEvent(pc));
				return null;
			}).when(c).close();
			when(pc.getConnection()).thenReturn(c);

			return pc;
		});
		ds.setMaxSize(1);
		ds.setMaxConnectionReuseCount(2);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		var p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		var c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		c.close();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
		p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-retire-count:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		c.close();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		c.close();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		p.close();
	}

	@Test
	public void testRetirePhysicalCloseError() throws TimeoutException, Throwable {
		final var ds = new IuConnectionPoolDataSource(() -> {
			class Box {
				ConnectionEventListener listener;
			}
			final var box = new Box();

			final var pc = mock(PooledConnection.class);
			doAnswer(a -> {
				box.listener = a.getArgument(0);
				return null;
			}).when(pc).addConnectionEventListener(any());

			final var c = mock(Connection.class);
			doAnswer(a -> {
				box.listener.connectionClosed(new ConnectionEvent(pc));
				return null;
			}).when(c).close();
			when(pc.getConnection()).thenReturn(c);

			doThrow(SQLException.class).when(pc).close();
			return pc;
		});
		ds.setMaxSize(1);
		ds.setMaxConnectionReuseCount(1);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		var p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		var c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.INFO, "jdbc-pool-retire-count:.*",
				SQLException.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
				SQLException.class);
		c.close();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		assertNotSame(p, ds.getPooledConnection());
	}

	@Test
	public void testRetireTimeout() throws TimeoutException, Throwable {
		final var ds = new IuConnectionPoolDataSource(() -> {
			class Box {
				ConnectionEventListener listener;
			}
			final var box = new Box();

			final var pc = mock(PooledConnection.class);
			doAnswer(a -> {
				box.listener = a.getArgument(0);
				return null;
			}).when(pc).addConnectionEventListener(any());

			final var c = mock(Connection.class);
			doAnswer(a -> {
				box.listener.connectionClosed(new ConnectionEvent(pc));
				return null;
			}).when(c).close();
			when(pc.getConnection()).thenReturn(c);

			return pc;
		});
		ds.setMaxSize(1);
		ds.setMaxConnectionReuseTime(Duration.ofMillis(100L));

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		var p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		var c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		c.close();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
		p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		c.close();

		Thread.sleep(100L);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-retire-timeout:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		c.close();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		p.close();
	}

	@Test
	public void testRecoverableConnectionError() throws TimeoutException, Throwable {
		class Status {
			boolean first = true;
		}
		final var status = new Status();
		final var ds = new IuConnectionPoolDataSource(() -> {
			if (status.first) {
				status.first = false;
				throw new SQLException();
			}

			final var pc = mock(PooledConnection.class);
			return pc;
		});

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.INFO, "jdbc-pool-recoverable; .*",
				SQLException.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		ds.getPooledConnection();
	}

	@Test
	public void testValidationInterval() throws TimeoutException, Throwable {
		final var ds = new IuConnectionPoolDataSource(() -> {
			class Box {
				ConnectionEventListener listener;
			}
			final var box = new Box();

			final var pc = mock(PooledConnection.class);
			doAnswer(a -> {
				box.listener = a.getArgument(0);
				return null;
			}).when(pc).addConnectionEventListener(any());

			final var r = mock(ResultSet.class);
			when(r.next()).thenReturn(true, true, false);
			when(r.getObject(1)).thenReturn(1);

			final var s = mock(Statement.class);
			when(s.executeQuery("")).thenReturn(r);

			final var c = mock(Connection.class);
			when(c.createStatement()).thenReturn(s);
			doAnswer(a -> {
				box.listener.connectionClosed(new ConnectionEvent(pc));
				return null;
			}).when(c).close();
			when(pc.getConnection()).thenReturn(c);

			return pc;
		});
		ds.setValidationInterval(Duration.ofMillis(100L));
		ds.setValidationQuery("");

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		var p = ds.getPooledConnection();
		var c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		c.close();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
		p = ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		c.close();

		Thread.sleep(100L);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		p = ds.getPooledConnection();
		c = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		c.close();

		verify(c.createStatement(), times(2)).executeQuery("");

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		p.close();
	}

}
