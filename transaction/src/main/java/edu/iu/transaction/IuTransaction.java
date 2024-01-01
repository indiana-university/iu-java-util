package edu.iu.transaction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuVisitor;
import jakarta.transaction.HeuristicCommitException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Portable {@link Transaction} implementation.
 * 
 * <p>
 * Transactions managed via this implementation are time-sensitive and tracked
 * asynchronously to force rollback before enlisted resource timeouts expire.
 * </p>
 * 
 * <p>
 * This implementation supports distributed transaction branches with heuristic
 * commit/rollback, and join logic. It is thread-safe and intended for use with
 * high-volume parallel processing workloads.
 * </p>
 */
public class IuTransaction implements Transaction, TransactionSynchronizationRegistry {

	private static final Logger LOG = Logger.getLogger(IuTransaction.class.getName());

	private record SuspendedResource(Xid xid, int flags, XAResource resource) {
	}

	private static class RollbackContinuation {
		private final Deque<Synchronization> synchronizations = new ArrayDeque<>();
		private final Deque<Synchronization> interposedSynchronizations = new ArrayDeque<>();
		private final Queue<XAResource> prepared = new ArrayDeque<>();
		private final Queue<XAResource> delisted = new ArrayDeque<>();
	}

	private static final ScheduledThreadPoolExecutor ROLLBACK_SCHEDULER;
	static {
		final var threadGroup = new ThreadGroup("iu-java-transaction");
		final var threadFactory = new ThreadFactory() {
			private volatile int num;

			@Override
			public synchronized Thread newThread(Runnable r) {
				return new Thread(threadGroup, r, "iu-java-transaction/" + (++num));
			}
		};
		ROLLBACK_SCHEDULER = new ScheduledThreadPoolExecutor(8, threadFactory);
	}

	private static ScheduledFuture<?> scheduleRollback(IuTransaction t) {
		final var loader = Thread.currentThread().getContextClassLoader();
		final Runnable scheduledRollback = () -> {
			final var current = Thread.currentThread();
			final var loaderToRestore = current.getContextClassLoader();
			try {
				current.setContextClassLoader(loader);

				synchronized (t) {
					try {
						t.continueRollback(new RollbackContinuation());
					} catch (Throwable e) {
						LOG.log(Level.WARNING, e, () -> t.xid + " timed rollback failure");
					}

					t.notifyAll();
				}

			} finally {
				current.setContextClassLoader(loaderToRestore);
			}
		};

		return ROLLBACK_SCHEDULER.schedule(scheduledRollback, Duration.between(Instant.now(), t.expires).toNanos(),
				TimeUnit.NANOSECONDS);
	}

	private static final IuVisitor<IuTransaction> TX_VISITOR = new IuVisitor<>();

	/**
	 * Visit {@link IuTransaction} instances.
	 * 
	 * @param transactionVisitor {@link IuVisitor#visit(Function) visitor function}
	 * @return {@link IuVisitor#visit(Function) visitor result}
	 */
	public static <V> Optional<V> visit(Function<IuTransaction, Optional<V>> transactionVisitor) {
		return TX_VISITOR.visit(transactionVisitor);
	}

	/**
	 * Subscribes a transaction listener.
	 * 
	 * @param transactionVisitor {@link IuVisitor#visit(Function) visitor function}
	 * @return {@link IuVisitor#visit(Function) visitor result}
	 */
	public static Stream<IuTransaction> visit(Function<IuTransaction, Optional<V>> transactionVisitor) {
		return TX_VISITOR.visit(transactionVisitor);
	}

	/**
	 * Provides the string representation of a transaction status code.
	 * 
	 * @param status status code
	 * @return string representation
	 */
	static String describeStatus(int status) {
		switch (status) {
		case Status.STATUS_PREPARING:
			return "STATUS_PREPARING";
		case Status.STATUS_PREPARED:
			return "STATUS_PREPARED";
		case Status.STATUS_ACTIVE:
			return "STATUS_ACTIVE";
		case Status.STATUS_MARKED_ROLLBACK:
			return "STATUS_MARKED_ROLLBACK";
		case Status.STATUS_COMMITTING:
			return "STATUS_COMMITTING";
		case Status.STATUS_COMMITTED:
			return "STATUS_COMMITTED";
		case Status.STATUS_ROLLING_BACK:
			return "STATUS_ROLLING_BACK";
		case Status.STATUS_ROLLEDBACK:
			return "STATUS_ROLLEDBACK";
		case Status.STATUS_NO_TRANSACTION:
			return "STATUS_NO_TRANSACTION";
		case Status.STATUS_UNKNOWN:
			return "STATUS_UNKNOWN";
		default:
			return "STATUS_X" + status;
		}
	}

	/**
	 * Provides the string representation of an XA error code.
	 * 
	 * @param errorCode XA error code
	 * @return string representation
	 */
	static String describeXAErrorCode(int errorCode) {
		switch (errorCode) {
		case XAResource.XA_OK:
			return "XA_OK";
		case XAException.XA_RBBASE:
			return "XA_RBBASE";
		case XAException.XA_RBCOMMFAIL:
			return "XA_RBCOMMFAIL";
		case XAException.XA_RBDEADLOCK:
			return "XA_RBDEADLOCK";
		case XAException.XA_RBINTEGRITY:
			return "XA_RBINTEGRITY";
		case XAException.XA_RBOTHER:
			return "XA_RBOTHER";
		case XAException.XA_RBPROTO:
			return "XA_RBPROTO";
		case XAException.XA_RBTIMEOUT:
			return "XA_RBTIMEOUT";
		case XAException.XA_RBTRANSIENT:
			return "XA_RBTRANSIENT";
		case XAException.XA_NOMIGRATE:
			return "XA_NOMIGRATE";
		case XAException.XA_HEURHAZ:
			return "XA_HEURHAZ";
		case XAException.XA_HEURCOM:
			return "XA_HEURCOM";
		case XAException.XA_HEURRB:
			return "XA_HEURRB";
		case XAException.XA_HEURMIX:
			return "XA_HEURMIX";
		case XAException.XA_RETRY:
			return "XA_RETRY";
		case XAException.XA_RDONLY:
			return "XA_RDONLY";
		case XAException.XAER_ASYNC:
			return "XAER_ASYNC";
		case XAException.XAER_RMERR:
			return "XAER_RMERR";
		case XAException.XAER_NOTA:
			return "XAER_NOTA";
		case XAException.XAER_INVAL:
			return "XAER_INVAL";
		case XAException.XAER_PROTO:
			return "XAER_PROTO";
		case XAException.XAER_RMFAIL:
			return "XAER_RMFAIL";
		case XAException.XAER_DUPID:
			return "XAER_DUPID";
		case XAException.XAER_OUTSIDE:
			return "XAER_OUTSIDE";
		default:
			return "XA_" + errorCode;
		}
	}

	private final IuXid xid;
	private final Instant expires;
	private final ScheduledFuture<?> timedRollback;

	private final Queue<IuTransaction> branches = new ConcurrentLinkedQueue<>();
	private final Deque<Synchronization> synchronizations = new ConcurrentLinkedDeque<>();
	private final Deque<Synchronization> interposedSynchronizations = new ConcurrentLinkedDeque<>();
	private final Deque<XAResource> resources = new ConcurrentLinkedDeque<>();
	private final Queue<SuspendedResource> suspendedResources = new ConcurrentLinkedQueue<>();

	private final Map<Object, Object> attributes = new ConcurrentHashMap<>();

	private volatile int status = Status.STATUS_ACTIVE;
	private volatile RollbackException rollbackOnly;

	/**
	 * Creates a new root transaction.
	 * 
	 * @param timeout transaction timeout; unless completed beforehand, the
	 *                transaction will automatically roll back when the timeout
	 *                expires, using the same the context transaction was created
	 *                in.
	 */
	public IuTransaction(Duration timeout) {
		if (timeout.toSeconds() < 1)
			throw new IllegalArgumentException();

		xid = new IuXid();
		expires = Instant.now().plus(timeout);
		timedRollback = scheduleRollback(this);
		TX_VISITOR.accept(this);
		LOG.fine(() -> xid + " begin");
	}

	/**
	 * Creates a new branch transaction.
	 * 
	 * @param parent parent transaction
	 */
	public IuTransaction(IuTransaction parent) {
		if (Duration.between(Instant.now(), parent.expires).toSeconds() < 1)
			throw new IllegalArgumentException();

		xid = new IuXid(parent.xid);
		expires = parent.expires;
		timedRollback = scheduleRollback(this);
		parent.branches.offer(this);
		TX_VISITOR.accept(this);
		LOG.fine(() -> xid + " branch " + parent.xid);
	}

	@Override
	public IuXid getTransactionKey() {
		return xid;
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public int getTransactionStatus() {
		return status;
	}

	@Override
	public boolean getRollbackOnly() {
		return rollbackOnly != null;
	}

	@Override
	public Object getResource(Object key) {
		assertStatus(Status.STATUS_ACTIVE);
		return attributes.get(key);
	}

	@Override
	public void putResource(Object key, Object value) {
		assertStatus(Status.STATUS_ACTIVE);
		if (value == null)
			attributes.remove(key);
		else
			attributes.put(key, value);
	}

	@Override
	public synchronized void registerSynchronization(Synchronization s) throws RollbackException {
		checkExpired();
		checkForRollback();
		assertStatus(Status.STATUS_ACTIVE, Status.STATUS_UNKNOWN);
		synchronizations.push(s);
	}

	@Override
	public synchronized void registerInterposedSynchronization(Synchronization s) {
		checkExpired();
		IuException.unchecked(this::checkForRollback);
		assertStatus(Status.STATUS_ACTIVE, Status.STATUS_UNKNOWN);
		interposedSynchronizations.push(s);
	}

	@Override
	public synchronized boolean enlistResource(XAResource res) throws RollbackException {
		checkExpired();
		checkForRollback();
		assertStatus(Status.STATUS_ACTIVE, Status.STATUS_UNKNOWN);

		for (final var r : resources)
			if (IuException.unchecked(() -> r.isSameRM(res)))
				return false;

		IuException.unchecked(() -> doEnlist(res));
		return true;
	}

	@Override
	public synchronized boolean delistResource(XAResource res, int flag) {
		checkExpired();
		assertStatus(Status.STATUS_ACTIVE, Status.STATUS_UNKNOWN, Status.STATUS_MARKED_ROLLBACK);

		return IuException.unchecked(() -> {
			final var suspendedResourceIterator = suspendedResources.iterator();
			while (suspendedResourceIterator.hasNext()) {
				final var suspendedResource = suspendedResourceIterator.next();
				if (suspendedResource.resource.isSameRM(res) //
						&& suspendedResource.flags == XAResource.TMRESUME) {
					suspendedResourceIterator.remove();
					res.end(suspendedResource.xid, flag);
					traceResource("delist-suspended", res);
					return true;
				}
			}

			final var i = resources.iterator();
			while (i.hasNext()) {
				final var resource = i.next();
				if (resource.isSameRM(res)) {
					i.remove();
					resource.end(xid, flag);
					traceResource("delist", res);
					return true;
				}
			}

			return false;
		});

	}

	/**
	 * Joins two transaction branches.
	 * 
	 * @param branch {@link IuTransaction}; <em>must</em> have the same
	 *               {@link Xid#getGlobalTransactionId()} as {@code this},
	 *               <em>should</em> be a direct branch of {@code this}.
	 */
	public synchronized void join(IuTransaction branch) {
		if (!Arrays.equals(xid.getGlobalTransactionId(), branch.xid.getGlobalTransactionId()))
			throw new IllegalArgumentException();

		assertStatus(Status.STATUS_ACTIVE, Status.STATUS_UNKNOWN);
		synchronized (branch) {
			branch.assertStatus(Status.STATUS_ACTIVE, Status.STATUS_UNKNOWN);

			while (!branch.suspendedResources.isEmpty()) {
				final var suspendedResource = branch.suspendedResources.poll();
				final var resource = suspendedResource.resource;
				final var xid = suspendedResource.xid;
				if (suspendedResource.flags == XAResource.TMRESUME)
					IuException.unchecked(() -> resource.end(xid, XAResource.TMSUCCESS));
				suspendedResources.offer(new SuspendedResource(xid, XAResource.TMJOIN, resource));
			}

			while (!branch.resources.isEmpty()) {
				final var resource = branch.resources.poll();
				IuException.unchecked(() -> {
					resource.end(branch.xid, XAResource.TMSUCCESS);
					suspendedResources.offer(new SuspendedResource(branch.xid, XAResource.TMJOIN, resource));
				});
			}

			branch.timedRollback.cancel(false);
			branch.updateStatus(Status.STATUS_NO_TRANSACTION);

			while (!branch.synchronizations.isEmpty()) {
				final var s = branch.synchronizations.pop();
				s.afterCompletion(Status.STATUS_NO_TRANSACTION);
				synchronizations.push(s);
			}

			while (!branch.interposedSynchronizations.isEmpty()) {
				final var s = branch.interposedSynchronizations.pop();
				s.afterCompletion(Status.STATUS_NO_TRANSACTION);
				interposedSynchronizations.push(s);
			}

			branch.notifyAll();
		}

		if (status == Status.STATUS_ACTIVE)
			IuException.unchecked(this::enlistSuspendedResources);

		this.notifyAll();
	}

	/**
	 * Suspends this transaction if active on the current thread.
	 * 
	 * <p>
	 * Suspend puts the transaction into a state where it can be passed off to
	 * another thread or {@link ModuleLayer}, potentially in a remote VM if all
	 * resources have been {@link #delistResource(XAResource, int) delisted}, and/or
	 * will be {@link #enlistResource(XAResource) enlisted} on the remote VM prior
	 * to {@link #resume()}. Same-{@link ModuleLayer} transactions may be suspended
	 * and resumed with the same active resources.
	 * </p>
	 */
	public synchronized void suspend() {
		assertStatus(Status.STATUS_ACTIVE);

		while (!resources.isEmpty())
			IuException.unchecked(() -> {
				final var resource = resources.pop();
				resource.end(xid, XAResource.TMSUSPEND);
				suspendedResources.offer(new SuspendedResource(xid, XAResource.TMRESUME, resource));
				traceResource("suspend", resource);
			});

		updateStatus(Status.STATUS_UNKNOWN);
		LOG.fine(() -> xid + " suspend");
	}

	/**
	 * Resumes a {@link #suspend() suspended} transaction.
	 */
	public synchronized void resume() {
		assertStatus(Status.STATUS_UNKNOWN);

		IuException.unchecked(this::enlistSuspendedResources);

		updateStatus(Status.STATUS_ACTIVE);
		LOG.fine(() -> xid + " resume");
		this.notifyAll();
	}

	@Override
	public void commit() throws RollbackException, HeuristicRollbackException, HeuristicMixedException {
		checkExpired();

		final Deque<IuTransaction> toCommit = new ArrayDeque<>();
		final Deque<IuTransaction> toCheck = new ArrayDeque<>();
		for (final var b : branches)
			toCheck.push(b);

		while (!toCheck.isEmpty()) {
			final var t = toCheck.pop();
			toCommit.push(t);
			for (final var b : t.branches)
				toCheck.push(b);
		}

		Throwable error = null;
		boolean branchCommitted = false;
		boolean branchRolledback = false;
		while (!toCommit.isEmpty()) {
			final var t = toCommit.pop();

			if (error == null && !t.getRollbackOnly())
				error = IuException.suppress(error, () -> {
					IuObject.waitFor(t, () -> t.isIdle(), expires);
					checkExpired(); // ensure timeout wins the race
				});

			switch (t.status) {
			case Status.STATUS_COMMITTED:
				branchCommitted = true;
				break;

			case Status.STATUS_ROLLEDBACK:
				branchRolledback = true;
				break;

			case Status.STATUS_NO_TRANSACTION:
				break;

			default: // STATUS_UNKNOWN
				if (error == null) {
					error = IuException.suppress(error, t::doCommit);
					if (error != null)
						branchRolledback = true;
					else
						branchCommitted = true;
				} else {
					error = IuException.suppress(error, () -> t.continueRollback(new RollbackContinuation()));
					branchRolledback = true;
				}
				break;
			}
		}

		if (branchRolledback) {
			final Exception heuristic;
			if (branchCommitted)
				heuristic = new HeuristicMixedException();
			else
				heuristic = new HeuristicRollbackException();

			if (error == null)
				error = heuristic;
			else
				error.addSuppressed(heuristic);
		}

		if (error == null)
			error = IuException.suppress(error, this::doCommit);
		else
			error = IuException.suppress(error, () -> this.continueRollback(new RollbackContinuation()));

		if (error != null)
			throw IuException.checked(error, RollbackException.class, HeuristicRollbackException.class,
					HeuristicMixedException.class);
	}

	@Override
	public void rollback() {
		final Deque<IuTransaction> toRollback = new ArrayDeque<>();
		final Deque<IuTransaction> toCheck = new ArrayDeque<>(branches);
		while (!toCheck.isEmpty()) {
			final var t = toCheck.pop();
			toRollback.push(t);
			for (final var b : t.branches)
				toCheck.push(b);
		}

		Throwable error = null;
		boolean branchCommitted = false;
		boolean branchRolledback = false;
		while (!toRollback.isEmpty()) {
			final var t = toRollback.pop();
			switch (t.status) {
			case Status.STATUS_COMMITTED:
				branchCommitted = true;
				break;

			case Status.STATUS_ROLLEDBACK:
				branchRolledback = true;
				break;

			case Status.STATUS_NO_TRANSACTION:
				break;

			default:
				error = IuException.suppress(error, () -> t.continueRollback(new RollbackContinuation()));
				branchRolledback = true;
				break;
			}
		}

		if (branchCommitted) {
			final Exception heuristic;
			if (branchRolledback)
				heuristic = new HeuristicMixedException();
			else
				heuristic = new HeuristicCommitException();
			if (error == null)
				error = heuristic;
			else
				error.addSuppressed(heuristic);
		}

		error = IuException.suppress(error, () -> this.continueRollback(new RollbackContinuation()));

		if (error != null)
			throw IuException.unchecked(error);
	}

	@Override
	public synchronized void setRollbackOnly() {
		assertStatus(Status.STATUS_ACTIVE, Status.STATUS_MARKED_ROLLBACK, Status.STATUS_PREPARING,
				Status.STATUS_PREPARED, Status.STATUS_COMMITTING, Status.STATUS_ROLLING_BACK);

		RollbackException rb = new RollbackException();
		if (rollbackOnly != null)
			rb.initCause(rollbackOnly);
		rollbackOnly = rb;
		if (!updateStatus(Status.STATUS_MARKED_ROLLBACK))
			return;

		LOG.fine(() -> xid + " rollback-only");
		this.notifyAll();
	}

	private void checkExpired() {
		if (expires.getEpochSecond() - Instant.now().getEpochSecond() < 1)
			throw new IllegalStateException(new TimeoutException(xid + " expired"));
	}

	private boolean updateStatus(int status) {
		if (this.status == status)
			return false;

		this.status = status;

		LOG.finer(() -> xid + ":" + describeStatus(status));
		return true;
	}

	private void traceResource(String method, XAResource res) {
		LOG.finer(() -> xid + ":XA " + method + " " + res);
	}

	private void traceCommit(Supplier<String> message) {
		LOG.finest(() -> xid + ":COMMIT " + message.get());
	}

	private void traceRollback(Supplier<String> message) {
		LOG.finest(() -> xid + ":ROLLBACK " + message.get());
	}

	private boolean isIdle() {
		return status == Status.STATUS_COMMITTED //
				|| status == Status.STATUS_ROLLEDBACK //
				|| status == Status.STATUS_NO_TRANSACTION //
				|| status == Status.STATUS_UNKNOWN;
	}

	private void assertStatus(int... status) {
		for (int s : status)
			if (this.status == s)
				return;

		StringBuilder msg = new StringBuilder("Status is ");
		msg.append(describeStatus(this.status));
		msg.append(" expected one of");
		for (int s : status) {
			msg.append(" ");
			msg.append(describeStatus(s));
		}

		throw new IllegalStateException(msg.toString());
	}

	private RollbackException createRollback() {
		final var rollback = new RollbackException(describeStatus(status));
		if (rollbackOnly != null)
			rollback.initCause(rollbackOnly);
		return rollback;
	}

	private void checkForRollback() throws RollbackException {
		if (status == Status.STATUS_MARKED_ROLLBACK || status == Status.STATUS_ROLLEDBACK)
			throw createRollback();
	}

	private synchronized void doEnlist(XAResource res) throws XAException {
		final var error = IuException.suppress(null,
				() -> res.setTransactionTimeout((int) Duration.between(Instant.now(), expires).toSeconds()));
		try {
			res.start(xid, XAResource.TMNOFLAGS);
			if (status == Status.STATUS_ACTIVE) {
				resources.push(res);
				traceResource("enlist", res);
			} else {
				res.end(xid, XAResource.TMSUSPEND);
				suspendedResources.offer(new SuspendedResource(xid, XAResource.TMRESUME, res));
				traceResource("enlist-suspended", res);
			}

			if (error != null)
				LOG.log(Level.INFO, error, () -> xid + " setTransactionTimeout() failure");

		} catch (Throwable e) {
			if (error != null)
				e.addSuppressed(error);
			throw e;
		}
	}

	private synchronized void enlistSuspendedResources() throws XAException {
		Queue<SuspendedResource> toJoin = new ArrayDeque<>();
		while (!suspendedResources.isEmpty()) {
			final var suspendedResource = suspendedResources.poll();

			final var flags = suspendedResource.flags;
			if (flags == XAResource.TMJOIN) {
				// unreachable assertions:
				// !suspendedResource.xid.equals(xid)
				// suspendedResource.xid.gtid = xid.gtid
				toJoin.add(suspendedResource);
				continue;
			} // unreachable else
				// assert suspendedResource.xid == xid : suspendedResource.xid + " " + xid;

			// unreachable assertion: flags == XAResource.TMRESUME
			final var resource = suspendedResource.resource;
			resource.start(xid, flags);
			resources.offer(resource);
		}

		while (!toJoin.isEmpty()) {
			final var suspendedResource = toJoin.poll();
			enlistAndJoin(suspendedResource.resource, suspendedResource.xid);
		}
	}

	private synchronized void enlistAndJoin(final XAResource resource, final Xid xid) throws XAException {
		XAResource sameRM = null;
		for (final var r : resources)
			if (r.isSameRM(resource)) {
				sameRM = r;
				break;
			}

		if (sameRM == null) {
			resource.setTransactionTimeout((int) Duration.between(Instant.now(), expires).toSeconds());
			resource.start(this.xid, XAResource.TMNOFLAGS);
			resources.offer(resource);
			sameRM = resource;
		}

		sameRM.start(xid, XAResource.TMJOIN);
	}

	private synchronized void doCommit() throws RollbackException {
		timedRollback.cancel(false);
		try {
			if (this.status == Status.STATUS_MARKED_ROLLBACK) {
				final var rollback = createRollback();
				IuException.suppress(rollback, this::rollback);
				IuException.suppress(rollback, () -> assertStatus(Status.STATUS_ROLLEDBACK));
				throw rollback;
			}

			checkExpired();
			checkForRollback();
			assertStatus(Status.STATUS_ACTIVE, Status.STATUS_UNKNOWN);

			traceCommit(() -> "begin");
			final var rollbackContinuation = new RollbackContinuation();
			try {
				traceCommit(() -> "before-synch");
				while (!synchronizations.isEmpty()) {
					final var s = synchronizations.pop();
					rollbackContinuation.synchronizations.push(s);
					s.beforeCompletion();
				}

				traceCommit(() -> "before-interposed-synch");
				while (!interposedSynchronizations.isEmpty()) {
					final var s = interposedSynchronizations.pop();
					rollbackContinuation.interposedSynchronizations.push(s);
					s.beforeCompletion();
				}

				traceCommit(() -> "delist-suspended-resources");
				while (!suspendedResources.isEmpty()) {
					final var suspendedResource = suspendedResources.poll();
					final var xid = suspendedResource.xid;
					final var flags = suspendedResource.flags;
					final var resource = suspendedResource.resource;
					if (flags == XAResource.TMRESUME) {
						resource.end(xid, XAResource.TMSUCCESS);
						rollbackContinuation.delisted.offer(resource);
					} else // if (flags == XAResource.TMJOIN), else unreachable
						enlistAndJoin(resource, xid);
				}

				traceCommit(() -> "delist-resources");
				while (!resources.isEmpty()) {
					final var res = resources.pop();
					rollbackContinuation.delisted.offer(res);
					res.end(xid, XAResource.TMSUCCESS);
					traceResource("delist:TMSUCCESS", res);
				}

				traceCommit(() -> "commit-phase-1");
				updateStatus(Status.STATUS_PREPARING);
				while (!rollbackContinuation.delisted.isEmpty()) {
					final var resource = rollbackContinuation.delisted.poll();
					final var status = resource.prepare(xid);
					if (status == XAResource.XA_OK)
						rollbackContinuation.prepared.offer(resource);
					traceResource("prepare:commit:" + status, resource);
				}
				updateStatus(Status.STATUS_PREPARED);

				traceCommit(() -> "commit-phase-2");
				updateStatus(Status.STATUS_COMMITTING);
				while (!rollbackContinuation.prepared.isEmpty()) {
					final var resource = rollbackContinuation.prepared.poll();
					out: while (Instant.now().isBefore(expires))
						try {
							resource.commit(xid, false);
							traceResource("commit:" + describeXAErrorCode(XAResource.XA_OK), resource);
							break;
						} catch (XAException e) {
							traceResource("commit:" + describeXAErrorCode(e.errorCode), resource);

							switch (e.errorCode) {
							case XAException.XA_RETRY:
								wait((Duration.between(Instant.now(), expires).toSeconds() / 2) + 1);
								continue;

							case XAException.XA_HEURCOM:
								try {
									resource.forget(xid);
								} catch (Throwable e2) {
									e2.addSuppressed(e);
									throw e2;
								}
								break out;

							case XAException.XA_HEURHAZ:
							case XAException.XA_HEURMIX:
							case XAException.XA_HEURRB:
								IuException.suppress(e, () -> resource.forget(xid));

							default:
								throw e;
							}
						}
					traceResource("commit", resource);
				}
				updateStatus(Status.STATUS_COMMITTED);

			} catch (Throwable e) {
				final var rollback = createRollback();
				rollback.initCause(e);
				IuException.suppress(rollback, () -> continueRollback(rollbackContinuation));
				IuException.suppress(rollback, () -> assertStatus(Status.STATUS_ROLLEDBACK));
				throw rollback;
			}

			traceCommit(() -> "after-interposed-synch");
			while (!rollbackContinuation.interposedSynchronizations.isEmpty())
				rollbackContinuation.interposedSynchronizations.pop().afterCompletion(Status.STATUS_COMMITTED);

			traceCommit(() -> "after-synch");
			while (!rollbackContinuation.synchronizations.isEmpty())
				rollbackContinuation.synchronizations.pop().afterCompletion(Status.STATUS_COMMITTED);

			traceCommit(() -> "complete");
			LOG.fine(() -> xid + " commit");

		} finally {
			this.notifyAll();
		}
	}

	private synchronized void continueRollback(RollbackContinuation rollbackContinuation) {
		timedRollback.cancel(false);
		try {
			Throwable error = IuException.suppress(null,
					() -> assertStatus(Status.STATUS_ACTIVE, Status.STATUS_UNKNOWN, Status.STATUS_PREPARING,
							Status.STATUS_PREPARED, Status.STATUS_MARKED_ROLLBACK, Status.STATUS_COMMITTING,
							Status.STATUS_ROLLING_BACK));

			updateStatus(Status.STATUS_MARKED_ROLLBACK);

			traceRollback(() -> "before-synch");
			while (!synchronizations.isEmpty())
				rollbackContinuation.synchronizations.offer(synchronizations.poll());

			traceRollback(() -> "before-interposed-synch");
			while (!interposedSynchronizations.isEmpty())
				rollbackContinuation.interposedSynchronizations.offer(interposedSynchronizations.poll());

			traceRollback(() -> "delist-suspended-resources");
			while (!suspendedResources.isEmpty()) {
				final var suspendedResource = suspendedResources.poll();
				final var xid = suspendedResource.xid;
				final var flags = suspendedResource.flags;
				final var resource = suspendedResource.resource;
				if (flags == XAResource.TMRESUME) {
					error = IuException.suppress(error, () -> resource.end(xid, XAResource.TMFAIL));
					rollbackContinuation.delisted.offer(resource);
				} else // if (flags == XAResource.TMJOIN), else unreachable
					error = IuException.suppress(error, () -> enlistAndJoin(resource, xid));
			}

			traceRollback(() -> "delist-resources");
			while (!resources.isEmpty()) {
				final var res = resources.pop();
				error = IuException.suppress(error, () -> res.end(xid, XAResource.TMFAIL));
				rollbackContinuation.delisted.offer(res);
				traceResource("delist:TMFAIL", res);
			}

			traceRollback(() -> "rollback-phase-1");
			while (!rollbackContinuation.delisted.isEmpty())
				rollbackContinuation.prepared.offer(rollbackContinuation.delisted.poll());

			traceRollback(() -> "rollback-phase-2");
			updateStatus(Status.STATUS_ROLLING_BACK);
			while (!rollbackContinuation.prepared.isEmpty()) {
				final var res = rollbackContinuation.prepared.poll();
				error = IuException.suppress(error, () -> res.rollback(xid));
				traceResource("rollback", res);
			}
			updateStatus(Status.STATUS_ROLLEDBACK);

			traceRollback(() -> "after-interposed-synch");
			while (!rollbackContinuation.interposedSynchronizations.isEmpty()) {
				final var s = rollbackContinuation.interposedSynchronizations.pop();
				error = IuException.suppress(error, () -> s.afterCompletion(Status.STATUS_ROLLEDBACK));
				traceRollback(() -> "after " + s);
			}

			traceRollback(() -> "after-synch");
			while (!rollbackContinuation.synchronizations.isEmpty()) {
				final var s = rollbackContinuation.synchronizations.pop();
				error = IuException.suppress(error, () -> s.afterCompletion(Status.STATUS_ROLLEDBACK));
				traceRollback(() -> "after " + s);
			}

			LOG.fine(() -> xid + " rollback");

			if (error != null)
				throw IuException.unchecked(error);
		} finally {
			this.notifyAll();
		}
	}

}
