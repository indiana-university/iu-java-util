package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeSupplier;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuPooledConnectionTest {

	@Test
	public void testConnectionEventListener() throws SQLException {
		final var pc = mock(PooledConnection.class);

		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});

		final var l = mock(ConnectionEventListener.class);
		p.addConnectionEventListener(l);

		final var mc = mock(Connection.class);
		when(pc.getConnection()).thenReturn(mc);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		var c = p.getConnection();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		
		c.close();
		verify(l).connectionClosed(argThat(a -> a.getSource() == p));

		p.removeConnectionEventListener(l);
		p.removeConnectionEventListener(l); // no-op

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		c = p.getConnection();

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		c.close();
		verify(l).connectionClosed(argThat(a -> a.getSource() == p)); // not twice
	}

	@Test
	public void testStatementReuse() throws SQLException {
		final var pc = mock(PooledConnection.class);
		final var s1 = mock(PreparedStatement.class);
		final var s2 = mock(PreparedStatement.class);
		final var s3 = mock(PreparedStatement.class);
		final var c = mock(Connection.class);
		when(c.unwrap(Connection.class)).thenReturn(c);
		when(c.prepareStatement("")).thenReturn(s1, s2, s3);
		when(pc.getConnection()).thenReturn(c);

		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		try (final var connection = p.getConnection()) {
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-open; .*");
			final var ps1 = connection.prepareStatement("");
			ps1.execute();
			verify(s1).execute();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-open; .*");
			final var ps2 = connection.prepareStatement("");
			ps2.execute();
			verify(s2).execute();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-close; .*");
			ps1.close();
			verify(s1, never()).close();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-close; .*");
			ps2.close();
			verify(s2, never()).close();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-reuse; .*");
			final var ps1r1 = connection.prepareStatement("");
			ps1r1.execute();
			verify(s1, times(2)).execute();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-close; .*");
			ps1r1.close();
			verify(s1, never()).close();

			final var e = new SQLException();
			when(s2.execute()).thenThrow(e);
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-reuse; .*");
			final var ps2r1 = connection.prepareStatement("");

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-statement-error; .*",
					SQLException.class, a -> a == e);
			assertSame(e, assertThrows(SQLException.class, () -> ps2r1.execute()));
			verify(s2).close();

			final var r = new RuntimeException();
			when(s1.execute()).thenThrow(r);
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-reuse; .*");
			final var ps1r2 = connection.prepareStatement("");

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-statement-error; .*",
					SQLException.class, a -> a.getCause() == r);
			assertSame(r, assertThrows(RuntimeException.class, () -> ps1r2.execute()));
			verify(s1).close();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-open; .*");
			final var ps3 = connection.prepareStatement("");
			ps3.execute();
			verify(s3).execute();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-close; .*");
			ps3.close();
			verify(s3, never()).close();

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-reuse; .*");
			final var ps3r1 = connection.prepareStatement("");
			ps3r1.execute();
			verify(s3, times(2)).execute();
			
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		}
		p.close();
	}

	@Test
	public void testStatementKey() {
		final var k1 = new IuPooledConnection.StatementKey(PreparedStatement.class, new Object[0]);
		assertNotEquals(k1, this);
		assertNotEquals(k1, null);

		final var k1a = new IuPooledConnection.StatementKey(PreparedStatement.class, new Object[0]);
		assertEquals(k1, k1a);
		assertEquals(k1a, k1);

		final var k2 = new IuPooledConnection.StatementKey(CallableStatement.class, new Object[] { new Object() });
		assertNotEquals(k1, k2);
		assertNotEquals(k2, k1);

		final var k2a = new IuPooledConnection.StatementKey(CallableStatement.class, new Object[] { new Object() });
		assertNotEquals(k2, k2a);
		assertNotEquals(k2a, k2);
	}

	@Test
	public void testStatementEventListeners() throws SQLException {
		final var pc = mock(PooledConnection.class);
		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});

		final var l = mock(StatementEventListener.class);
		p.addStatementEventListener(l);

		final var s = mock(PreparedStatement.class);
		final var c = mock(Connection.class);
		when(c.unwrap(Connection.class)).thenReturn(c);
		when(c.prepareStatement("")).thenReturn(s);
		when(pc.getConnection()).thenReturn(c);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-open; .*");
		final var ps = p.getConnection().prepareStatement("");

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-close; .*");
		ps.close();
		verify(l).statementClosed(argThat(ev -> ev.getSource() == p && ev.getStatement() == s));

		final var e = new SQLException();
		when(ps.execute()).thenThrow(e);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-statement-error; .*",
				SQLException.class);
		assertThrows(SQLException.class, ps::execute);
		verify(l).statementErrorOccurred(
				argThat(ev -> ev.getSource() == p && ev.getStatement() == s && ev.getSQLException() == e));

		p.removeStatementEventListener(l);

		// no twice
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-close; .*");
		ps.close();
		verify(l).statementClosed(argThat(ev -> ev.getSource() == p && ev.getStatement() == s));
		
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-statement-error; .*",
				SQLException.class);
		assertThrows(SQLException.class, ps::execute);
		verify(l).statementErrorOccurred(
				argThat(ev -> ev.getSource() == p && ev.getStatement() == s && ev.getSQLException() == e));

		p.close();
	}

	@Test
	public void testGetConnection() throws SQLException, InterruptedException {
		final var c = mock(Connection.class);
		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenReturn(c);

		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		p.getConnection();
		assertSame(c, pc.getConnection());
		p.close();
	}

	@Test
	public void testConnectionStatsAndErrors() throws Throwable {
		final UnsafeSupplier<Connection> mockConnection = () -> {
			final var c = mock(Connection.class);
			when(c.unwrap(Connection.class)).thenReturn(c);
			return c;
		};
		final var c1 = mockConnection.get();
		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenReturn(c1);

		var now = Instant.now();
		final var p = new IuPooledConnection(now, pc, null, Duration.ofSeconds(5L), a -> {
		});
		assertSame(now, p.getConnectionInitiated());

		final var l = mock(ConnectionEventListener.class);
		p.addConnectionEventListener(l);

		var opened = p.getConnectionOpened();
		assertFalse(opened.isBefore(now));

		assertNull(p.getLastTransactionSegmentStarted());
		assertNull(p.getLastTransactionSegmentEnded());
		assertNull(p.getAverageTransactionSegmentDuration());
		assertNull(p.getMaxTransactionSegmentDuration());
		assertEquals(0, p.getTransactionSegmentCount());

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		var lc = p.getConnection();
		assertNotSame(c1, lc);
		assertSame(c1, lc.unwrap(Connection.class));

		assertThrows(IllegalStateException.class, p::getConnection);
		var logicalOpen = p.getLogicalConnectionOpened();

		Thread.sleep(25L);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		lc.close();

		assertEquals(logicalOpen, p.getLastTransactionSegmentStarted());
		var logicalClose = p.getLastTransactionSegmentEnded();
		assertNotNull(logicalClose);
		var segmentDuration = Duration.between(logicalOpen, logicalClose);
		assertEquals(segmentDuration, p.getAverageTransactionSegmentDuration());
		assertEquals(segmentDuration, p.getMaxTransactionSegmentDuration());
		assertEquals(1, p.getTransactionSegmentCount());

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		lc = p.getConnection();
		logicalOpen = p.getLogicalConnectionOpened();

		Thread.sleep(200L);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close; .*");
		lc.close();

		System.out.println(p);
		assertEquals(logicalOpen, p.getLastTransactionSegmentStarted());
		logicalClose = p.getLastTransactionSegmentEnded();
		assertNotNull(logicalClose);
		segmentDuration = Duration.between(logicalOpen, logicalClose);
		assertTrue(segmentDuration.compareTo(p.getAverageTransactionSegmentDuration()) > 0);
		assertEquals(segmentDuration, p.getMaxTransactionSegmentDuration());
		assertEquals(2, p.getTransactionSegmentCount());

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		lc = p.getConnection();
		logicalOpen = p.getLogicalConnectionOpened();

		Thread.sleep(25L);
		final var e = new SQLException();
		final var r = new RuntimeException();
		when(c1.createStatement()).thenThrow(e, r);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-logical-close; .*",
				SQLException.class, a -> a == e);
		assertSame(e, assertThrows(SQLException.class, lc::createStatement));
		verify(l).connectionErrorOccurred(argThat(ev -> ev.getSource() == p && ev.getSQLException() == e));

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-logical-close; .*",
				SQLException.class, a -> a.getCause() == r);
		assertSame(r, assertThrows(RuntimeException.class, lc::createStatement));
		verify(l).connectionErrorOccurred(argThat(ev -> ev.getSource() == p && ev.getSQLException().getCause() == r));

		assertEquals(logicalOpen, p.getLastTransactionSegmentStarted());
		logicalClose = p.getLastTransactionSegmentEnded();
		assertNotNull(logicalClose);
		segmentDuration = Duration.between(logicalOpen, logicalClose);
		assertTrue(segmentDuration.compareTo(p.getAverageTransactionSegmentDuration()) < 0);
		assertTrue(segmentDuration.compareTo(p.getMaxTransactionSegmentDuration()) < 0);
		assertEquals(3, p.getTransactionSegmentCount());

		assertSame(e, assertThrows(IllegalStateException.class, p::getConnection).getCause());
	}

	@Test
	public void testGetConnectionErrorFromFactory() throws SQLException {
		final var e = new SQLException();
		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenThrow(e);

		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});
		assertSame(e, assertThrows(SQLException.class, p::getConnection));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testClose() throws SQLException {
		final var pc = mock(PooledConnection.class);
		final var onClose = mock(Consumer.class);
		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), onClose);
		p.close();
		verify(onClose).accept(p);
		assertThrows(IllegalStateException.class, p::getConnection);
		p.close();
		verify(onClose).accept(p); // onClose not called twice
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReaper() throws SQLException, InterruptedException {
		final var pc = mock(PooledConnection.class);
		final var c = mock(Connection.class);
		when(pc.getConnection()).thenReturn(c);
		final var onClose = mock(Consumer.class);
		final var p = new IuPooledConnection(null, pc, null, Duration.ofMillis(100L), onClose);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		final var lc = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-reaper-close; .*",
				Throwable.class, a -> "opened by".equals(a.getMessage()));
		Thread.sleep(200L);
		verify(pc).close();
		lc.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReaperHandlesError() throws SQLException, InterruptedException {
		final var pc = mock(PooledConnection.class);
		doThrow(SQLException.class).when(pc).close();
		final var c = mock(Connection.class);
		when(pc.getConnection()).thenReturn(c);
		final var onClose = mock(Consumer.class);
		final var p = new IuPooledConnection(null, pc, null, Duration.ofMillis(100L), onClose);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open; .*");
		final var lc = p.getConnection();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.WARNING, "jdbc-pool-reaper-fail; .*",
				SQLException.class);
		Thread.sleep(200L);
		verify(pc).close();
		lc.close();
	}

}
