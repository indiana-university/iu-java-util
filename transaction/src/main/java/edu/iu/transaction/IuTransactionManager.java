package edu.iu.transaction;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import edu.iu.IuAsynchronousSubject;
import edu.iu.IuAsynchronousSubscription;
import edu.iu.IuVisitor;
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
 */
@Resources({ //
		@Resource(type = TransactionManager.class), //
		@Resource(type = UserTransaction.class), //
		@Resource(type = TransactionSynchronizationRegistry.class), //
})
public final class IuTransactionManager
		implements TransactionManager, UserTransaction, TransactionSynchronizationRegistry {

	private final ThreadLocal<Deque<IuTransaction>> activeTransactions = new ThreadLocal<Deque<IuTransaction>>();
	private final IuVisitor<IuTransaction> visitor = new IuVisitor<>();
	private final IuAsynchronousSubject<IuTransaction> subject = visitor.subject();
	private Duration timeout = Duration.ofMinutes(2L);

	/**
	 * Default constructor.
	 */
	public IuTransactionManager() {
	}

	/**
	 * Clears the transaction state from the current thread, as a final safeguard,
	 * to be invoked after all transaction resources <em>should</em> have been
	 * released, and before returning the thread for reuse.
	 */
	public void clearThreadState() {
		activeTransactions.remove();
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

	/**
	 * Visits {@link IuTransaction} managed by this instance.
	 * 
	 * @param transactionVisitor {@link IuVisitor#visit(Function) visitor function}
	 * @return {@link IuVisitor#visit(Function) visitor result}
	 */
	public <V> Optional<V> visit(Function<IuTransaction, Optional<V>> transactionVisitor) {
		return visitor.visit(transactionVisitor);
	}

	/**
	 * Subscribes to an internally controlled {@link IuAsynchronousSubject subject}
	 * of <strong>incomplete</strong> transactions.
	 * 
	 * <p>
	 * After initial traversal, {@link IuTransaction} references will be provided by
	 * the subject on status change, including change to <strong>completed</strong>
	 * statuses {@link Status#STATUS_NO_TRANSACTION},
	 * {@link Status#STATUS_COMMITTED}, and {@link Status#STATUS_ROLLEDBACK}.
	 * </p>
	 * 
	 * @return {@link IuAsynchronousSubscription} of all open transactions
	 * @see IuAsynchronousSubject#subscribe
	 */
	public IuAsynchronousSubscription<IuTransaction> subscribe() {
		return subject.subscribe();
	}

	/**
	 * Listens to an internally controlled {@link IuAsynchronousSubject subject} of
	 * <strong>incomplete</strong> transactions.
	 * 
	 * <p>
	 * After initial traversal, {@link IuTransaction} references will be provided by
	 * the subject on status change, including change to <strong>completed</strong>
	 * statuses {@link Status#STATUS_NO_TRANSACTION},
	 * {@link Status#STATUS_COMMITTED}, and {@link Status#STATUS_ROLLEDBACK}.
	 * </p>
	 * 
	 * @param listener {@link Consumer Consumer&lt;IuTransaction&gt;}
	 * @return {@link Runnable} thunk to cancel the listener
	 * @see IuAsynchronousSubject#listen(Consumer)
	 */
	public Runnable listen(Consumer<IuTransaction> listener) {
		return subject.listen(listener);
	}

	@Override
	public void begin() {
		var active = activeTransactions.get();

		final IuTransaction transaction;
		if (active == null) {
			active = new ArrayDeque<>();
			transaction = new IuTransaction(timeout, this::handleStatusChange);
		} else
			transaction = new IuTransaction(active.peek());

		subject.accept(transaction);
		visitor.accept(transaction);

		active.push(transaction);

		activeTransactions.set(active);
	}

	@Override
	public IuTransaction getTransaction() {
		final var active = activeTransactions.get();
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
		final var active = activeTransactions.get();
		if (active == null)
			throw new IllegalStateException();

		final var transaction = active.pop();
		if (active.isEmpty())
			activeTransactions.remove();

		transaction.suspend();
		return transaction;
	}

	@Override
	public void resume(Transaction transaction) throws InvalidTransactionException {
		if (!(transaction instanceof IuTransaction iuTransaction))
			throw new InvalidTransactionException();

		var active = activeTransactions.get();
		if (active == null)
			active = new ArrayDeque<>();
		else if (!Arrays.equals(active.peek().getTransactionKey().getGlobalTransactionId(),
				iuTransaction.getTransactionKey().getGlobalTransactionId()))
			throw new IllegalStateException();

		iuTransaction.resume();
		active.push(iuTransaction);
		activeTransactions.set(active);
	}

	@Override
	public void commit() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		var active = activeTransactions.get();
		if (active == null)
			throw new IllegalStateException();

		final var transaction = active.pop();
		if (active.isEmpty())
			activeTransactions.remove();

		transaction.commit();
	}

	@Override
	public void rollback() {
		var active = activeTransactions.get();
		if (active == null)
			throw new IllegalStateException();

		final var transaction = active.pop();
		if (active.isEmpty())
			activeTransactions.remove();

		transaction.rollback();
	}

	private void handleStatusChange(IuTransaction transaction) {
		subject.accept(transaction);
		if (transaction.isCompleted())
			visitor.clear(transaction);
	}
}
