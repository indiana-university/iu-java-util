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
import org.junit.jupiter.api.BeforeEach;
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

	private IuTransactionManager transactionManager;

	@BeforeEach
	public void setup() {
		transactionManager = new IuTransactionManager();
	}

	@AfterEach
	public void teardown() throws Exception {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		transactionManager.close();
	}

	@Test
	public void testTimeout() throws Exception {
		Field f = IuTransactionManager.class.getDeclaredField("timeout");
		f.setAccessible(true);
		assertEquals(Duration.ofMinutes(2L), f.get(transactionManager));
		transactionManager.setTimeout(Duration.ofMillis(10L));
		assertEquals(Duration.ofMillis(10L), f.get(transactionManager));
		transactionManager.setTransactionTimeout(5);
		assertEquals(Duration.ofSeconds(5L), f.get(transactionManager));
		transactionManager.setTransactionTimeout(0);
		assertEquals(Duration.ofMinutes(2L), f.get(transactionManager));
	}

	@Test
	public void testBeginAndCommit() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");
		transactionManager.begin();
		IuTestLogger.assertExpectedMessages();

		assertEquals(Status.STATUS_ACTIVE, transactionManager.getTransactionStatus());

		final var tx = transactionManager.getTransaction();
		assertEquals(Status.STATUS_ACTIVE, tx.getStatus());
		assertEquals(transactionManager.getTransactionKey(), tx.getTransactionKey());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* commit");
		transactionManager.commit();

		assertEquals(Status.STATUS_NO_TRANSACTION, transactionManager.getTransactionStatus());
		assertEquals(Status.STATUS_COMMITTED, tx.getStatus());
		assertNull(transactionManager.getTransactionKey());
	}

	@Test
	public void testHeuristicRollback() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");
		transactionManager.begin();
		IuTestLogger.assertExpectedMessages();

		assertEquals(Status.STATUS_ACTIVE, transactionManager.getTransactionStatus());

		final var tx = transactionManager.getTransaction();
		assertEquals(Status.STATUS_ACTIVE, tx.getStatus());
		assertEquals(transactionManager.getTransactionKey(), tx.getTransactionKey());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* branch iuxid-63225\\+.*");
		transactionManager.begin();
		IuTestLogger.assertExpectedMessages();

		assertEquals(Status.STATUS_ACTIVE, transactionManager.getTransactionStatus());

		final var btx = transactionManager.getTransaction();
		assertEquals(Status.STATUS_ACTIVE, tx.getStatus());
		assertNotEquals(transactionManager.getTransactionKey(), tx.getTransactionKey());
		assertEquals(transactionManager.getTransactionKey(), btx.getTransactionKey());
		assertEquals(btx.getTransactionKey().getGlobalTransactionId(), tx.getTransactionKey().getGlobalTransactionId());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* rollback");
		transactionManager.rollback();

		assertEquals(Status.STATUS_ROLLEDBACK, btx.getStatus());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* rollback");
		assertThrows(HeuristicRollbackException.class, transactionManager::commit);
		assertEquals(Status.STATUS_ROLLEDBACK, tx.getStatus());
	}

	@Test
	public void testHeuristicCommit() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");
		transactionManager.begin();
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* branch iuxid-63225\\+.*");
		transactionManager.begin();
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* commit");
		transactionManager.commit();

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* rollback");
		assertInstanceOf(HeuristicCommitException.class,
				assertThrows(IllegalStateException.class, transactionManager::rollback).getCause());
	}

	@Test
	public void testBeginAndVisit() {
		IuTestLogger.allow("edu.iu.transaction.IuTransaction", Level.FINER);
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");
		transactionManager.begin();

		final var t = transactionManager.getTransaction();
		assertSame(Status.STATUS_ACTIVE, t.getStatus());
		assertSame(Status.STATUS_ACTIVE, t.getTransactionStatus());
		assertFalse(t.getRollbackOnly());

		class Box {
			boolean found;
		}
		final var box = new Box();
		transactionManager.visit(tx -> {
			if (tx == t)
				box.found = true;
			return null;
		});
		assertTrue(box.found);

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* rollback");
	}

	@Test
	public void testSubscribe() throws Throwable {
		IuAsynchronousSubscription<IuTransaction> sub = transactionManager.subscribe();
		final var t = new IuUtilityTaskController<>(() -> {
			final var i = sub.stream().iterator();
			IuTransaction last = null;
			while (i.hasNext())
				last = i.next();
			return last;
		}, Instant.now().plusSeconds(5L));
		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* begin");

		transactionManager.begin();
		sub.close();
		assertSame(transactionManager.getTransaction(), t.get());

		IuTestLogger.expect("edu.iu.transaction.IuTransaction", Level.FINE, "iuxid-63225\\+.* rollback");
	}

	@Test
	public void testNoTransaction() {
		assertThrows(IllegalStateException.class, transactionManager::commit);
		assertThrows(IllegalStateException.class, transactionManager::rollback);
	}

	@Test
	public void testRollbackOnly() {
		assertThrows(IllegalStateException.class, transactionManager::setRollbackOnly);
		assertThrows(IllegalStateException.class, transactionManager::getRollbackOnly);
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		transactionManager.begin();
		assertFalse(transactionManager.getRollbackOnly());
		transactionManager.setRollbackOnly();
		assertTrue(transactionManager.getRollbackOnly());
		assertThrows(RollbackException.class, transactionManager::commit);
	}

	@Test
	public void testResources() {
		assertThrows(IllegalStateException.class, () -> transactionManager.getResource(null));
		assertThrows(IllegalStateException.class, () -> transactionManager.putResource(null, null));
		assertThrows(IllegalStateException.class, () -> transactionManager.registerInterposedSynchronization(null));
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		transactionManager.begin();
		final var key = new Object();
		final var value = new Object();
		transactionManager.putResource(key, value);
		assertEquals(value, transactionManager.getResource(key));
		final var synch = mock(Synchronization.class);
		transactionManager.registerInterposedSynchronization(synch);
		assertDoesNotThrow(transactionManager::commit);
		verify(synch).beforeCompletion();
		verify(synch).afterCompletion(Status.STATUS_COMMITTED);
	}

	@Test
	public void testSuspendResume() {
		assertThrows(IllegalStateException.class, () -> transactionManager.suspend());
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		final var badTx = mock(Transaction.class);
		assertThrows(InvalidTransactionException.class, () -> transactionManager.resume(badTx));
		transactionManager.begin();
		final var tx = transactionManager.suspend();
		assertEquals(Status.STATUS_NO_TRANSACTION, transactionManager.getStatus());
		assertDoesNotThrow(() -> transactionManager.resume(tx));
		assertEquals(tx, transactionManager.getTransaction());
		assertDoesNotThrow(transactionManager::commit);
	}

	@Test
	public void testSuspendResumeNested() {
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		transactionManager.begin();
		final var tx1 = transactionManager.getTransaction();
		transactionManager.begin();
		final var tx = transactionManager.suspend();
		assertEquals(Status.STATUS_ACTIVE, transactionManager.getStatus());
		assertEquals(tx1, transactionManager.getTransaction());
		assertDoesNotThrow(() -> transactionManager.resume(tx));
		assertEquals(tx, transactionManager.getTransaction());
		assertEquals(tx, transactionManager.suspend());
		assertDoesNotThrow(transactionManager::commit);
		assertEquals(Status.STATUS_COMMITTED, tx.getStatus());
	}

	@Test
	public void testSuspendResumeBranch() {
		assertThrows(IllegalStateException.class, () -> transactionManager.suspend());
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE, "iuxid-63225\\+.*");
		final var badTx = mock(Transaction.class);
		assertThrows(InvalidTransactionException.class, () -> transactionManager.resume(badTx));
		transactionManager.begin();
		final var tx = transactionManager.suspend();
		transactionManager.begin();
		assertThrows(IllegalStateException.class, () -> transactionManager.resume(tx));
		assertDoesNotThrow(transactionManager::commit);
	}

	@Test
	public void testClearThreadState() {
		IuTestLogger.allow(IuTransaction.class.getName(), Level.FINE);
		transactionManager.begin();
		transactionManager.clearThreadState();
		assertEquals(Status.STATUS_NO_TRANSACTION, transactionManager.getStatus());
	}
}
