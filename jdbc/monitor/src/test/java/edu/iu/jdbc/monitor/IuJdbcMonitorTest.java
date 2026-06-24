/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.iu.jdbc.monitor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuJdbcMonitorTest {

	private static final String LOG = IuJdbcMonitor.class.getPackageName();

	// -------------------------------------------------------------------------
	// IuJdbcMonitor public API
	// -------------------------------------------------------------------------

	@Test
	public void testMonitorReturnsConnectionProxy() throws SQLException {
		final var conn = mock(Connection.class);
		final var proxy = IuJdbcMonitor.monitor(conn);
		assertInstanceOf(Connection.class, proxy);
	}

	@Test
	public void testMonitorDelegatesToUnderlying() throws SQLException {
		final var conn = mock(Connection.class);
		when(conn.isClosed()).thenReturn(true);
		final var proxy = IuJdbcMonitor.monitor(conn);
		assertTrue(proxy.isClosed());
	}

	// -------------------------------------------------------------------------
	// ConnectionHandler – createStatement
	// -------------------------------------------------------------------------

	@Test
	public void testCreateStatementWrapsProxy() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement();
		assertInstanceOf(Statement.class, s);
	}

	@Test
	public void testCreateStatementWithTypeAndConcurrencyWrapsProxy() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)).thenReturn(stmt);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		assertInstanceOf(Statement.class, s);
	}

	@Test
	public void testCreateStatementWithHoldabilityWrapsProxy() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
				ResultSet.CLOSE_CURSORS_AT_COMMIT)).thenReturn(stmt);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
				ResultSet.CLOSE_CURSORS_AT_COMMIT);
		assertInstanceOf(Statement.class, s);
	}

	// -------------------------------------------------------------------------
	// ConnectionHandler – prepareStatement
	// -------------------------------------------------------------------------

	@Test
	public void testPrepareStatementWrapsProxy() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("SELECT 1")).thenReturn(ps);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("SELECT 1");
		assertInstanceOf(PreparedStatement.class, p);
	}

	@Test
	public void testPrepareStatementWithAutoGeneratedKeysWrapsProxy() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("INSERT INTO t VALUES (?)", Statement.RETURN_GENERATED_KEYS)).thenReturn(ps);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("INSERT INTO t VALUES (?)", Statement.RETURN_GENERATED_KEYS);
		assertInstanceOf(PreparedStatement.class, p);
	}

	// -------------------------------------------------------------------------
	// ConnectionHandler – prepareCall
	// -------------------------------------------------------------------------

	@Test
	public void testPrepareCallWrapsCallableStatementProxy() throws SQLException {
		final var conn = mock(Connection.class);
		final var cs = mock(CallableStatement.class);
		when(conn.prepareCall("{call my_proc(?)}")).thenReturn(cs);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var c = proxy.prepareCall("{call my_proc(?)}");
		assertInstanceOf(CallableStatement.class, c);
	}

	@Test
	public void testPrepareCallWithTypesWrapsProxy() throws SQLException {
		final var conn = mock(Connection.class);
		final var cs = mock(CallableStatement.class);
		when(conn.prepareCall("{call p()}", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)).thenReturn(cs);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var c = proxy.prepareCall("{call p()}", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		assertInstanceOf(CallableStatement.class, c);
	}

	// -------------------------------------------------------------------------
	// ConnectionHandler – exception propagation
	// -------------------------------------------------------------------------

	@Test
	public void testConnectionHandlerPropagatesException() throws SQLException {
		final var conn = mock(Connection.class);
		when(conn.createStatement()).thenThrow(new SQLException("db error"));

		final var proxy = IuJdbcMonitor.monitor(conn);
		assertThrows(SQLException.class, proxy::createStatement);
	}

	// -------------------------------------------------------------------------
	// StatementHandler – executeQuery
	// -------------------------------------------------------------------------

	@Test
	public void testStatementExecuteQueryWrapsResultSet() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		final var rs = mock(ResultSet.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeQuery("SELECT 1")).thenReturn(rs);
		when(rs.next()).thenReturn(false);

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: complete; sql=SELECT 1; execute=PT.*; rows=0; scan=PT.*");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement();
		final var result = s.executeQuery("SELECT 1");
		assertFalse(result.next()); // exhaust → logs complete
	}

	// -------------------------------------------------------------------------
	// StatementHandler – executeUpdate
	// -------------------------------------------------------------------------

	@Test
	public void testStatementExecuteUpdate() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeUpdate("UPDATE t SET a=1")).thenReturn(3);

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: execute; sql=UPDATE t SET a=1; execute=PT.*; affected=3");

		final var proxy = IuJdbcMonitor.monitor(conn);
		proxy.createStatement().executeUpdate("UPDATE t SET a=1");
	}

	@Test
	public void testStatementExecuteLargeUpdate() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeLargeUpdate("DELETE FROM t")).thenReturn(100L);

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: execute; sql=DELETE FROM t; execute=PT.*; affected=100");

		final var proxy = IuJdbcMonitor.monitor(conn);
		proxy.createStatement().executeLargeUpdate("DELETE FROM t");
	}

	// -------------------------------------------------------------------------
	// StatementHandler – execute (boolean)
	// -------------------------------------------------------------------------

	@Test
	public void testStatementExecute() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.execute("CALL proc()")).thenReturn(false);

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: execute; sql=CALL proc\\(\\); execute=PT.*");

		final var proxy = IuJdbcMonitor.monitor(conn);
		proxy.createStatement().execute("CALL proc()");
	}

	// -------------------------------------------------------------------------
	// StatementHandler – executeBatch
	// -------------------------------------------------------------------------

	@Test
	public void testStatementExecuteBatch() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeBatch()).thenReturn(new int[] { 1, 2, Statement.EXECUTE_FAILED, Statement.SUCCESS_NO_INFO });

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: batch; sql=\\[INSERT INTO t VALUES \\(1\\), INSERT INTO t VALUES \\(2\\), BAD INSERT \\(3\\)\\]; execute=PT.*; affected=3");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement();
		s.addBatch("INSERT INTO t VALUES (1)");
		s.addBatch("INSERT INTO t VALUES (2)");
		s.addBatch("BAD INSERT (3)");
		s.executeBatch();
	}

	@Test
	public void testStatementExecuteLargeBatch() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeLargeBatch()).thenReturn(new long[] { 5L, Statement.SUCCESS_NO_INFO });

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: batch; sql=\\[DELETE FROM t\\]; execute=PT.*; affected=5");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement();
		s.addBatch("DELETE FROM t");
		s.executeLargeBatch();
	}

	@Test
	public void testStatementClearBatchClearsSqls() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeBatch()).thenReturn(new int[] {});

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: batch; sql=\\[\\]; execute=PT.*; affected=0");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement();
		s.addBatch("INSERT INTO t VALUES (99)");
		s.clearBatch(); // clears tracked SQL, so executeBatch logs empty list
		s.executeBatch();
		verify(stmt).clearBatch(); // underlying also called
	}

	// -------------------------------------------------------------------------
	// StatementHandler – exception propagation
	// -------------------------------------------------------------------------

	@Test
	public void testStatementHandlerPropagatesException() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeQuery("BAD SQL")).thenThrow(new SQLException("syntax error"));

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement();
		assertThrows(SQLException.class, () -> s.executeQuery("BAD SQL"));
	}

	// -------------------------------------------------------------------------
	// StatementHandler – non-intercepted method pass-through
	// -------------------------------------------------------------------------

	@Test
	public void testStatementPassThrough() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.getMaxRows()).thenReturn(42);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var s = proxy.createStatement();
		// getMaxRows is not intercepted – should delegate directly
		assertDoesNotThrow(() -> s.getMaxRows());
		verify(stmt).getMaxRows();
	}

	// -------------------------------------------------------------------------
	// ResultSetHandler – row counting and logging
	// -------------------------------------------------------------------------

	@Test
	public void testResultSetFewRowsNoScanLog() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		final var rs = mock(ResultSet.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeQuery("SELECT 1")).thenReturn(rs);
		when(rs.next()).thenReturn(true, true, false);

		// No scan log because < 1000 rows. Only complete log.
		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: complete; sql=SELECT 1; execute=PT.*; rows=2; scan=PT.*");

		final var s = IuJdbcMonitor.monitor(conn).createStatement();
		final var result = s.executeQuery("SELECT 1");
		assertTrue(result.next());
		assertTrue(result.next());
		assertFalse(result.next());
	}

	@Test
	public void testResultSetScanLogEvery1000Rows() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		final var rs = mock(ResultSet.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeQuery("SELECT *")).thenReturn(rs);

		// 1000 true answers then false
		final var trueAnswers = new Boolean[999];
		java.util.Arrays.fill(trueAnswers, Boolean.TRUE);
		when(rs.next()).thenReturn(Boolean.TRUE, trueAnswers).thenReturn(Boolean.FALSE);

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: scan; sql=SELECT \\*; rows=1000; elapsed=PT.*");
		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: complete; sql=SELECT \\*; execute=PT.*; rows=1000; scan=PT.*");

		final var result = IuJdbcMonitor.monitor(conn).createStatement().executeQuery("SELECT *");
		for (int i = 0; i < 1000; i++)
			assertTrue(result.next());
		assertFalse(result.next());
	}

	@Test
	public void testResultSetScanLogTwoMilestones() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		final var rs = mock(ResultSet.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeQuery("SELECT *")).thenReturn(rs);

		// 2000 true answers then false
		final var trueAnswers = new Boolean[1999];
		java.util.Arrays.fill(trueAnswers, Boolean.TRUE);
		when(rs.next()).thenReturn(Boolean.TRUE, trueAnswers).thenReturn(Boolean.FALSE);

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: scan; sql=SELECT \\*; rows=1000; elapsed=PT.*");
		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: scan; sql=SELECT \\*; rows=2000; elapsed=PT.*");
		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: complete; sql=SELECT \\*; execute=PT.*; rows=2000; scan=PT.*");

		final var result = IuJdbcMonitor.monitor(conn).createStatement().executeQuery("SELECT *");
		for (int i = 0; i < 2000; i++)
			assertTrue(result.next());
		assertFalse(result.next());
	}

	// -------------------------------------------------------------------------
	// ResultSetHandler – non-next pass-through
	// -------------------------------------------------------------------------

	@Test
	public void testResultSetPassThrough() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		final var rs = mock(ResultSet.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeQuery("SELECT 1")).thenReturn(rs);
		when(rs.getString(1)).thenReturn("hello");

		final var result = IuJdbcMonitor.monitor(conn).createStatement().executeQuery("SELECT 1");
		// getString(1) is not intercepted – passes through to delegate
		assertDoesNotThrow(() -> result.getString(1));
		verify(rs).getString(1);

		// Exhaust the result set to satisfy the complete log expectation
		when(rs.next()).thenReturn(false);
		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: complete; sql=SELECT 1; execute=PT.*; rows=0; scan=PT.*");
		result.next();
	}

	// -------------------------------------------------------------------------
	// ResultSetHandler – exception propagation on next()
	// -------------------------------------------------------------------------

	@Test
	public void testResultSetNextPropagatesException() throws SQLException {
		final var conn = mock(Connection.class);
		final var stmt = mock(Statement.class);
		final var rs = mock(ResultSet.class);
		when(conn.createStatement()).thenReturn(stmt);
		when(stmt.executeQuery("SELECT 1")).thenReturn(rs);
		when(rs.next()).thenThrow(new SQLException("stream closed"));

		final var result = IuJdbcMonitor.monitor(conn).createStatement().executeQuery("SELECT 1");
		assertThrows(SQLException.class, result::next);
	}

	// -------------------------------------------------------------------------
	// PreparedStatementHandler – parameter tracking
	// -------------------------------------------------------------------------

	@Test
	public void testPreparedStatementParameterCapture() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("SELECT * FROM t WHERE id = ?")).thenReturn(ps);
		when(ps.executeUpdate()).thenReturn(0);

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: execute; sql=SELECT \\* FROM t WHERE id = \\?; execute=PT.*; affected=0");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("SELECT * FROM t WHERE id = ?");
		p.setInt(1, 42);
		p.executeUpdate();
	}

	@Test
	public void testPreparedStatementSetNull() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("INSERT INTO t(a) VALUES(?)")).thenReturn(ps);
		when(ps.executeUpdate()).thenReturn(1);

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: execute; sql=INSERT INTO t\\(a\\) VALUES\\(\\?\\); execute=PT.*; affected=1");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("INSERT INTO t(a) VALUES(?)");
		p.setNull(1, java.sql.Types.VARCHAR);
		p.executeUpdate();
	}

	@Test
	public void testPreparedStatementMultipleParams() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("SELECT * FROM t WHERE a=? AND b=?")).thenReturn(ps);
		when(ps.executeUpdate()).thenReturn(5);

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: execute; sql=SELECT \\* FROM t WHERE a=\\? AND b=\\?; execute=PT.*; affected=5");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("SELECT * FROM t WHERE a=? AND b=?");
		p.setString(1, "hello");
		p.setInt(2, 99);
		p.executeUpdate();
	}

	@Test
	public void testPreparedStatementClearParameters() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("SELECT ?")).thenReturn(ps);
		when(ps.executeUpdate()).thenReturn(0);

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: execute; sql=SELECT \\?; execute=PT.*; affected=0");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("SELECT ?");
		p.setString(1, "old-value");
		p.clearParameters(); // clears tracked params
		p.executeUpdate();
		verify(ps).clearParameters(); // underlying also called
	}

	// -------------------------------------------------------------------------
	// PreparedStatementHandler – executeQuery
	// -------------------------------------------------------------------------

	@Test
	public void testPreparedStatementExecuteQueryWrapsResultSet() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		final var rs = mock(ResultSet.class);
		when(conn.prepareStatement("SELECT name FROM t WHERE id=?")).thenReturn(ps);
		when(ps.executeQuery()).thenReturn(rs);
		when(rs.next()).thenReturn(false);

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: complete; sql=SELECT name FROM t WHERE id=\\?; execute=PT.*; rows=0; scan=PT.*");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("SELECT name FROM t WHERE id=?");
		p.setInt(1, 7);
		final var result = ((PreparedStatement) p).executeQuery();
		result.next();
	}

	// -------------------------------------------------------------------------
	// PreparedStatementHandler – execute (boolean)
	// -------------------------------------------------------------------------

	@Test
	public void testPreparedStatementExecuteBoolean() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("CALL proc(?)")).thenReturn(ps);
		when(ps.execute()).thenReturn(false);

		IuTestLogger.expect(LOG, Level.INFO, "jdbc-monitor: execute; sql=CALL proc\\(\\?\\); execute=PT.*");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("CALL proc(?)");
		p.setString(1, "x");
		((PreparedStatement) p).execute();
	}

	// -------------------------------------------------------------------------
	// PreparedStatementHandler – executeLargeUpdate
	// -------------------------------------------------------------------------

	@Test
	public void testPreparedStatementExecuteLargeUpdate() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("DELETE FROM t WHERE id=?")).thenReturn(ps);
		when(ps.executeLargeUpdate()).thenReturn(1L);

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: execute; sql=DELETE FROM t WHERE id=\\?; execute=PT.*; affected=1");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("DELETE FROM t WHERE id=?");
		p.setInt(1, 5);
		((PreparedStatement) p).executeLargeUpdate();
	}

	// -------------------------------------------------------------------------
	// PreparedStatementHandler – executeBatch
	// -------------------------------------------------------------------------

	@Test
	public void testPreparedStatementExecuteBatch() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("INSERT INTO t VALUES(?)")).thenReturn(ps);
		when(ps.executeBatch()).thenReturn(new int[] { 1, 2, Statement.EXECUTE_FAILED });

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: batch; sql=INSERT INTO t VALUES\\(\\?\\); execute=PT.*; affected=3");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = (PreparedStatement) proxy.prepareStatement("INSERT INTO t VALUES(?)");
		p.executeBatch();
	}

	@Test
	public void testPreparedStatementExecuteLargeBatch() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("INSERT INTO t VALUES(?)")).thenReturn(ps);
		when(ps.executeLargeBatch()).thenReturn(new long[] { 2L, Statement.SUCCESS_NO_INFO });

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: batch; sql=INSERT INTO t VALUES\\(\\?\\); execute=PT.*; affected=2");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = (PreparedStatement) proxy.prepareStatement("INSERT INTO t VALUES(?)");
		p.executeLargeBatch();
	}

	// -------------------------------------------------------------------------
	// PreparedStatementHandler – non-intercepted method pass-through
	// -------------------------------------------------------------------------

	@Test
	public void testPreparedStatementPassThrough() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("SELECT 1")).thenReturn(ps);

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("SELECT 1");
		// setFetchSize(int) is not a parameter setter (only 1 arg) – passes through
		assertDoesNotThrow(() -> p.setFetchSize(100));
		verify(ps).setFetchSize(100);
	}

	// -------------------------------------------------------------------------
	// PreparedStatementHandler – exception propagation
	// -------------------------------------------------------------------------

	@Test
	public void testPreparedStatementHandlerPropagatesException() throws SQLException {
		final var conn = mock(Connection.class);
		final var ps = mock(PreparedStatement.class);
		when(conn.prepareStatement("BAD")).thenReturn(ps);
		when(ps.executeUpdate()).thenThrow(new SQLException("constraint violation"));

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var p = proxy.prepareStatement("BAD");
		assertThrows(SQLException.class, () -> ((PreparedStatement) p).executeUpdate());
	}

	// -------------------------------------------------------------------------
	// CallableStatement via prepareCall
	// -------------------------------------------------------------------------

	@Test
	public void testCallableStatementExecuteUpdate() throws SQLException {
		final var conn = mock(Connection.class);
		final var cs = mock(CallableStatement.class);
		when(conn.prepareCall("{call p(?)}")).thenReturn(cs);
		when(cs.executeUpdate()).thenReturn(0);

		IuTestLogger.expect(LOG, Level.INFO,
				"jdbc-monitor: execute; sql=\\{call p\\(\\?\\)\\}; execute=PT.*; affected=0");

		final var proxy = IuJdbcMonitor.monitor(conn);
		final var c = (CallableStatement) proxy.prepareCall("{call p(?)}");
		c.setString(1, "val");
		c.executeUpdate();
	}

}
