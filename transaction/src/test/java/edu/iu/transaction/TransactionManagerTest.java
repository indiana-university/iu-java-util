package edu.iu.transaction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuAsynchronousSubscription;
import edu.iu.IuUtilityTaskController;
import edu.iu.test.IuTestLogger;
import jakarta.transaction.HeuristicCommitException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;

@SuppressWarnings("javadoc")
public class TransactionManagerTest {

	private static IuTransactionManager TM = new IuTransactionManager();

	@AfterEach
	public void teardown() {
		TM.clearThreadState();
	}

	@Test
	public void testTimeout() throws Exception {
		Field f = IuTransactionManager.class.getDeclaredField("timeout");
		f.setAccessible(true);
		assertEquals(Duration.ofMinutes(2L), f.get(TM));
		TM.setTimeout(Duration.ofMillis(10L));
		assertEquals(Duration.ofMillis(10L), f.get(TM));
		TM.setTransactionTimeout(5);
		assertEquals(Duration.ofSeconds(5L), f.get(TM));
		TM.setTransactionTimeout(0);
		assertEquals(Duration.ofMinutes(2L), f.get(TM));
	}

	@Test
	public void testBeginAndCommit() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");
		TM.begin();
		IuTestLogger.assertExpectedMessages();

		assertEquals(Status.STATUS_ACTIVE, TM.getTransactionStatus());

		final var tx = TM.getTransaction();
		assertEquals(Status.STATUS_ACTIVE, tx.getStatus());
		assertEquals(TM.getTransactionKey(), tx.getTransactionKey());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* commit");
		TM.commit();

		assertEquals(Status.STATUS_NO_TRANSACTION, TM.getTransactionStatus());
		assertEquals(Status.STATUS_COMMITTED, tx.getStatus());
		assertNull(TM.getTransactionKey());
	}

	@Test
	public void testHeuristicRollback() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");
		TM.begin();
		IuTestLogger.assertExpectedMessages();

		assertEquals(Status.STATUS_ACTIVE, TM.getTransactionStatus());

		final var tx = TM.getTransaction();
		assertEquals(Status.STATUS_ACTIVE, tx.getStatus());
		assertEquals(TM.getTransactionKey(), tx.getTransactionKey());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* branch iuxid-63225\\+.*");
		TM.begin();
		IuTestLogger.assertExpectedMessages();

		assertEquals(Status.STATUS_ACTIVE, TM.getTransactionStatus());

		final var btx = TM.getTransaction();
		assertEquals(Status.STATUS_ACTIVE, tx.getStatus());
		assertNotEquals(TM.getTransactionKey(), tx.getTransactionKey());
		assertEquals(TM.getTransactionKey(), btx.getTransactionKey());
		assertEquals(btx.getTransactionKey().getGlobalTransactionId(), tx.getTransactionKey().getGlobalTransactionId());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* rollback");
		TM.rollback();

		assertEquals(Status.STATUS_ROLLEDBACK, btx.getStatus());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* rollback");
		assertThrows(HeuristicRollbackException.class, TM::commit);
		assertEquals(Status.STATUS_ROLLEDBACK, tx.getStatus());
	}

	@Test
	public void testHeuristicCommit() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");
		TM.begin();
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* branch iuxid-63225\\+.*");
		TM.begin();
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* commit");
		TM.commit();

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* rollback");
		assertInstanceOf(HeuristicCommitException.class,
				assertThrows(IllegalStateException.class, TM::rollback).getCause());
	}

	@Test
	public void testBeginAndVisit() {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");
		TM.begin();

		final var t = TM.getTransaction();
		assertSame(Status.STATUS_ACTIVE, t.getStatus());
		assertSame(Status.STATUS_ACTIVE, t.getTransactionStatus());
		assertFalse(t.getRollbackOnly());

		class Box {
			boolean found;
		}
		final var box = new Box();
		TM.visit(tx -> {
			if (tx == t)
				box.found = true;
			return null;
		});
		assertTrue(box.found);
	}

	@Test
	public void testSubscribe() throws Throwable {
		IuAsynchronousSubscription<IuTransaction> sub = TM.subscribe();
		final var t = new IuUtilityTaskController<>(() -> {
			final var i = sub.stream().iterator();
			IuTransaction last = null;
			while (i.hasNext())
				last = i.next();
			return last;
		}, Instant.now().plusSeconds(5L));
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");

		TM.begin();
		sub.close();
		assertSame(TM.getTransaction(), t.get());
	}

	@Test
	public void testNoTransaction() {
		assertThrows(IllegalStateException.class, TM::commit);
		assertThrows(IllegalStateException.class, TM::rollback);
	}

	@Test
	public void testRollbackOnly() {
		assertThrows(IllegalStateException.class, TM::setRollbackOnly);
		assertThrows(IllegalStateException.class, TM::getRollbackOnly);
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		TM.begin();
		assertFalse(TM.getRollbackOnly());
		TM.setRollbackOnly();
		assertTrue(TM.getRollbackOnly());
		assertThrows(RollbackException.class, TM::commit);
	}
	
	@Test
	public void testResources() {
		assertThrows(IllegalStateException.class, () -> TM.getResource(null));
		assertThrows(IllegalStateException.class, () -> TM.putResource(null, null));
		assertThrows(IllegalStateException.class, () -> TM.registerInterposedSynchronization(null));
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		TM.begin();
		final var key = new Object();
		final var value = new Object();
		TM.putResource(key, value);
		assertEquals(value, TM.getResource(key));
		final var synch = mock(Synchronization.class);
		TM.registerInterposedSynchronization(synch);
		assertDoesNotThrow(TM::commit);
		verify(synch).beforeCompletion();
		verify(synch).afterCompletion(Status.STATUS_COMMITTED);
	}

	@Test
	public void testSuspendResume() {
		assertThrows(IllegalStateException.class, () -> TM.suspend());
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		final var badTx = mock(Transaction.class);
		assertThrows(InvalidTransactionException.class, () -> TM.resume(badTx));
		TM.begin();
		final var tx = TM.suspend();
		assertEquals(Status.STATUS_NO_TRANSACTION, TM.getStatus());
		assertDoesNotThrow(() -> TM.resume(tx));
		assertEquals(tx, TM.getTransaction());
		assertDoesNotThrow(TM::commit);
	}
	
	@Test
	public void testSuspendResumeNested() {
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		TM.begin();
		final var tx1 = TM.getTransaction();
		TM.begin();
		final var tx = TM.suspend();
		assertEquals(Status.STATUS_ACTIVE, TM.getStatus());
		assertEquals(tx1, TM.getTransaction());
		assertDoesNotThrow(() -> TM.resume(tx));
		assertEquals(tx, TM.getTransaction());
		assertEquals(tx, TM.suspend());
		assertDoesNotThrow(TM::commit);
		assertEquals(Status.STATUS_COMMITTED, tx.getStatus());
	}

	@Test
	public void testSuspendResumeBranch() {
		assertThrows(IllegalStateException.class, () -> TM.suspend());
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		final var badTx = mock(Transaction.class);
		assertThrows(InvalidTransactionException.class, () -> TM.resume(badTx));
		TM.begin();
		final var tx = TM.suspend();
		TM.begin();
		assertThrows(IllegalStateException.class, () -> TM.resume(tx));
		assertDoesNotThrow(TM::commit);
	}

}
