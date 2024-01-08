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
import static org.mockito.Mockito.mock;
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
import javax.sql.StatementEvent;
import javax.sql.StatementEventListener;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeSupplier;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuPooledConnectionTest {

	@Test
	public void testConnectionEventListener() throws SQLException {
		final var pc = mock(PooledConnection.class);

		class Box {
			ConnectionEventListener decorated;
		}
		final var box = new Box();

		doAnswer(a -> {
			box.decorated = a.getArgument(0, ConnectionEventListener.class);
			return null;
		}).when(pc).addConnectionEventListener(any());
		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});
		assertNotNull(box.decorated);
		assertThrows(NullPointerException.class, () -> box.decorated.connectionClosed(new ConnectionEvent(pc)));

		final var l = mock(ConnectionEventListener.class);
		p.addConnectionEventListener(l);

		final var mc = mock(Connection.class);
		when(pc.getConnection()).thenReturn(mc);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		p.getConnection();

		final var event = mock(ConnectionEvent.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		box.decorated.connectionClosed(event);
		verify(l).connectionClosed(argThat(a -> a.getSource() == p));

		p.removeConnectionEventListener(l);
		p.removeConnectionEventListener(l); // no-op

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		p.getConnection();

		var event2 = mock(ConnectionEvent.class);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-close:.*");
		box.decorated.connectionClosed(event2);
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

		class Box {
			StatementEventListener decorated;
		}
		final var box = new Box();

		doAnswer(a -> {
			box.decorated = a.getArgument(0, StatementEventListener.class);
			return null;
		}).when(pc).addStatementEventListener(any());
		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});
		assertNotNull(box.decorated);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c + "; IuPooledConnection .*");
		try (final var connection = p.getConnection()) {
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-open:" + c + ":" + s1 + "; IuPooledConnection .*");
			assertSame(s1, connection.prepareStatement(""));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-open:" + c + ":" + s2 + "; IuPooledConnection .*");
			assertSame(s2, connection.prepareStatement(""));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-close:" + c + ":" + s1 + "; IuPooledConnection .*");
			box.decorated.statementClosed(new StatementEvent(pc, s1));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-close:" + c + ":" + s2 + "; IuPooledConnection .*");
			box.decorated.statementClosed(new StatementEvent(pc, s2));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-reuse:" + c + ":" + s1 + "; IuPooledConnection .*");
			assertSame(s1, connection.prepareStatement(""));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-close:" + c + ":" + s1 + "; IuPooledConnection .*");
			box.decorated.statementClosed(new StatementEvent(pc, s1));

			final var e = new SQLException();
			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO,
					"jdbc-pool-statement-error:" + c + ":" + s1 + "; IuPooledConnection .*", SQLException.class,
					a -> a == e);
			box.decorated.statementErrorOccurred(new StatementEvent(pc, s1, e));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO,
					"jdbc-pool-statement-error:" + c + ":" + s1 + "; IuPooledConnection .*", SQLException.class,
					a -> a == e); // handle dup
			box.decorated.statementErrorOccurred(new StatementEvent(pc, s1, e));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO,
					"jdbc-pool-statement-error:" + c + ":" + s2 + "; IuPooledConnection .*", SQLException.class,
					a -> a == e);
			box.decorated.statementErrorOccurred(new StatementEvent(pc, s2, e));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO,
					"jdbc-pool-statement-error:" + c + ":" + s2 + "; IuPooledConnection .*", SQLException.class,
					a -> a == e); // handle dup
			box.decorated.statementErrorOccurred(new StatementEvent(pc, s2, e));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-open:" + c + ":" + s3 + "; IuPooledConnection .*");
			assertSame(s3, connection.prepareStatement(""));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-close:" + c + ":" + s3 + "; IuPooledConnection .*");
			box.decorated.statementClosed(new StatementEvent(pc, s3));

			IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
					"jdbc-pool-statement-reuse:" + c + ":" + s3 + "; IuPooledConnection .*");
			assertSame(s3, connection.prepareStatement(""));
		}
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

		class Box {
			StatementEventListener decorated;
		}
		final var box = new Box();

		doAnswer(a -> {
			box.decorated = a.getArgument(0, StatementEventListener.class);
			return null;
		}).when(pc).addStatementEventListener(any());
		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});
		assertNotNull(box.decorated);

		final var l = mock(StatementEventListener.class);
		p.addStatementEventListener(l);

		final var s = mock(PreparedStatement.class);
		final var c = mock(Connection.class);
		when(c.unwrap(Connection.class)).thenReturn(c);
		when(c.prepareStatement("")).thenReturn(s);
		when(pc.getConnection()).thenReturn(c);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-logical-open:.*");
		final var lc = p.getConnection();
		assertNotSame(c, lc);
		assertSame(c, lc.unwrap(Connection.class));

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-open:.*");
		assertSame(s, lc.prepareStatement(""));

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-close:.*");
		box.decorated.statementClosed(new StatementEvent(pc, s));
		verify(l).statementClosed(argThat(ev -> ev.getSource() == p && ev.getStatement() == s));

		final var e = new SQLException();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO, "jdbc-pool-statement-error:.*",
				SQLException.class, a -> a == e);
		box.decorated.statementErrorOccurred(new StatementEvent(pc, s, e));
		verify(l).statementErrorOccurred(
				argThat(ev -> ev.getSource() == p && ev.getStatement() == s && ev.getSQLException() == e));

		p.removeStatementEventListener(l);

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER, "jdbc-pool-statement-close:.*");
		box.decorated.statementClosed(new StatementEvent(pc, s, e));
		// no twice
		verify(l).statementClosed(argThat(ev -> ev.getSource() == p && ev.getStatement() == s));
		verify(l).statementErrorOccurred(
				argThat(ev -> ev.getSource() == p && ev.getStatement() == s && ev.getSQLException() == e));

	}

	@Test
	public void testGetConnection() throws SQLException, InterruptedException {
		final var c = mock(Connection.class);
		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenReturn(c);

		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c + "; IuPooledConnection .*");
		p.getConnection();
		assertSame(c, pc.getConnection());
	}

	@Test
	public void testReuseConnection() throws SQLException {
		final var pc = mock(PooledConnection.class);
		final var p = new IuPooledConnection(null, pc, null, Duration.ofSeconds(1L), a -> {
		});
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-reuse; IuPooledConnection .*");
		assertTrue(p.reuse());
		assertFalse(p.reuse());
	}

	@Test
	public void testConnectionStatsAndErrors() throws Throwable {
		class Box {
			ConnectionEventListener listener;
		}
		final var box = new Box();

		final UnsafeSupplier<Connection> mockConnection = () -> {
			final var c = mock(Connection.class);
			when(c.unwrap(Connection.class)).thenReturn(c);
			return c;
		};
		final var c1 = mockConnection.get();
		final var c2 = mockConnection.get();
		final var c3 = mockConnection.get();
		final var pc = mock(PooledConnection.class);
		when(pc.getConnection()).thenReturn(c1, c2, c3);
		doAnswer(a -> {
			return box.listener = a.getArgument(0);
		}).when(pc).addConnectionEventListener(any());

		var now = Instant.now();
		final var p = new IuPooledConnection(now, pc, null, Duration.ofSeconds(1L), a -> {
		});
		assertSame(now, p.connectionInitiated());

		final var l = mock(ConnectionEventListener.class);
		p.addConnectionEventListener(l);

		var opened = p.connectionOpened();
		assertFalse(opened.isBefore(now));
		assertNotNull(box.listener);

		assertNull(p.lastTransactionSegmentStarted());
		assertNull(p.lastTransactionSegmentEnded());
		assertNull(p.averageTransactionSegmentDuration());
		assertNull(p.maxTransactionSegmentDuration());
		assertEquals(0, p.transactionSegmentCount());

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c1 + "; IuPooledConnection .*");
		var lc = p.getConnection();
		assertNotSame(c1, lc);
		assertSame(c1, lc.unwrap(Connection.class));

		assertThrows(IllegalStateException.class, p::getConnection);
		var logicalOpen = p.logicalConnectionOpened();

		Thread.sleep(25L);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-close:" + c1 + "; IuPooledConnection .*");
		box.listener.connectionClosed(new ConnectionEvent(pc));

		assertEquals(logicalOpen, p.lastTransactionSegmentStarted());
		var logicalClose = p.lastTransactionSegmentEnded();
		assertNotNull(logicalClose);
		var segmentDuration = Duration.between(logicalOpen, logicalClose);
		assertEquals(segmentDuration, p.averageTransactionSegmentDuration());
		assertEquals(segmentDuration, p.maxTransactionSegmentDuration());
		assertEquals(1, p.transactionSegmentCount());

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c2 + "; IuPooledConnection .*");
		assertSame(c2, p.getConnection().unwrap(Connection.class));
		logicalOpen = p.logicalConnectionOpened();

		Thread.sleep(50L);
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-close:" + c2 + "; IuPooledConnection .*");
		box.listener.connectionClosed(new ConnectionEvent(pc));

		assertEquals(logicalOpen, p.lastTransactionSegmentStarted());
		logicalClose = p.lastTransactionSegmentEnded();
		assertNotNull(logicalClose);
		segmentDuration = Duration.between(logicalOpen, logicalClose);
		assertTrue(segmentDuration.compareTo(p.averageTransactionSegmentDuration()) > 0);
		assertEquals(segmentDuration, p.maxTransactionSegmentDuration());
		assertEquals(2, p.transactionSegmentCount());

		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.FINER,
				"jdbc-pool-logical-open:" + c3 + "; IuPooledConnection .*");
		assertSame(c3, p.getConnection().unwrap(Connection.class));
		logicalOpen = p.logicalConnectionOpened();

		Thread.sleep(25L);
		final var e = new SQLException();
		IuTestLogger.expect("edu.iu.jdbc.pool.IuPooledConnection", Level.INFO,
				"jdbc-pool-logical-close:" + c3 + "; IuPooledConnection .*", SQLException.class, a -> a == e);
		box.listener.connectionErrorOccurred(new ConnectionEvent(pc, e));
		verify(l).connectionErrorOccurred(argThat(ev -> ev.getSource() == p && ev.getSQLException() == e));

		assertEquals(logicalOpen, p.lastTransactionSegmentStarted());
		logicalClose = p.lastTransactionSegmentEnded();
		assertNotNull(logicalClose);
		segmentDuration = Duration.between(logicalOpen, logicalClose);
		assertTrue(segmentDuration.compareTo(p.averageTransactionSegmentDuration()) < 0);
		assertTrue(segmentDuration.compareTo(p.maxTransactionSegmentDuration()) < 0);
		assertEquals(3, p.transactionSegmentCount());

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

}
