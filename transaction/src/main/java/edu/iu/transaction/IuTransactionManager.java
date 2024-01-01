package edu.iu.transaction;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import jakarta.annotation.Resource;
import jakarta.annotation.Resources;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

/**
 * Portable virtual transaction manager implementation.
 * 
 * <p>
 * This transaction manager is fully virtual and may be used as a singleton or
 * session-bound manager. Transactions supplied by this implementation are
 * managed as a static visitor via {@link IuTransaction}.
 * </p>
 */
@Resources({ //
		@Resource(type = TransactionManager.class), //
		@Resource(type = UserTransaction.class), //
		@Resource(type = TransactionSynchronizationRegistry.class), //
})
public final class IuTransactionManager
		implements TransactionManager, UserTransaction, TransactionSynchronizationRegistry {

	private static final ThreadLocal<Deque<IuTransaction>> ACTIVE = new ThreadLocal<Deque<IuTransaction>>();

	/**
	 * Clears the transaction state from the current thread, as a final safeguard,
	 * to be invoked after all transaction resources <em>should</em> have been
	 * released, and before returning the thread for reuse.
	 */
	public static void clearThreadState() {
		ACTIVE.remove();
	}

	private Duration timeout = Duration.ofMinutes(2L);

	/**
	 * Constructor.
	 */
	public IuTransactionManager() {
	}

	/**
	 * Sets the transaction timeout.
	 * 
	 * @param timeout timeout
	 */
	@Resource
	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	@Override
	public void begin() {
		var active = ACTIVE.get();

		final IuTransaction transaction;
		if (active == null) {
			active = new ArrayDeque<>();
			transaction = new IuTransaction(timeout);
		} else
			transaction = new IuTransaction(active.peek());

		active.push(transaction);

		ACTIVE.set(active);
	}

	@Override
	public IuTransaction getTransaction() {
		final var active = ACTIVE.get();
		return active == null ? null : active.peek();
	}

	@Override
	public int getStatus() {
		var transaction = getTransaction();
		if (transaction == null)
			return Status.STATUS_NO_TRANSACTION;
		else
			return transaction.getStatus();
	}

	@Override
	public void setRollbackOnly() {
		var transaction = getTransaction();
		if (transaction == null)
			throw new IllegalStateException();
		else
			transaction.setRollbackOnly();
	}

	@Override
	public void setTransactionTimeout(int timeout) {
		if (timeout < 1)
			setTimeout(Duration.ofMinutes(2L));
		else
			setTimeout(Duration.ofSeconds(timeout));
	}

	@Override
	public IuXid getTransactionKey() {
		var transaction = getTransaction();
		if (transaction == null)
			return null;
		else
			return transaction.getTransactionKey();
	}

	@Override
	public void putResource(Object key, Object value) {
		var transaction = getTransaction();
		if (transaction == null)
			throw new IllegalStateException();
		else
			transaction.putResource(key, value);
	}

	@Override
	public Object getResource(Object key) {
		var transaction = getTransaction();
		if (transaction == null)
			throw new IllegalStateException();
		else
			return transaction.getResource(key);
	}

	@Override
	public void registerInterposedSynchronization(Synchronization sync) {
		var transaction = getTransaction();
		if (transaction == null)
			throw new IllegalStateException();
		else
			transaction.registerInterposedSynchronization(sync);
	}

	@Override
	public int getTransactionStatus() {
		return getStatus();
	}

	@Override
	public boolean getRollbackOnly() {
		var transaction = getTransaction();
		if (transaction == null)
			throw new IllegalStateException();
		else
			return transaction.getRollbackOnly();
	}

	@Override
	public IuTransaction suspend() {
		final var active = ACTIVE.get();
		if (active == null)
			throw new IllegalStateException();

		final var transaction = active.pop();
		if (active.isEmpty())
			ACTIVE.remove();

		transaction.suspend();
		return transaction;
	}

	@Override
	public void resume(Transaction transaction) throws InvalidTransactionException {
		if (!(transaction instanceof IuTransaction iuTransaction))
			throw new InvalidTransactionException();

		var active = ACTIVE.get();
		if (active == null)
			active = new ArrayDeque<>();
		else if (!Arrays.equals(active.peek().getTransactionKey().getGlobalTransactionId(),
				iuTransaction.getTransactionKey().getGlobalTransactionId()))
			throw new IllegalStateException();

		iuTransaction.resume();
		active.push(iuTransaction);
		ACTIVE.set(active);
	}

	@Override
	public void commit() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		var active = ACTIVE.get();
		if (active == null)
			throw new IllegalStateException();

		final var transaction = active.pop();
		if (active.isEmpty())
			ACTIVE.remove();

		transaction.commit();
	}

	@Override
	public void rollback() {
		var active = ACTIVE.get();
		if (active == null)
			throw new IllegalStateException();

		final var transaction = active.pop();
		if (active.isEmpty())
			ACTIVE.remove();

		transaction.rollback();
	}

}
