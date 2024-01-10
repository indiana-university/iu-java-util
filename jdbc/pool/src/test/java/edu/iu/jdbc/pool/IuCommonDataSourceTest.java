package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.logging.Level;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuCommonDataSourceTest {

	@Test
	public void testLogMethods() throws SQLException {
		try (final var ds = new IuCommonDataSource(null) {
		}) {
			assertEquals(getClass().getPackageName(), ds.getParentLogger().getName());
			assertNull(ds.getLogWriter());
			assertThrows(SQLFeatureNotSupportedException.class, () -> ds.setLogWriter(null));
			assertEquals(15, ds.getLoginTimeout());
			assertThrows(IllegalArgumentException.class, () -> ds.setLoginTimeout(-1));
			ds.setLoginTimeout(1);
			assertEquals(1, ds.getLoginTimeout());
			ds.setLoginTimeout(0);
			assertEquals(15, ds.getLoginTimeout());
		}
	}

	@Test
	public void testLoginTimeout() throws SQLException {
		try (final var ds = new IuCommonDataSource(null) {
		}) {
			assertEquals(15, ds.getLoginTimeout());
			assertThrows(IllegalArgumentException.class, () -> ds.setLoginTimeout(-1));
			ds.setLoginTimeout(1);
			assertEquals(1, ds.getLoginTimeout());
			ds.setLoginTimeout(0);
			assertEquals(15, ds.getLoginTimeout());
		}
	}

	@Test
	public void testSetAndToStringMethods() throws SQLException {
		final var ds = new IuCommonDataSource(null) {
		};
		assertEquals(
				" [loginTimeout=15, closed=false, url=null, username=null, schema=null, available=0, open=0, maxSize=16, maxRetry=1, maxConnectionReuseCount=100, maxConnectionReuseTime=PT15M, abandonedConnectionTimeout=PT30M, validationQuery=null, validationInterval=PT15S]",
				ds.toString());
		ds.setLoginTimeout(12);
		ds.setAbandonedConnectionTimeout(Duration.ofMillis(34));
		ds.setUrl("jdbc:foo:bar");
		ds.setUsername("foo");
		ds.setSchema("bar");
		ds.setMaxConnectionReuseCount(56);
		ds.setMaxConnectionReuseTime(Duration.ofHours(89L));
		ds.setValidationInterval(Duration.ofNanos(72L));
		ds.setValidationQuery("select foo from bar");
		ds.setMaxRetry(45);
		ds.setMaxSize(67);
		assertEquals(
				" [loginTimeout=12, closed=false, url=jdbc:foo:bar, username=foo, schema=bar, available=0, open=0, maxSize=67, maxRetry=45, maxConnectionReuseCount=56, maxConnectionReuseTime=PT89H, abandonedConnectionTimeout=PT0.034S, validationQuery=select foo from bar, validationInterval=PT0.000000072S]",
				ds.toString());
		ds.close();
	}

	@Test
	public void testConnectionInitializer() throws SQLException {
		try (final var ds = mockCommonDataSource()) {
			ds.setMaxRetry(0);
			ds.setShutdownTimeout(Duration.ofMillis(1L));
			ds.setConnectionInitializer(c -> {
				final var wc = mock(Connection.class);
				when(wc.unwrap(Connection.class)).thenReturn(c);
				doAnswer(a -> {
					c.close();
					return null;
				}).when(wc).close();
				return wc;
			});

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
			final var lc = ds.getPooledConnection().getConnection();
			assertNotSame(lc, lc.unwrap(Connection.class));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			lc.close();
			
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		}
	}

	@Test
	public void testInvalidConnectionInitializer() throws SQLException {
		try (final var ds = mockCommonDataSource()) {
			ds.setMaxRetry(0);
			ds.setShutdownTimeout(Duration.ofMillis(1L));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");

			ds.setConnectionInitializer(c -> {
				final var wc = mock(Connection.class);
				return wc;
			});

			assertThrows(SQLException.class, () -> ds.getPooledConnection().getConnection());
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		}
	}

	@Test
	public void testClose() throws Throwable {
		final var pc1 = mock(PooledConnection.class);
		final var c1 = mock(Connection.class);
		when(c1.unwrap(Connection.class)).thenReturn(c1);
		when(pc1.getConnection()).thenReturn(c1);

		final var ds = new IuCommonDataSource(() -> pc1) {
		};
		final var onClose = mock(UnsafeRunnable.class);
		ds.setOnClose(onClose);
		ds.close();
		verify(onClose).run();
	}

	private IuCommonDataSource mockCommonDataSource() {
		return new IuCommonDataSource(() -> {
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
			when(c.unwrap(Connection.class)).thenReturn(c);
			when(pc.getConnection()).thenReturn(c);
			doAnswer(a -> {
				box.listener.connectionClosed(new ConnectionEvent(pc));
				return null;
			}).when(c).close();
			return pc;
		}) {
		};
	}

}
