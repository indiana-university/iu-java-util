package edu.iu.transaction;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.transaction.xa.Xid;

import edu.iu.IuAsynchronousSubject;
import edu.iu.IuAsynchronousSubscription;
import edu.iu.IuVisitor;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

/**
 * Portable virtual transaction manager implementation.
 */
public final class IuTransactionManager
		implements TransactionManager, UserTransaction, TransactionSynchronizationRegistry, AutoCloseable {

	private final ThreadLocal<Deque<IuTransaction>> activeTransactions = new ThreadLocal<Deque<IuTransaction>>();
	private final IuVisitor<IuTransaction> visitor = new IuVisitor<>();
	private final IuAsynchronousSubject<IuTransaction> subject = visitor.subject();
	private final ScheduledThreadPoolExecutor rollbackScheduler;
	private Duration timeout = Duration.ofMinutes(2L);

	/**
	 * Default constructor.
	 */
	public IuTransactionManager() {
		final var threadGroup = new ThreadGroup("iu-java-transaction");
		final var threadFactory = new ThreadFactory() {
			private volatile int num;

			@Override
			public synchronized Thread newThread(Runnable r) {
				return new Thread(threadGroup, r, "iu-java-transaction/" + (++num));
			}
		};
		rollbackScheduler = new ScheduledThreadPoolExecutor(8, threadFactory);
		rollbackScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
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
	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	/**
	 * Visits {@link IuTransaction} managed by this instance.
	 *
	 * @param <V>                optional return type
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

	@Override
	public void begin() {
		var active = activeTransactions.get();

		final IuTransaction transaction;
		if (active == null) {
			active = new ArrayDeque<>();
			transaction = new IuTransaction(timeout, this::handleStatusChange, rollbackScheduler);
		} else
			transaction = new IuTransaction(active.peek(), rollbackScheduler);

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
	public void setTransactionTimeout(int timeout) throws SystemException {
		if (timeout == 0)
			setTimeout(Duration.ofMinutes(2L));
		else if (timeout > 0)
			setTimeout(Duration.ofSeconds(timeout));
		else
			throw new SystemException();
	}

	@Override
	public Object getTransactionKey() {
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
	public IuTransaction suspend() throws SystemException {
		final var active = activeTransactions.get();
		if (active == null)
			throw new SystemException();

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
		else if (!Arrays.equals(((Xid) active.peek().getTransactionKey()).getGlobalTransactionId(),
				((Xid) iuTransaction.getTransactionKey()).getGlobalTransactionId()))
			throw new InvalidTransactionException();

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

	@Override
	public void close() throws Exception {
		rollbackScheduler.shutdown();
		visitor.visit(t -> {
			if (t != null)
				t.rollback();
			return null;
		});
		subject.close();
		rollbackScheduler.awaitTermination(timeout.getSeconds(), TimeUnit.SECONDS);
	}

}
