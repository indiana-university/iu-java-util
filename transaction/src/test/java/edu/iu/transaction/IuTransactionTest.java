package edu.iu.transaction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.UnsafeFunction;
import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTest;
import edu.iu.test.IuTestLogger;
import jakarta.transaction.HeuristicCommitException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

/**
 * 
 */
@SuppressWarnings("javadoc")
public class IuTransactionTest {

	private Handler consoleHandler;

	@BeforeEach
	public void setup() {
		assertDoesNotThrow(() -> Class.forName(IuTransaction.class.getName()));
		if ("true".equals(IuTest.getProperty("debug"))) {
			final var log = LogManager.getLogManager().getLogger(IuTransaction.class.getName());
			consoleHandler = new ConsoleHandler();
			consoleHandler.setLevel(Level.ALL);
			log.addHandler(consoleHandler);
		}
	}

	@AfterEach
	public void teardown() {
		if (consoleHandler != null)
			LogManager.getLogManager().getLogger(IuTransaction.class.getName()).removeHandler(consoleHandler);
	}

	private IuTransaction tx() {
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE,
				"iuxid-63224\\+[\\w\\-]{32}\\+[\\w\\-]{32} begin");
		return new IuTransaction(Duration.ofSeconds(5L));
	}

	@Test
	public void testDescribeStatus() {
		assertEquals("STATUS_NO_TRANSACTION", IuTransaction.describeStatus(Status.STATUS_NO_TRANSACTION));
		assertEquals("STATUS_X34", IuTransaction.describeStatus(34));
	}

	@Test
	public void testDescribeXAErrorCode() {
		assertEquals("XA_RBBASE", IuTransaction.describeXAErrorCode(XAException.XA_RBBASE));
		assertEquals("XA_RBCOMMFAIL", IuTransaction.describeXAErrorCode(XAException.XA_RBCOMMFAIL));
		assertEquals("XA_RBDEADLOCK", IuTransaction.describeXAErrorCode(XAException.XA_RBDEADLOCK));
		assertEquals("XA_RBINTEGRITY", IuTransaction.describeXAErrorCode(XAException.XA_RBINTEGRITY));
		assertEquals("XA_RBOTHER", IuTransaction.describeXAErrorCode(XAException.XA_RBOTHER));
		assertEquals("XA_RBPROTO", IuTransaction.describeXAErrorCode(XAException.XA_RBPROTO));
		assertEquals("XA_RBTIMEOUT", IuTransaction.describeXAErrorCode(XAException.XA_RBTIMEOUT));
		assertEquals("XA_RBTRANSIENT", IuTransaction.describeXAErrorCode(XAException.XA_RBTRANSIENT));
		assertEquals("XA_NOMIGRATE", IuTransaction.describeXAErrorCode(XAException.XA_NOMIGRATE));
		assertEquals("XA_HEURHAZ", IuTransaction.describeXAErrorCode(XAException.XA_HEURHAZ));
		assertEquals("XA_HEURCOM", IuTransaction.describeXAErrorCode(XAException.XA_HEURCOM));
		assertEquals("XA_HEURRB", IuTransaction.describeXAErrorCode(XAException.XA_HEURRB));
		assertEquals("XA_HEURMIX", IuTransaction.describeXAErrorCode(XAException.XA_HEURMIX));
		assertEquals("XA_RETRY", IuTransaction.describeXAErrorCode(XAException.XA_RETRY));
		assertEquals("XA_RDONLY", IuTransaction.describeXAErrorCode(XAException.XA_RDONLY));
		assertEquals("XAER_ASYNC", IuTransaction.describeXAErrorCode(XAException.XAER_ASYNC));
		assertEquals("XAER_RMERR", IuTransaction.describeXAErrorCode(XAException.XAER_RMERR));
		assertEquals("XAER_NOTA", IuTransaction.describeXAErrorCode(XAException.XAER_NOTA));
		assertEquals("XAER_INVAL", IuTransaction.describeXAErrorCode(XAException.XAER_INVAL));
		assertEquals("XAER_PROTO", IuTransaction.describeXAErrorCode(XAException.XAER_PROTO));
		assertEquals("XAER_RMFAIL", IuTransaction.describeXAErrorCode(XAException.XAER_RMFAIL));
		assertEquals("XAER_DUPID", IuTransaction.describeXAErrorCode(XAException.XAER_DUPID));
		assertEquals("XAER_OUTSIDE", IuTransaction.describeXAErrorCode(XAException.XAER_OUTSIDE));
		assertEquals("XA_34", IuTransaction.describeXAErrorCode(34));
	}

	@Test
	public void testPositiveDuration() {
		assertThrows(IllegalArgumentException.class, () -> new IuTransaction(Duration.ZERO));
	}

	@Test
	public void testBranch() {
		final var t = tx();
		assertSame(Status.STATUS_ACTIVE, t.getTransactionStatus());
		final var escapedXid = t.getTransactionKey().toString().replace("+", "\\+");
		final var gtid = IdGenerator.encodeId(t.getTransactionKey().getGlobalTransactionId());
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE,
				"iuxid-63224\\+" + gtid + "\\+[\\w\\-]{32} branch " + escapedXid);
		final var bt = new IuTransaction(t);
		assertEquals(bt.getTransactionKey().getGlobalTransactionId(), t.getTransactionKey().getGlobalTransactionId());
		assertNotEquals(bt.getTransactionKey(), t.getTransactionKey());
	}

	@Test
	public void testGetPutResource() throws Exception {
		final var t = tx();
		final var escapedXid = t.getTransactionKey().toString().replace("+", "\\+");
		final var k = new Object();
		final var r = new Object();
		assertNull(t.getResource(k));
		t.putResource(k, r);
		assertSame(r, t.getResource(k));
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_UNKNOWN");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, escapedXid + " suspend");
		t.suspend();
		assertThrows(IllegalStateException.class, () -> t.getResource(k));
		assertThrows(IllegalStateException.class, () -> t.putResource(k, r));
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_ACTIVE");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, escapedXid + " resume");
		t.resume();
		assertSame(r, t.getResource(k));
		t.putResource(k, null);
		assertNull(t.getResource(k));
	}

	@Test
	public void testBeginAndCommit() throws Exception {
		final var t = tx();
		assertSame(Status.STATUS_ACTIVE, t.getStatus());
		final var escapedXid = t.getTransactionKey().toString().replace("+", "\\+");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":COMMIT begin");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":COMMIT before-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":COMMIT before-interposed-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":COMMIT delist-resources");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":COMMIT delist-suspended-resources");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":COMMIT commit-phase-1");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_PREPARING");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_PREPARED");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":COMMIT commit-phase-2");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_COMMITTING");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_COMMITTED");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":COMMIT after-interposed-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":COMMIT after-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":COMMIT complete");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, escapedXid + " commit");
		t.commit();
		assertSame(Status.STATUS_COMMITTED, t.getStatus());
		assertThrows(IllegalStateException.class, t::commit);
	}

	@Test
	public void testBeginAndRollback() throws RollbackException {
		final var t = tx();
		assertSame(Status.STATUS_ACTIVE, t.getStatus());
		final var escapedXid = t.getTransactionKey().toString().replace("+", "\\+");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_MARKED_ROLLBACK");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":ROLLBACK before-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK before-interposed-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK delist-suspended-resources");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK delist-resources");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK rollback-phase-1");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK rollback-phase-2");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_ROLLING_BACK");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_ROLLEDBACK");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK after-interposed-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":ROLLBACK after-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, escapedXid + " rollback");
		t.rollback();
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
		assertThrows(RollbackException.class, () -> t.enlistResource(null));
	}

	@Test
	public void testRollbackOnly() throws RollbackException {
		final var t = tx();
		final var escapedXid = t.getTransactionKey().toString().replace("+", "\\+");
		assertSame(Status.STATUS_ACTIVE, t.getStatus());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_MARKED_ROLLBACK");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, escapedXid + " rollback-only");
		t.setRollbackOnly();
		t.setRollbackOnly(); // suppresses an extra call, otherwise no effect
		assertTrue(t.getRollbackOnly());
		assertThrows(RollbackException.class, () -> t.enlistResource(null));

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":ROLLBACK before-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK before-interposed-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK delist-suspended-resources");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK delist-resources");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK rollback-phase-1");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK rollback-phase-2");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_ROLLING_BACK");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINER, escapedXid + ":STATUS_ROLLEDBACK");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST,
				escapedXid + ":ROLLBACK after-interposed-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINEST, escapedXid + ":ROLLBACK after-synch");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, escapedXid + " rollback");
		final var rollback = assertThrows(RollbackException.class, t::commit);
		assertEquals(0, rollback.getSuppressed().length, () -> {
			rollback.printStackTrace();
			return rollback.getSuppressed()[0].toString();
		});
		assertInstanceOf(RollbackException.class, rollback.getCause());
		assertInstanceOf(RollbackException.class, rollback.getCause().getCause());
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
	}

	@Test
	public void testSynchronization() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var sync = mock(Synchronization.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.registerSynchronization(sync);
		t.commit();
		verify(sync).beforeCompletion();
		verify(sync).afterCompletion(Status.STATUS_COMMITTED);
	}

	@Test
	public void testSynchronizationRollback() throws RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var sync = mock(Synchronization.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.registerSynchronization(sync);
		t.rollback();
		verify(sync, never()).beforeCompletion();
		verify(sync).afterCompletion(Status.STATUS_ROLLEDBACK);
	}

	@Test
	public void testSynchronizationErrorOnRollback() throws RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var sync = mock(Synchronization.class);
		final var r = new RuntimeException();
		doThrow(r).when(sync).afterCompletion(Status.STATUS_ROLLEDBACK);

		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.registerSynchronization(sync);
		assertSame(r, assertThrows(RuntimeException.class, t::rollback));
	}

	@Test
	public void testInterposedSynchronization() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var sync = mock(Synchronization.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.registerInterposedSynchronization(sync);
		t.commit();
		verify(sync).beforeCompletion();
		verify(sync).afterCompletion(Status.STATUS_COMMITTED);
	}

	@Test
	public void testInterposedSynchronizationRollback() throws RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var sync = mock(Synchronization.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.registerInterposedSynchronization(sync);
		t.rollback();
		verify(sync, never()).beforeCompletion();
		verify(sync).afterCompletion(Status.STATUS_ROLLEDBACK);
	}

	@Test
	public void testXAResourceSetTimeoutError() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var x = new XAException();
		when(resource.setTransactionTimeout(intThat(i -> i <= 5))).thenThrow(x);
		final var escapedXid = t.getTransactionKey().toString().replace("+", "\\+");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.INFO,
				escapedXid + " setTransactionTimeout\\(\\) failure", XAException.class);
		t.enlistResource(resource);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
	}

	@Test
	public void testXAResourceSetTimeoutAndStartError() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		when(resource.isSameRM(resource)).thenReturn(true);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var xt = new XAException();
		when(resource.setTransactionTimeout(intThat(i -> i <= 5))).thenThrow(xt);
		final var x = new XAException();
		doThrow(x).when(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		final var e = assertThrows(IllegalStateException.class, () -> t.enlistResource(resource));
		assertSame(x, e.getCause());
		assertSame(xt, x.getSuppressed()[0]);
	}

	@Test
	public void testXAResourceCommit() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		when(resource.isSameRM(resource)).thenReturn(true);
		final var resource2 = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.enlistResource(resource);
		t.enlistResource(resource2); // enforce no effect
		t.enlistResource(resource); // enforce no effect
		verify(resource).setTransactionTimeout(intThat(i -> i <= 5));
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource2).setTransactionTimeout(intThat(i -> i <= 5));
		verify(resource2).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		t.commit();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).prepare(t.getTransactionKey());
		verify(resource).commit(t.getTransactionKey(), false);
		verify(resource2).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource2).prepare(t.getTransactionKey());
		verify(resource2).commit(t.getTransactionKey(), false);
	}

	@Test
	public void testXAResourceDelist() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		when(resource.isSameRM(resource)).thenReturn(true);
		final var resource2 = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.enlistResource(resource);
		t.enlistResource(resource2);
		verify(resource).setTransactionTimeout(intThat(i -> i <= 5));
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		t.delistResource(resource, XAResource.TMSUCCESS);
		t.delistResource(resource, XAResource.TMSUCCESS); // enforce no effect
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource2, never()).end(any(), any(int.class));
	}

	@Test
	public void testXAResourceDelistSuspendedAndResumeWithJoinedResource() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		when(resource.isSameRM(resource)).thenReturn(true);
		final var resource2 = mock(XAResource.class);
		when(resource2.isSameRM(resource2)).thenReturn(true);
		final var resource3 = mock(XAResource.class);
		when(resource.isSameRM(resource3)).thenReturn(true);
		when(resource3.isSameRM(resource)).thenReturn(true);
		when(resource3.isSameRM(resource3)).thenReturn(true);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var t2 = new IuTransaction(t);
		t.enlistResource(resource);
		t2.enlistResource(resource2);
		t2.enlistResource(resource3);
		t.suspend();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUSPEND);
		t.join(t2);
		verify(resource2).end(t2.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource3).end(t2.getTransactionKey(), XAResource.TMSUCCESS);
		t.delistResource(resource, XAResource.TMSUCCESS);
		t.delistResource(resource, XAResource.TMSUCCESS); // enforce no effect
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		t.resume();
		verify(resource2).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource2).start(t2.getTransactionKey(), XAResource.TMJOIN);
		verify(resource3).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource3).start(t2.getTransactionKey(), XAResource.TMJOIN);
	}

	@Test
	public void testXAResourceDelistError() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		when(resource.isSameRM(resource)).thenReturn(true);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var x = new XAException();
		doThrow(x).when(resource).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		t.enlistResource(resource);
		verify(resource).setTransactionTimeout(ArgumentMatchers.intThat(i -> i <= 5));
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);

		assertSame(x, assertThrows(IllegalStateException.class, () -> t.delistResource(resource, XAResource.TMSUCCESS))
				.getCause());
		t.delistResource(resource, XAResource.TMSUCCESS); // enforce no effect
	}

	@Test
	public void testXAResourceThrowsXAException() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var resource = mock(XAResource.class);
		final var x = new XAException();
		doThrow(x).when(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		assertSame(x, assertThrows(IllegalStateException.class, () -> t.enlistResource(resource)).getCause());
	}

	@Test
	public void testXAResourceSuspendAndResume() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.enlistResource(resource);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		t.suspend();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUSPEND);
		t.resume();
		verify(resource).start(t.getTransactionKey(), XAResource.TMRESUME);
	}

	@Test
	public void testXAResourceSuspendError() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.enlistResource(resource);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		final var x = new XAException();
		doThrow(x).when(resource).end(t.getTransactionKey(), XAResource.TMSUSPEND);
		assertSame(x, assertThrows(IllegalStateException.class, t::suspend).getCause());
	}

	@Test
	public void testXAResourceSuspendThenResumeError() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.enlistResource(resource);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		t.suspend();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUSPEND);
		final var x = new XAException();
		doThrow(x).when(resource).start(t.getTransactionKey(), XAResource.TMRESUME);
		assertSame(x, assertThrows(IllegalStateException.class, t::resume).getCause());
	}

	@Test
	public void testXAResourceReadOnly() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.enlistResource(resource);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		when(resource.prepare(t.getTransactionKey())).thenReturn(XAResource.XA_RDONLY);
		t.commit();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).prepare(t.getTransactionKey());
		verify(resource, never()).commit(eq(t.getTransactionKey()), any(boolean.class));
	}

	@Test
	public void testXAResourceContinueRollback() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var s = mock(Synchronization.class);
		final var r = new RuntimeException();
		doThrow(r).when(s).beforeCompletion();
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.registerSynchronization(s);
		t.enlistResource(resource);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		assertSame(r, assertThrows(RollbackException.class, t::commit).getCause());
		verify(resource).end(t.getTransactionKey(), XAResource.TMFAIL);
		verify(resource).rollback(t.getTransactionKey());
		verify(resource, never()).commit(eq(t.getTransactionKey()), any(boolean.class));
	}

	@Test
	public void testExpired() throws InterruptedException, TimeoutException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(1L));
		IuObject.waitFor(t, () -> t.getStatus() == Status.STATUS_ROLLEDBACK, Duration.ofSeconds(2L));
		assertThrows(IllegalStateException.class, () -> t.enlistResource(null));
		assertThrows(IllegalStateException.class, () -> t.commit());
	}

	@Test
	public void testResumeFromTimedRollback() throws InterruptedException, TimeoutException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(1L));
		t.suspend();
		IuObject.waitFor(t, () -> t.getStatus() == Status.STATUS_ROLLEDBACK, Duration.ofSeconds(2L));
		assertThrows(IllegalStateException.class, () -> t.enlistResource(null));
		assertThrows(IllegalStateException.class, () -> t.commit());
	}

	@Test
	public void testRollbackFailureFromTimedRollback() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(2L));
		final var resource = mock(XAResource.class);
		final var x = new XAException();
		doThrow(x).when(resource).end(t.getTransactionKey(), XAResource.TMFAIL);
		t.enlistResource(resource);
		t.suspend();

		final var escapedXid = t.getTransactionKey().toString().replace("+", "\\+");
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.WARNING, escapedXid + " timed rollback failure",
				IllegalStateException.class, e -> e.getCause() == x);
		IuObject.waitFor(t, () -> t.getStatus() == Status.STATUS_ROLLEDBACK, Duration.ofSeconds(3L));
	}

	@Test
	public void testTimedRollbackRace() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(1L));
		synchronized (t) {
			t.registerSynchronization(new Synchronization() {
				@Override
				public void beforeCompletion() {
					IuException.unchecked(() -> Thread.sleep(1500L));
				}

				@Override
				public void afterCompletion(int status) {
				}
			});
			t.commit();
		}
		assertSame(Status.STATUS_COMMITTED, t.getStatus());
	}

	@Test
	public void testExpiredParent() throws InterruptedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(1L));
		Thread.sleep(1000L);
		assertThrows(IllegalArgumentException.class, () -> new IuTransaction(t));
	}

	@Test
	public void testSuspendedCommit() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.enlistResource(resource);
		verify(resource).setTransactionTimeout(intThat(i -> i <= 5));
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		t.suspend();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUSPEND);
		t.commit();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).prepare(t.getTransactionKey());
		verify(resource).commit(t.getTransactionKey(), false);
	}

	@Test
	public void testSuspendedRollback() throws RollbackException, XAException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		t.enlistResource(resource);
		verify(resource).setTransactionTimeout(intThat(i -> i <= 5));
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		t.suspend();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUSPEND);
		t.rollback();
		verify(resource).end(t.getTransactionKey(), XAResource.TMFAIL);
		verify(resource).rollback(t.getTransactionKey());
	}

	@Test
	public void testBranchAndSuspend() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var t2 = new IuTransaction(t);
		t2.suspend();
		t.commit();
		assertSame(Status.STATUS_COMMITTED, t2.getStatus());
		assertSame(Status.STATUS_COMMITTED, t.getStatus());
	}

	@Test
	public void testBranchAndCommit() throws Throwable {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var t2 = new IuTransaction(t);

		class Box {
			boolean done;
			Throwable error;
		}
		final var box = new Box();
		new Thread(() -> {
			try {
				t2.commit();
				box.done = true;
			} catch (Throwable e) {
				box.error = e;
			} finally {
				synchronized (box) {
					box.notifyAll();
				}
			}
		}).start();

		t.commit();

		IuObject.waitFor(box, () -> box.done || box.error != null, Duration.ofSeconds(1L));
		if (box.error != null)
			throw box.error;
		assertTrue(box.done);

		assertSame(Status.STATUS_COMMITTED, t2.getStatus());
		assertSame(Status.STATUS_COMMITTED, t.getStatus());
	}

	@Test
	public void testHeuristicRollback() throws Throwable {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var t2 = new IuTransaction(t);

		class Box {
			boolean done;
			Throwable error;
		}
		final var box = new Box();
		new Thread(() -> {
			try {
				t2.rollback();
				box.done = true;
			} catch (Throwable e) {
				box.error = e;
			} finally {
				synchronized (t2) {
					t2.notifyAll();
				}
			}
		}).start();

		assertThrows(HeuristicRollbackException.class, t::commit);

		IuObject.waitFor(box, () -> box.done || box.error != null, Duration.ofSeconds(1L));
		if (box.error != null)
			throw box.error;
		assertTrue(box.done);

		assertSame(Status.STATUS_ROLLEDBACK, t2.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
	}

	@Test
	public void testHeuristicMix() throws Throwable {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var t2 = new IuTransaction(t);
		final var t3 = new IuTransaction(t2);
		final var t4 = new IuTransaction(t2);

		class Box {
			boolean done;
			Throwable error;
		}
		final UnsafeFunction<UnsafeRunnable, Box> f = r -> {
			final var box = new Box();
			new Thread(() -> {
				try {
					r.run();
					box.done = true;
				} catch (Throwable e) {
					box.error = e;
				} finally {
					synchronized (box) {
						box.notifyAll();
					}
				}
			}).start();
			return box;
		};
		final var b3 = f.apply(() -> t3.commit());
		final var b4 = f.apply(() -> t4.rollback());
		final var b2 = f.apply(() -> assertThrows(HeuristicMixedException.class, t2::commit));

		assertThrows(HeuristicMixedException.class, t::commit);

		IuObject.waitFor(b2, () -> b2.done || b2.error != null, Duration.ofSeconds(1L));
		IuObject.waitFor(b3, () -> b3.done || b3.error != null, Duration.ofSeconds(1L));
		IuObject.waitFor(b4, () -> b4.done || b4.error != null, Duration.ofSeconds(1L));
		Throwable error = null;
		if (b2.error != null)
			error = b2.error;
		if (b3.error != null)
			if (error == null)
				error = b3.error;
			else
				error.addSuppressed(b3.error);
		if (b4.error != null)
			if (error == null)
				error = b4.error;
			else
				error.addSuppressed(b4.error);
		if (error != null)
			throw error;

		assertSame(Status.STATUS_ROLLEDBACK, t4.getStatus());
		assertSame(Status.STATUS_COMMITTED, t3.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t2.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
	}

	@Disabled
	@Test
	public void testHeuristicTimeout() throws Throwable {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofMillis(1250L));
		final var t2 = new IuTransaction(t);
		final var t3 = new IuTransaction(t2);
		final var t4 = new IuTransaction(t2);

		class Box {
			boolean done;
			Throwable error;
		}
		final UnsafeFunction<UnsafeRunnable, Box> f = r -> {
			final var box = new Box();
			new Thread(() -> {
				try {
					r.run();
					box.done = true;
				} catch (Throwable e) {
					box.error = e;
				} finally {
					synchronized (box) {
						box.notifyAll();
					}
				}
			}).start();
			return box;
		};
		final var b3 = f.apply(() -> t3.commit());
		final var b4 = f.apply(() -> t4.rollback());
		final var b2 = f.apply(() -> {
			Thread.sleep(2000L);
			assertInstanceOf(TimeoutException.class, assertThrows(IllegalStateException.class, t2::commit).getCause());
		});

		assertInstanceOf(TimeoutException.class, assertThrows(IllegalStateException.class, t::commit).getCause());

		IuObject.waitFor(b2, () -> b2.done || b2.error != null, Duration.ofSeconds(1L));

		Throwable error = null;
		if (b2.error != null)
			error = b2.error;
		if (b3.error != null)
			if (error == null)
				error = b3.error;
			else
				error.addSuppressed(b3.error);
		if (b4.error != null)
			if (error == null)
				error = b4.error;
			else
				error.addSuppressed(b4.error);
		if (error != null)
			throw error;

		assertTrue(b2.done);

		assertSame(Status.STATUS_ROLLEDBACK, t2.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
	}

	@Test
	public void testHeuristicMixedOnRollback() throws Throwable {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofMillis(1250L));
		final var t2 = new IuTransaction(t);
		final var t3 = new IuTransaction(t2);
		final var t4 = new IuTransaction(t2);
		t3.commit();
		final var e = assertThrows(IllegalStateException.class, t::rollback);
		assertInstanceOf(HeuristicMixedException.class, e.getCause());
		assertSame(Status.STATUS_ROLLEDBACK, t4.getStatus());
		assertSame(Status.STATUS_COMMITTED, t3.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t2.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
	}

	@Test
	public void testHeuristicCommitOnRollback() throws Throwable {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofMillis(1250L));
		final var t2 = new IuTransaction(t);
		final var t3 = new IuTransaction(t2);
		final var t4 = new IuTransaction(t2);
		t3.commit();
		t4.commit();
		final var e = assertThrows(IllegalStateException.class, t2::rollback);
		assertInstanceOf(HeuristicCommitException.class, e.getCause());
		assertSame(Status.STATUS_COMMITTED, t4.getStatus());
		assertSame(Status.STATUS_COMMITTED, t3.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t2.getStatus());
		final var e2 = assertThrows(IllegalStateException.class, t::rollback);
		assertInstanceOf(HeuristicMixedException.class, e2.getCause());
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
	}

	@Test
	public void testHeuristicMixedFromRollbackError() throws Throwable {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofMillis(1250L));
		final var t2 = new IuTransaction(t);
		final var t3 = new IuTransaction(t2);
		final var t4 = new IuTransaction(t2);
		t3.commit();
		t4.commit();
		final var s = mock(Synchronization.class);
		final var r = new RuntimeException();
		doThrow(r).when(s).afterCompletion(Status.STATUS_ROLLEDBACK);
		t2.registerSynchronization(s);
		assertSame(r, assertThrows(RuntimeException.class, t::rollback));
		assertInstanceOf(HeuristicMixedException.class, r.getSuppressed()[0]);
		assertSame(Status.STATUS_COMMITTED, t4.getStatus());
		assertSame(Status.STATUS_COMMITTED, t3.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t2.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
	}

	@Test
	public void testJoinMustBeFromSameBranch() {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		assertThrows(IllegalArgumentException.class,
				() -> new IuTransaction(Duration.ofSeconds(1L)).join(new IuTransaction(Duration.ofSeconds(1L))));
	}

	@Test
	public void testSuspendAndJoin()
			throws XAException, RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(2L));

		final var t2 = new IuTransaction(t);
		final var sync = mock(Synchronization.class);
		t2.registerSynchronization(sync);
		final var resource = mock(XAResource.class);
		when(resource.isSameRM(resource)).thenReturn(true);
		when(resource.prepare(t.getTransactionKey())).thenReturn(XAResource.XA_RDONLY);
		t2.enlistResource(resource);
		verify(resource).start(t2.getTransactionKey(), XAResource.TMNOFLAGS);
		t2.suspend();
		verify(resource).end(t2.getTransactionKey(), XAResource.TMSUSPEND);

		final var t3 = new IuTransaction(t);
		final var sync2 = mock(Synchronization.class);
		t3.registerInterposedSynchronization(sync2);
		final var resource2 = mock(XAResource.class);
		when(resource2.isSameRM(resource2)).thenReturn(true);
		when(resource2.prepare(t.getTransactionKey())).thenReturn(XAResource.XA_OK);
		t3.enlistResource(resource2);
		verify(resource2).start(t3.getTransactionKey(), XAResource.TMNOFLAGS);
		t3.suspend();
		verify(resource2).end(t3.getTransactionKey(), XAResource.TMSUSPEND);

		t2.join(t3);
		verify(sync2).afterCompletion(Status.STATUS_NO_TRANSACTION);
		verify(resource2).end(t3.getTransactionKey(), XAResource.TMSUCCESS);

		t.join(t2);
		verify(sync).afterCompletion(Status.STATUS_NO_TRANSACTION);
		verify(resource).end(t2.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource).start(t2.getTransactionKey(), XAResource.TMJOIN);
		verify(resource2).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource2).start(t3.getTransactionKey(), XAResource.TMJOIN);

		t.commit();
		verify(sync).beforeCompletion();
		verify(sync2).beforeCompletion();
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource2).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).prepare(t.getTransactionKey());
		verify(resource2).prepare(t.getTransactionKey());
		verify(resource2).commit(t.getTransactionKey(), false);
		verify(sync).afterCompletion(Status.STATUS_COMMITTED);
		verify(sync2).afterCompletion(Status.STATUS_COMMITTED);
	}

	@Test
	public void testSuspendAndCommitParentWithBadSync()
			throws XAException, RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var sync = mock(Synchronization.class);
		final var t = new IuTransaction(Duration.ofSeconds(2L));
		final var t2 = new IuTransaction(t);
		t2.registerSynchronization(sync);
		final var resource = mock(XAResource.class);
		when(resource.isSameRM(resource)).thenReturn(true);
		when(resource.prepare(t2.getTransactionKey())).thenReturn(XAResource.XA_RDONLY);
		t2.enlistResource(resource);
		verify(resource).start(t2.getTransactionKey(), XAResource.TMNOFLAGS);
		t2.suspend();
		verify(resource).end(t2.getTransactionKey(), XAResource.TMSUSPEND);

		final var t3 = new IuTransaction(t);
		final var sync2 = mock(Synchronization.class);
		final var r = new RuntimeException();
		doThrow(r).when(sync2).beforeCompletion();
		t3.registerInterposedSynchronization(sync2);
		final var resource2 = mock(XAResource.class);
		when(resource2.isSameRM(resource2)).thenReturn(true);
		when(resource2.prepare(t.getTransactionKey())).thenReturn(XAResource.XA_OK);
		t3.enlistResource(resource2);
		verify(resource2).start(t3.getTransactionKey(), XAResource.TMNOFLAGS);
		t3.suspend();
		verify(resource2).end(t3.getTransactionKey(), XAResource.TMSUSPEND);

		final var rb = assertThrows(RollbackException.class, t::commit);
		assertSame(r, rb.getCause());
		assertInstanceOf(HeuristicMixedException.class, rb.getSuppressed()[0]);
		verify(sync).beforeCompletion();
		verify(resource).end(t2.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).prepare(t2.getTransactionKey());
		verify(sync).afterCompletion(Status.STATUS_COMMITTED);
		verify(sync2).beforeCompletion();
		verify(resource2).end(t3.getTransactionKey(), XAResource.TMFAIL);
		verify(resource2).rollback(t3.getTransactionKey());
		verify(sync2).afterCompletion(Status.STATUS_ROLLEDBACK);
	}

	@Test
	public void testJoinAndRollback() throws RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var sync = mock(Synchronization.class);
		final var t = new IuTransaction(Duration.ofSeconds(2L));
		final var t2 = new IuTransaction(t);
		t2.registerSynchronization(sync);
		t.join(t2);
		verify(sync).afterCompletion(Status.STATUS_NO_TRANSACTION);
		t.rollback();
		verify(sync).afterCompletion(Status.STATUS_ROLLEDBACK);
	}

	@Test
	public void testRollbackOnlyOnFirstBranch()
			throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var t = new IuTransaction(Duration.ofSeconds(2L));
		final var t2 = new IuTransaction(t);
		t2.setRollbackOnly();
		final var t3 = new IuTransaction(t);
		assertThrows(RollbackException.class, t::commit);
		assertSame(Status.STATUS_ROLLEDBACK, t.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t2.getStatus());
		assertSame(Status.STATUS_ROLLEDBACK, t3.getStatus());
	}

	@Test
	public void testEnlistWhileSuspended() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		when(resource.isSameRM(resource)).thenReturn(true);
		final var t = new IuTransaction(Duration.ofSeconds(2L));
		t.suspend();
		t.enlistResource(resource);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUSPEND);
		final var t2 = new IuTransaction(t);
		t2.enlistResource(resource);
		verify(resource).start(t2.getTransactionKey(), XAResource.TMNOFLAGS);
		t.join(t2);
		verify(resource).end(t2.getTransactionKey(), XAResource.TMSUCCESS);
		t.resume();
		verify(resource).start(t.getTransactionKey(), XAResource.TMRESUME);
		verify(resource).start(t2.getTransactionKey(), XAResource.TMJOIN);
	}

	@Test
	public void testSuspendJoinAndCommit()
			throws XAException, RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(2L));
		t.suspend();
		final var t2 = new IuTransaction(t);
		t2.enlistResource(resource);
		t.join(t2);
		t.commit();
		verify(resource).start(t2.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource).end(t2.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource).start(t2.getTransactionKey(), XAResource.TMJOIN);
		verify(resource).end(t.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).prepare(t.getTransactionKey());
		verify(resource).commit(t.getTransactionKey(), false);
	}

	@Test
	public void testSuspendJoinAndRollback()
			throws XAException, RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE, ".*");
		final var resource = mock(XAResource.class);
		final var t = new IuTransaction(Duration.ofSeconds(2L));
		t.suspend();
		final var t2 = new IuTransaction(t);
		t2.enlistResource(resource);
		t.join(t2);
		t.rollback();
		verify(resource).start(t2.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource).end(t2.getTransactionKey(), XAResource.TMSUCCESS);
		verify(resource).start(t.getTransactionKey(), XAResource.TMNOFLAGS);
		verify(resource).start(t2.getTransactionKey(), XAResource.TMJOIN);
		verify(resource).end(t.getTransactionKey(), XAResource.TMFAIL);
		verify(resource).rollback(t.getTransactionKey());
	}

	@Test
	public void testXaHeuristicCommit() throws XAException, RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var x = mock(XAResource.class);
		t.enlistResource(x);
		final var xe = new XAException(XAException.XA_HEURCOM);
		doThrow(xe).when(x).commit(t.getTransactionKey(), false);
		assertDoesNotThrow(t::commit);
		verify(x).forget(t.getTransactionKey());
	}
	
	@Test
	public void testXaHeuristicCommitForgetFailure() throws XAException, RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var x = mock(XAResource.class);
		t.enlistResource(x);
		final var xe = new XAException(XAException.XA_HEURCOM);
		final var xe2 = new XAException(XAException.XAER_NOTA);
		doThrow(xe).when(x).commit(t.getTransactionKey(), false);
		doThrow(xe2).when(x).forget(t.getTransactionKey());
		assertSame(xe, assertThrows(RollbackException.class, t::commit).getCause());
		assertSame(xe2, xe.getSuppressed()[0]);
	}
	
	@Test
	public void testXaHeurHazRollback() throws XAException, RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE);
		final var t = new IuTransaction(Duration.ofSeconds(5L));
		final var x = mock(XAResource.class);
		t.enlistResource(x);
		final var xe = new XAException(XAException.XA_HEURHAZ);
		doThrow(xe).when(x).commit(t.getTransactionKey(), false);
		assertSame(xe, assertThrows(RollbackException.class, t::commit).getCause());
		verify(x).forget(t.getTransactionKey());
	}

	@Test
	public void testXaHeurMixRollback() throws XAException, RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE);
		final var t = new IuTransaction(Duration.ofSeconds(1L));
		final var x = mock(XAResource.class);
		t.enlistResource(x);
		final var xe = new XAException(XAException.XA_HEURMIX);
		doThrow(xe).when(x).commit(t.getTransactionKey(), false);
		assertSame(xe, assertThrows(RollbackException.class, t::commit).getCause());
		verify(x).forget(t.getTransactionKey());
	}

	@Test
	public void testXaHeuristicRollback() throws XAException, RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE);
		final var t = new IuTransaction(Duration.ofSeconds(1L));
		final var x = mock(XAResource.class);
		t.enlistResource(x);
		final var xe = new XAException(XAException.XA_HEURRB);
		doThrow(xe).when(x).commit(t.getTransactionKey(), false);
		assertSame(xe, assertThrows(RollbackException.class, t::commit).getCause());
		verify(x).forget(t.getTransactionKey());
	}

	@Test
	public void testXaErrorRollback() throws XAException, RollbackException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE);
		final var t = new IuTransaction(Duration.ofSeconds(1L));
		final var x = mock(XAResource.class);
		t.enlistResource(x);
		final var xe = new XAException(XAException.XAER_RMFAIL);
		doThrow(xe).when(x).commit(t.getTransactionKey(), false);
		assertSame(xe, assertThrows(RollbackException.class, t::commit).getCause());
		verify(x, never()).forget(t.getTransactionKey());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testStatusChange() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINE);
		final var c = mock(Consumer.class);
		final var t = new IuTransaction(Duration.ofSeconds(1L), c);
		t.commit();
		// STATUS_PREPARING
		// STATUS_PREPARED
		// STATUS_COMMITTING
		// STATUS_COMMITTED
		verify(c, times(4)).accept(t);
	}
}
