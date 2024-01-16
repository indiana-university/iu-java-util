package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.opentest4j.AssertionFailedError;

import edu.iu.IuObject;
import edu.iu.IuUtilityTaskController;
import edu.iu.UnsafeRunnable;
import edu.iu.UnsafeSupplier;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;

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
		try (final var ds = mockCommonDataSource()) {
			ds.setLoginTimeout(12);
			ds.setAbandonedConnectionTimeout(Duration.ofMillis(34));
			ds.setUrl("jdbc:foo:bar");
			ds.setUsername("foo");
			ds.setSchema("bar");
			ds.setMaxConnectionReuseCount(56);
			ds.setMaxConnectionReuseTime(Duration.ofHours(89L));
			ds.setValidationInterval(Duration.ofNanos(72L));
			ds.setValidationQuery("select foo from bar");
			ds.setShutdownTimeout(Duration.ofSeconds(1L));
			ds.setMaxRetry(45);
			ds.setMaxSize(67);

			final var j = assertDoesNotThrow(() -> Json.createReader(new StringReader(ds.toString())).readObject(),
					ds::toString);
			assertEquals("MockCommonDataSource", j.getString("type"));
			assertEquals(ds.getAvailable(), j.getInt("available"));
			assertEquals(ds.getOpen(), j.getInt("open"));
			assertEquals(ds.isClosed(), j.getBoolean("closed"));
			assertEquals("jdbc:foo:bar", j.getString("url"));
			assertEquals("foo", j.getString("username"));
			assertEquals("bar", j.getString("schema"));
			assertEquals(67, j.getInt("maxSize"));
			assertEquals(45, j.getInt("maxRetry"));
			assertEquals(56, j.getInt("maxConnectionReuseCount"));
			assertEquals("PT89H", j.getString("maxConnectionReuseTime"));
			assertEquals("PT0.034S", j.getString("abandonedConnectionTimeout"));
			assertEquals("select foo from bar", j.getString("validationQuery"));
			assertEquals("PT0.000000072S", j.getString("validationInterval"));
			assertEquals("PT1S", j.getString("shutdownTimeout"));
		}
	}

	@Test
	public void testClose() throws Throwable {
		final var onClose = mock(UnsafeRunnable.class);
		try (final var ds = mockCommonDataSource()) {
			ds.setOnClose(onClose);
			ds.close();
		} // second close is no-op, onClose only runs once
		verify(onClose).run();
	}

	@Test
	public void testOneConnection() throws SQLException {
		try (final var ds = mockCommonDataSource()) {
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			final var p = ds.getPooledConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			final var c = p.getConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			c.close();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		}
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
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			final var lc = ds.getPooledConnection().getConnection();
			assertNotSame(lc, lc.unwrap(Connection.class));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
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

			ds.setConnectionInitializer(c -> {
				final var wc = mock(Connection.class);
				return wc;
			});

			assertThrows(SQLException.class, () -> ds.getPooledConnection().getConnection());
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		}
	}

	@Test
	public void testWaitForLogicalClose() throws Throwable {
		class CloseTask implements UnsafeSupplier<Void> {
			volatile Connection connection;

			@Override
			public Void get() throws Throwable {
				synchronized (this) {
					this.wait();
				}
				IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
				connection.close();
				return null;
			}
		}

		Throwable throwing = null;
		final var closeTask = new CloseTask();
		final var closeTaskController = new IuUtilityTaskController<>(closeTask, Instant.now().plusSeconds(5L));
		try (final var ds = mockCommonDataSource()) {
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			synchronized (closeTask) {
				closeTask.connection = ds.getPooledConnection().getConnection();
				closeTask.notify();
			}
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		} catch (Throwable e) {
			throwing = e;
			throw e;
		} finally {
			if (closeTaskController != null)
				try {
					closeTaskController.get();
				} catch (Throwable e) {
					if (throwing == null)
						throw e;
					else
						throwing.addSuppressed(e);
				}
		}
	}

	@Test
	public void testCloseWhileWaiting() throws TimeoutException, Throwable {
		final var ds = mockCommonDataSource();
		ds.setMaxSize(1);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		final var c = ds.getPooledConnection().getConnection();

		class CloseTask implements UnsafeSupplier<Void> {
			volatile boolean waiting;
			volatile boolean ready;
			volatile boolean done;

			@Override
			public Void get() throws Throwable {
				synchronized (this) {
					waiting = true;
					notifyAll();
					IuObject.waitFor(this, () -> ready, Duration.ofSeconds(5L));
				}
				Thread.sleep(50L);
				c.close();
				done = true;
				return null;
			}
		}
		final var closeTask = new CloseTask();
		final var closeTaskController = new IuUtilityTaskController<>(closeTask, Instant.now().plusSeconds(5L));

		final var connectTask = new IuUtilityTaskController<>(() -> {
			synchronized (closeTask) {
				IuObject.waitFor(closeTask, () -> closeTask.waiting, Duration.ofSeconds(5L));
				closeTask.ready = true;
				closeTask.notifyAll();
			}
			return ds.getPooledConnection();
		}, Instant.now().plusSeconds(5L));

		synchronized (closeTask) {
			IuObject.waitFor(closeTask, () -> closeTask.ready, Duration.ofSeconds(5L));
		}

		Thread.sleep(10L);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		ds.close();
		IuTestLogger.assertExpectedMessages();
		
		assertTrue(closeTask.done);
		closeTaskController.get();

		final var e = assertThrows(SQLException.class, connectTask::get);
		assertInstanceOf(SQLException.class, e.getCause(), () -> {
			e.printStackTrace();
			return e.toString();
		});
		assertEquals("closed", e.getCause().getMessage(), () -> {
			e.printStackTrace();
			return e.toString();
		});
	}

	@Test
	public void testAbandonedConnectionOnClose() throws SQLException {
		try (final var ds = mockCommonDataSource()) {
			ds.setShutdownTimeout(Duration.ofMillis(50L));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			ds.getPooledConnection().getConnection();
			assertEquals("1 connections remaining in the pool after graceful shutdown PT0.05S",
					assertThrows(SQLException.class, ds::close).getMessage());
		}
	}

	@Test
	public void testGetAndValidateConnection() throws SQLException {
		try (final var ds = mockCommonDataSource()) {
			ds.setValidationQuery("");

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			final var p = ds.getPooledConnection();
			IuTestLogger.assertExpectedMessages();

			final var c = p.getConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			c.close();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		}
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

		final var ds = new IuCommonDataSource(f::getPooledConnection) {
		};
		ds.setValidationQuery("");
		ds.setMaxRetry(0);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-logical-close; .*",
				SQLException.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
				SQLException.class);

		final var e = assertThrows(SQLException.class, ds::getPooledConnection);
		assertInstanceOf(SQLException.class, e.getCause(), () -> {
			e.printStackTrace();
			return e.toString();
		});

		ds.close();
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

		final var ds = new IuCommonDataSource(f::getPooledConnection) {
		};
		ds.setValidationQuery("");
		ds.setMaxRetry(0);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-logical-close; .*",
				SQLException.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
				SQLException.class);
		final var e = assertThrows(SQLException.class, ds::getPooledConnection);
		assertInstanceOf(SQLException.class, e.getCause(), () -> {
			e.printStackTrace();
			return e.toString();
		});

		ds.close();
	}

	@Test
	public void testClosePhysicalConnection() throws SQLException {
		try (final var ds = mockCommonDataSource()) {
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			final var p = ds.getPooledConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			final var c = p.getConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			c.close();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
			p.close();
			IuTestLogger.assertExpectedMessages();
		}
	}

	@Test
	public void testCloseConnectionOnError() throws SQLException {
		try (final var ds = mockCommonDataSource()) {
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			final var p = ds.getPooledConnection();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			final var c = p.getConnection();

			final var e = new SQLException();
			when(ds.factory.lastConnection.createStatement()).thenThrow(e);
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-logical-close; .*",
					SQLException.class, a -> a == e);
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
					SQLException.class, a -> a == e);
			
			assertSame(e, assertThrows(SQLException.class, c::createStatement));
		}
	}

	@Test
	public void testGetConnectionTimeout() throws SQLException, InterruptedException, TimeoutException {
		class Box {
			boolean interruped;
			volatile boolean done;
		}
		final var box = new Box();

		final var ds = new IuCommonDataSource(() -> {
			try {
				Thread.sleep(1500L); // to ensure
				// timeout is handled first for consistent
				// test results and coverage
			} catch (InterruptedException e) {
				Thread.sleep(50L);
				box.interruped = true;
				return null;
			} finally {
				synchronized (box) {
					box.done = true;
					box.notifyAll();
				}
			}
			throw new AssertionFailedError();
		}) {
		};
		ds.setLoginTimeout(1);

		final var e = assertThrows(SQLException.class, ds::getPooledConnection);
		assertInstanceOf(SQLException.class, e.getCause());
		assertInstanceOf(TimeoutException.class, e.getCause().getCause());

		IuObject.waitFor(box, () -> box.done, Duration.ofSeconds(1L));
		assertTrue(box.interruped);
		ds.close();
	}

	@Test
	public void testWaitForReuse() throws TimeoutException, Throwable {
		try (final var ds = mockCommonDataSource()) {
			ds.setMaxSize(1);

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");

			class ConnectTask implements UnsafeSupplier<IuPooledConnection> {
				volatile boolean ready;

				@Override
				public IuPooledConnection get() throws Throwable {
					synchronized (this) {
						if (!ready)
							this.wait();
					}
					return openAndClose();
				}

				private IuPooledConnection openAndClose() throws Throwable {
					final var p = ds.getPooledConnection();
					final var c = p.getConnection();
					Thread.sleep(5L);
					c.close();
					return p;
				}
			}
			final var connectTask = new ConnectTask();
			final var connectTaskController = new IuUtilityTaskController<>(connectTask, Instant.now().plusSeconds(5L));
			synchronized (connectTask) {
				connectTask.ready = true;
				connectTask.notify();
			}
			assertSame(connectTask.openAndClose(), connectTaskController.get());
		}
	}

	@Test
	public void testRetireReused() throws TimeoutException, Throwable {
		try (final var ds = mockCommonDataSource()) {
			ds.setMaxSize(1);
			ds.setMaxConnectionReuseCount(2);

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			var p = ds.getPooledConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			var c = p.getConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			c.close();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
			p = ds.getPooledConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			c = p.getConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-retire-count:2 .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
			c.close();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			p = ds.getPooledConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			c = p.getConnection();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			c.close();
			IuTestLogger.assertExpectedMessages();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		}
	}

	@Test
	public void testRetirePhysicalCloseError() throws TimeoutException, Throwable {
		final var ds = mockCommonDataSource();
		ds.setMaxSize(1);
		ds.setMaxConnectionReuseCount(1);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.INFO, "jdbc-pool-retire-count:1 .*",
				SQLException.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
				SQLException.class);
		var p = ds.getPooledConnection();
		doThrow(SQLException.class).when(ds.factory.lastPooledConnection).close();

		p.getConnection().close();
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		assertNotSame(p, ds.getPooledConnection());
		doThrow(SQLException.class).when(ds.factory.lastPooledConnection).close();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.WARNING, "jdbc-pool-close:PT.*",
				SQLException.class);
		assertThrows(SQLException.class, () -> ds.close());
	}

	@Test
	public void testRetireTimeout() throws Throwable {
		final var ds = mockCommonDataSource();
		ds.setMaxSize(1);

		var now = System.currentTimeMillis();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		ds.getPooledConnection().getConnection().close();
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		ds.getPooledConnection().getConnection().close();
		IuTestLogger.assertExpectedMessages();

		ds.setMaxConnectionReuseTime(Duration.ofMillis(Long.min(1L, System.currentTimeMillis() - now - 1)));

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-retire-timeout:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
		final var p = ds.getPooledConnection();
		p.getConnection().close();
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		p.close();
		IuTestLogger.assertExpectedMessages();

		ds.close();
	}

	@Test
	public void testRecoverableConnectionError() throws TimeoutException, Throwable {
		class Status {
			int count = 0;
		}
		final var status = new Status();
		final var ds = new IuCommonDataSource(() -> {
			if (status.count++ < 2)
				throw new SQLException();

			final var pc = mock(PooledConnection.class);
			return pc;
		}) {
			{
				setMaxRetry(5);
			}
		};

		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.INFO, "jdbc-pool-recoverable; .*",
				SQLException.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
		ds.getPooledConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		ds.close();
	}

	@Test
	public void testValidationInterval() throws TimeoutException, Throwable {
		try (final var ds = mockCommonDataSource()) {
			ds.setValidationQuery("");

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-open:PT.*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			var c = ds.getPooledConnection().getConnection();
			verify(ds.factory.lastStatement).executeQuery("");
			c.close();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			c = ds.getPooledConnection().getConnection();
//			assertNull(ds.factory.lastStatement);
			c.close();

			ds.setValidationInterval(Duration.ZERO);
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reuse; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINER, "jdbc-pool-reusable; .*");
			c = ds.getPooledConnection().getConnection();
			verify(ds.factory.lastStatement).executeQuery("");
			c.close();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuCommonDataSource", Level.FINE, "jdbc-pool-close:PT.*");
		}
	}

//	private static class PooledConnectionController implements Answer<Void> {
//		private PooledConnection pooledConnection;
//		private ConnectionEventListener connectionEventListener;
//
//		@Override
//		public Void answer(InvocationOnMock invocation) throws Throwable {
//			pooledConnection = (PooledConnection) invocation.getMock();
//			connectionEventListener = invocation.getArgument(0);
//			return null;
//		}
//
//		private void close() {
//			connectionEventListener.connectionClosed(new ConnectionEvent(pooledConnection));
//		}
//
//		private void error(SQLException e) {
//			connectionEventListener.connectionErrorOccurred(new ConnectionEvent(pooledConnection, e));
//		}
//	}
//
	private static class MockPooledConnectionFactory implements UnsafeSupplier<PooledConnection> {
//		private PooledConnectionController lastController;
		private PooledConnection lastPooledConnection;
		private Connection lastConnection;
		private Statement lastStatement;

		@Override
		public PooledConnection get() throws Throwable {
//			final var controller = new PooledConnectionController();
			final var pc = mock(PooledConnection.class);
			lastPooledConnection = pc;
//			doAnswer(controller).when(pc).addConnectionEventListener(any());

			final Answer<ResultSet> newResultSet = a -> {
				final var r = mock(ResultSet.class);
				when(r.next()).thenReturn(true, false);
				when(r.getObject(1)).thenReturn(new Object());
				return r;
			};

			final Answer<Statement> newStatement = a -> {
				final var s = mock(Statement.class);
				when(s.executeQuery("")).thenAnswer(newResultSet);
				lastStatement = s;
				return s;
			};

			final Answer<Connection> newConnection = a -> {
				final var c = mock(Connection.class);
				when(c.createStatement()).thenAnswer(newStatement);
				when(c.unwrap(Connection.class)).thenReturn(c);
//				doAnswer(ab -> {
////					controller.close();
//					return null;
//				}).when(c).close();
				lastStatement = null;
				lastConnection = c;
				return c;
			};

			when(pc.getConnection()).thenAnswer(newConnection);

//			lastController = controller;
			return pc;
		}
	}

	private static class MockCommonDataSource extends IuCommonDataSource {
		private MockPooledConnectionFactory factory;

		private MockCommonDataSource(MockPooledConnectionFactory factory) {
			super(factory);
			this.factory = factory;
			setShutdownTimeout(Duration.ofSeconds(1L));
		}
	}

	private MockCommonDataSource mockCommonDataSource() {
		return new MockCommonDataSource(new MockPooledConnectionFactory());
	}

}
