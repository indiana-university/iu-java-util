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
package edu.iu.jdbc.pool;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuUtilityTaskController;

/**
 * Basic database connection pool utility.
 */
public class IuConnectionPool implements ConnectionEventListener, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuConnectionPool.class.getName());

	private final IuPooledConnectionFactory factory;
	private final IuConnectionPoolConfiguration config;

	private final Queue<PooledConnectionHolder> openConnections = new ConcurrentLinkedQueue<>();
	private final Queue<PooledConnectionHolder> reusableConnections = new ConcurrentLinkedQueue<>();
	private final ScheduledThreadPoolExecutor reaperScheduler;

	private volatile boolean closed;
	private volatile int pendingConnections;

	/**
	 * Default constructor.
	 * 
	 * @param factory {@link IuPooledConnectionFactory}
	 * @param config  {@link IuConnecitonPoolConfiguration}
	 */
	public IuConnectionPool(IuPooledConnectionFactory factory, IuConnectionPoolConfiguration config) {
		this.factory = factory;
		this.config = config;

		final var descr = config.getDescription();
		final var threadGroup = new ThreadGroup("iu-java-jdbc-pool-reaper");
		final var threadFactory = new ThreadFactory() {
			private int num;

			@Override
			public Thread newThread(Runnable r) {
				return new Thread(threadGroup, r, "iu-java-jdbc-pool-reaper/" + descr + "/" + (++num));
			}
		};
		reaperScheduler = new ScheduledThreadPoolExecutor(config.getAbandonedConnectionThreads(), threadFactory);
		reaperScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
	}

	/**
	 * Schedules a pooled connection to be reclaimed and closed if checked out for
	 * longer than
	 * {@link IuConnectionPoolConfiguration#getAbandonedConnectionTimeout()}.
	 * 
	 * @param holder open connection holder just prior to return from
	 *               {@link #checkOut()}
	 * @return {@link ScheduledFuture} to be canceled when the connection is passed
	 *         to {@link #reuseOrClose(PooledConnection)}
	 */
	private ScheduledFuture<?> scheduleAbandonedConnectionReaper(PooledConnectionHolder holder) {
		return reaperScheduler.schedule(() -> {
			openConnections.remove(holder);
			final var pooledConnection = holder.pooledConnection;
			final var error = IuException.suppress(null, pooledConnection::close);
			LOG.log(Level.INFO, error, () -> "jdbc-pool-abandoned:" + config.getDescription() + ":" + pooledConnection);
		}, config.getAbandonedConnectionTimeout().getSeconds(), TimeUnit.SECONDS);
	}

	/**
	 * Checks out a {@link PooledConnection}.
	 * 
	 * @return {@link PooledConnection}
	 * @throws SQLException if the connection fails due to a database error
	 */
	public PooledConnection checkOut() throws SQLException {
		PooledConnectionHolder holder = null;

		final var connectBegin = Instant.now();
		final var connectBefore = connectBegin.plus(config.getLoginTimeout());

		final var descr = config.getDescription();
		final var maxSize = config.getMaxSize();
		final var maxRetry = config.getMaxRetry();
		final var maxConnectionReuseTime = config.getMaxConnectionReuseTime();
		final var validationInterval = config.getValidationInterval();

		var attempt = 0;
		Throwable error = null;
		while (!closed //
				&& attempt <= maxRetry //
				&& connectBefore.isAfter(Instant.now()))
			try {
				attempt++;

				synchronized (this) {
					IuObject.waitFor(this, () -> closed //
							|| !reusableConnections.isEmpty() //
							|| openConnections.size() + pendingConnections < maxSize, connectBefore);
					if (closed)
						throw new SQLException("closed");
					else
						pendingConnections++;
				}

				try {
					PooledConnectionHolder reusableConnection;
					while ((reusableConnection = reusableConnections.poll()) != null) {
						final var pooledConnection = reusableConnection.pooledConnection;

						final var timeSinceInit = Duration.between(reusableConnection.initiated, Instant.now());
						if (timeSinceInit.compareTo(maxConnectionReuseTime) >= 0) {
							openConnections.remove(reusableConnection);
							pooledConnection.close();
							LOG.fine(() -> "jdbc-pool-retire-timeout:" + descr + ":" + timeSinceInit + ' '
									+ pooledConnection + ' ' + this);
							continue;
						}

						holder = reusableConnection;
						LOG.finer(() -> "jdbc-pool-reuse:" + descr + ":" + Duration.between(connectBegin, Instant.now())
								+ ":" + timeSinceInit + ' ' + pooledConnection + ' ' + this);
						break;
					}

					if (holder == null) {
						final var initTime = Instant.now();
						final var pooledConnection = IuException.checked(SQLException.class, () -> {
							try {
								return IuUtilityTaskController.getBefore(factory::createPooledConnection,
										connectBefore);
							} catch (TimeoutException e) {
								throw new SQLException(e);
							}
						});

						holder = new PooledConnectionHolder(pooledConnection, initTime);
						openConnections.offer(holder);

						final var connectComplete = Instant.now();
						LOG.fine(() -> "jdbc-pool-open:" + descr + ":" + Duration.between(connectBegin, connectComplete)
								+ ":" + Duration.between(initTime, Instant.now()) + ' ' + pooledConnection + ' '
								+ this);
					}

					final var lastUse = holder.getLastUse();
					if (lastUse == null //
							|| Duration.between(lastUse, Instant.now()).compareTo(validationInterval) >= 0) {
						final var validationQuery = config.getValidationQuery();
						if (validationQuery != null) {
							holder.validate(validationQuery);
							final var pooledConnection = holder.pooledConnection;
							LOG.finer(() -> "jdbc-pool-valid:" + descr + ": " + pooledConnection);
						}
					}

					if (error != null)
						LOG.log(Level.INFO, error, () -> "jdbc-pool-recoverable; " + this);

					holder.usageStarted(scheduleAbandonedConnectionReaper(holder));
					holder.pooledConnection.addConnectionEventListener(this);
					return holder.pooledConnection;

				} finally {
					synchronized (this) {
						pendingConnections--;
						this.notifyAll();
					}
				}

			} catch (Throwable e) {
				if (holder != null) {
					openConnections.remove(holder);
					IuException.suppress(e, holder.pooledConnection::close);
					holder = null;
				}
				error = IuException.suppress(error, e);
			}

		throw new SQLException("jdbc-pool-fail: attempt=" + attempt + ", timeout=" + connectBefore + "; " + this,
				error);
	}

	/**
	 * Indicates that a {@link PooledConnection} is no longer in use by the
	 * application and may potentially be reused.
	 * 
	 * @param pooledConnection {@link PooledConnection} obtained from
	 *                         {@link #checkOut()}
	 * @throws SQLException if an error occurs attempting to close a non-reusable
	 *                      connection
	 */
	protected void reuseOrClose(PooledConnection pooledConnection) throws SQLException {
		pooledConnection.removeConnectionEventListener(this);

		final var holder = openConnections.stream().filter(h -> h.pooledConnection == pooledConnection).findAny()
				.orElse(null);
		if (holder == null) {
			LOG.info(() -> "jdbc-pool-orphan:" + config.getDescription() + ":" + pooledConnection);
			pooledConnection.close();
			return;
		}

		holder.usageComplete();

		final var count = holder.getUsageCount();
		if (closed || //
				count >= config.getMaxConnectionReuseCount()) {
			LOG.fine(() -> "jdbc-pool-retire:" + config.getDescription() + ":" + count + ' ' + pooledConnection);
			openConnections.remove(holder);
			pooledConnection.close();
		} else
			reusableConnections.offer(holder);

		synchronized (this) {
			this.notifyAll();
		}
	}

	@Override
	public void connectionClosed(ConnectionEvent event) {
		IuException.unchecked(() -> reuseOrClose((PooledConnection) event.getSource()));
	}

	@Override
	public void connectionErrorOccurred(ConnectionEvent event) {
		final var pooledConnection = (PooledConnection) event.getSource();
		final var error = event.getSQLException();
		LOG.log(Level.INFO, error, () -> "jdbc-pool-error:" + config.getDescription() + ':' + pooledConnection);
	}

	/**
	 * Waits for completion and closes all open connections.
	 */
	@Override
	public synchronized void close() throws SQLException {
		if (!closed) {
			closed = true;
			reaperScheduler.shutdown();

			class CloseStatus {
				Throwable error = null;
			}
			final var closeStatus = new CloseStatus();

			final var reusableConnectionIterator = reusableConnections.iterator();
			while (reusableConnectionIterator.hasNext()) {
				final var holder = reusableConnectionIterator.next();
				reusableConnectionIterator.remove();
				closeStatus.error = IuException.suppress(closeStatus.error, () -> holder.pooledConnection.close());
				openConnections.remove(holder);
			}

			final var shutdownTimeout = config.getShutdownTimeout();
			closeStatus.error = IuException.suppress(closeStatus.error,
					() -> IuObject.waitFor(this, openConnections::isEmpty, shutdownTimeout));

			final var openConnectionIterator = openConnections.iterator();
			while (openConnectionIterator.hasNext()) {
				final var c = openConnectionIterator.next();
				openConnectionIterator.remove();
				closeStatus.error = IuException.suppress(closeStatus.error, () -> c.pooledConnection.close());
			}

			closeStatus.error = IuException.suppress(closeStatus.error, factory::onShutdown);

			if (closeStatus.error != null)
				throw IuException.checked(closeStatus.error, SQLException.class);
		}
	}

}