package edu.iu.jdbc.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEvent;
import javax.sql.StatementEventListener;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.UnsafeFunction;

/**
 * Generic {@link PooledConnection} implementation.
 */
public class IuPooledConnection implements PooledConnection, ConnectionEventListener, StatementEventListener {

	private static final Logger LOG = Logger.getLogger(IuPooledConnection.class.getName());

	private static final ClassLoader JAVA_SQL_LOADER = Connection.class.getClassLoader();
	private static final Class<?>[] CONNECTION_PROXY_INTERFACES = new Class<?>[] { Connection.class };
	private static final ScheduledThreadPoolExecutor REAPER_SCHEDULER;

	static {
		final var threadGroup = new ThreadGroup("iu-java-jdbc-pool-reaper");
		final var threadFactory = new ThreadFactory() {
			private int num;

			@Override
			public Thread newThread(Runnable r) {
				final var thread = new Thread(threadGroup, r, "iu-java-jdbc-pool-reaper/" + (++num));
				thread.setDaemon(true);
				return thread;
			}
		};
		REAPER_SCHEDULER = new ScheduledThreadPoolExecutor(4, threadFactory);
	}

	/**
	 * Hash key for {@link PreparedStatement} initialization args.
	 */
	static class StatementKey {
		private final Class<? extends PreparedStatement> type;
		private final Object[] args;

		/**
		 * Constructor.
		 * 
		 * @param type {@link PreparedStatement} or {@link CallableStatement}
		 * @param args arguments to be passed to
		 *             {@link Method#invoke(Object, Object...)} after a cache miss.
		 */
		StatementKey(Class<? extends PreparedStatement> type, Object[] args) {
			this.type = type;
			this.args = args;
		}

		@Override
		public int hashCode() {
			return IuObject.hashCode(type, args);
		}

		@Override
		public boolean equals(Object obj) {
			if (!IuObject.typeCheck(this, obj))
				return false;
			StatementKey other = (StatementKey) obj;
			return type == other.type && IuObject.equals(args, other.args);
		}
	}

	private class ConnectionHandler implements InvocationHandler {

		private final Connection delegate;

		private ConnectionHandler(Connection delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			final var returnType = method.getReturnType();

			final StatementKey statementKey;
			if (PreparedStatement.class.isAssignableFrom(returnType)) {
				statementKey = new StatementKey(returnType.asSubclass(PreparedStatement.class), args);

				synchronized (reusableStatements) {
					Queue<PreparedStatement> cachedStatements = reusableStatements.get(statementKey);
					if (cachedStatements != null) {
						final var cachedStatement = cachedStatements.poll();
						if (cachedStatements.isEmpty())
							reusableStatements.remove(statementKey);

						handleStatementReused(cachedStatement);
						return cachedStatement;
					}
				}

			} else
				statementKey = null;

			final var rv = IuException.checkedInvocation(() -> method.invoke(delegate, args));

			if (statementKey != null) {
				final var statement = (PreparedStatement) rv;
				handleStatementOpened(statement);

				synchronized (statementReverseIndex) {
					statementReverseIndex.put(statement, statementKey);
				}
			}

			return rv;
		}
	}

	private final Queue<ConnectionEventListener> connectionEventListeners = new ConcurrentLinkedQueue<>();
	private final Queue<StatementEventListener> statementEventListeners = new ConcurrentLinkedQueue<>();
	private final Map<StatementKey, Queue<PreparedStatement>> reusableStatements = new HashMap<>();
	private final Map<PreparedStatement, StatementKey> statementReverseIndex = new WeakHashMap<>();
	private final ConnectionEvent connectionClosedEvent = new ConnectionEvent(this);

	private final Instant connectionInitiated;
	private final Instant connectionOpened = Instant.now();
	private final PooledConnection physicalConnection;
	private final UnsafeFunction<Connection, Connection> connectionInitializer;
	private final Duration abandonedConnectionTimeout;
	private final Consumer<IuPooledConnection> onClose;

	private volatile boolean validated;
	private volatile Connection connection;
	private volatile Instant logicalConnectionOpened;
	private volatile ScheduledFuture<?> reaper;
	private volatile Throwable error;
	private volatile boolean closed;

	private volatile Instant lastTransactionSegmentStarted;
	private volatile Instant lastTransactionSegmentEnded;
	private volatile Duration averageTransactionSegmentDuration;
	private volatile Duration maxTransactionSegmentDuration;
	private volatile long transactionSegmentCount;

	/**
	 * Constructor.
	 * 
	 * @param initTime                   {@link Instant} the physical connection was
	 *                                   initiated
	 * @param abandonedConnectionTimeout {@link Duration} after
	 *                                   {@link #getConnection()} is invoked when
	 *                                   the connection will be considered abandoned
	 *                                   and removed from the pool
	 * @param physicalConnection         {@link PooledConnection} downstream
	 *                                   physical connection
	 * @param connectionInitializer      From
	 *                                   {@link IuCommonDataSource#setConnectionInitializer(UnsafeFunction)}
	 * @param onClose                    receives a handle to this pooled connection
	 *                                   decorator when {@link #close()} is invoked
	 */
	public IuPooledConnection(Instant initTime, PooledConnection physicalConnection,
			UnsafeFunction<Connection, Connection> connectionInitializer, Duration abandonedConnectionTimeout,
			Consumer<IuPooledConnection> onClose) {
		this.connectionInitiated = initTime;
		this.physicalConnection = physicalConnection;
		this.connectionInitializer = connectionInitializer;
		this.abandonedConnectionTimeout = abandonedConnectionTimeout;
		this.onClose = onClose;

		physicalConnection.addConnectionEventListener(this);
		physicalConnection.addStatementEventListener(this);
	}

	@Override
	public synchronized Connection getConnection() throws SQLException {
		if (closed) {
			final var closedError = new IllegalStateException("closed");
			if (error != null)
				closedError.initCause(error);
			throw closedError;
		}

		if (connection != null)
			if (validated) {
				validated = false;
				return connection;
			} else
				throw new IllegalStateException("already connected");

		final var newConnection = physicalConnection.getConnection();
		LOG.finer(() -> "jdbc-pool-logical-open:" + newConnection + "; " + this);

		final var openTrace = new Throwable("opened by");
		reaper = REAPER_SCHEDULER.schedule(() -> {
			try {
				afterLogicalClose();
				close();
				LOG.log(Level.INFO, openTrace, () -> "jdbc-pool-reaper-close:" + newConnection);
			} catch (Throwable e) {
				LOG.log(Level.WARNING, e, () -> "jdbc-pool-reaper-fail:" + newConnection);
			}
		}, abandonedConnectionTimeout.toMillis(), TimeUnit.MILLISECONDS);

		if (connectionInitializer != null) {
			final var initializedConnection = IuException.checked(SQLException.class, newConnection,
					connectionInitializer);
			if (newConnection != initializedConnection.unwrap(Connection.class))
				throw new SQLException(
						"Invalid connection initializer; unwrap(Connection.class) must return original connection");
			connection = initializedConnection;
		} else
			connection = newConnection;

		logicalConnectionOpened = Instant.now();

		return (Connection) Proxy.newProxyInstance(JAVA_SQL_LOADER, CONNECTION_PROXY_INTERFACES,
				new ConnectionHandler(connection));
	}

	@Override
	public void addConnectionEventListener(ConnectionEventListener listener) {
		connectionEventListeners.add(listener);
	}

	@Override
	public void connectionClosed(ConnectionEvent event) {
		Objects.requireNonNull(connection, "not connected");

		final var connection = this.connection;
		afterLogicalClose();
		LOG.finer(() -> "jdbc-pool-logical-close:" + connection + "; " + this);

		connectionEventListeners.parallelStream().forEach(a -> a.connectionClosed(connectionClosedEvent));
	}

	@Override
	public void connectionErrorOccurred(ConnectionEvent event) {
		final var error = event.getSQLException();
		if (connection != null) {
			final var connection = this.connection;
			afterLogicalClose();
			LOG.log(Level.INFO, error, () -> "jdbc-pool-logical-close:" + connection + "; " + this);
		}
		afterPhysicalClose(error);

		final var decoratedEvent = new ConnectionEvent(this, error);
		connectionEventListeners.parallelStream().forEach(a -> a.connectionErrorOccurred(decoratedEvent));
	}

	@Override
	public void removeConnectionEventListener(ConnectionEventListener listener) {
		connectionEventListeners.remove(listener);
	}

	@Override
	public void addStatementEventListener(StatementEventListener listener) {
		statementEventListeners.add(listener);
	}

	@Override
	public void statementClosed(StatementEvent event) {
		final var statement = event.getStatement();
		LOG.finer(() -> "jdbc-pool-statement-close:" + connection + ':' + statement + "; " + this);

		final var decoratedEvent = new StatementEvent(this, statement);
		statementEventListeners.parallelStream().forEach(a -> a.statementClosed(decoratedEvent));

		final var statementKey = statementReverseIndex.get(statement);
		if (statementKey != null)
			synchronized (reusableStatements) {
				var cachedStatements = reusableStatements.get(statementKey);
				if (cachedStatements == null)
					reusableStatements.put(statementKey, cachedStatements = new ArrayDeque<>());
				cachedStatements.offer(statement);
			}
	}

	@Override
	public void statementErrorOccurred(StatementEvent event) {
		final var statement = event.getStatement();
		final var error = event.getSQLException();
		LOG.log(Level.INFO, error, () -> "jdbc-pool-statement-error:" + connection + ':' + statement + "; " + this);

		final var decoratedEvent = new StatementEvent(this, statement, error);
		statementEventListeners.parallelStream().forEach(a -> a.statementErrorOccurred(decoratedEvent));

		synchronized (statementReverseIndex) {
			statementReverseIndex.remove(statement);
		}

		synchronized (reusableStatements) {
			final var i = reusableStatements.values().iterator();
			while (i.hasNext()) {
				final var q = i.next();
				if (q.remove(statement) //
						&& q.isEmpty())
					i.remove();
			}
		}
	}

	@Override
	public void removeStatementEventListener(StatementEventListener listener) {
		statementEventListeners.remove(listener);
	}

	@Override
	public synchronized void close() throws SQLException {
		final Throwable closeError;
		if (!closed)
			closeError = IuException.suppress(null, physicalConnection::close);
		else
			closeError = null;

		Throwable error = closeError;
		if (connection != null)
			error = IuException.suppress(error, () -> afterLogicalClose());
		error = IuException.suppress(error, () -> afterPhysicalClose(closeError));

		if (error != null)
			throw IuException.checked(error, SQLException.class);
	}

	@Override
	public String toString() {
		return "IuPooledConnection [" //
				+ "connectionInitiated=" + connectionInitiated //
				+ ", connectionOpened=" + connectionOpened //
				+ ", physicalConnection=" + physicalConnection //
				+ ", connection=" + connection //
				+ ", logicalConnectionOpened=" + logicalConnectionOpened //
				+ ", lastTransactionSegmentStarted=" + lastTransactionSegmentStarted //
				+ ", lastTransactionSegmentEnded=" + lastTransactionSegmentEnded //
				+ ", averageTransactionSegmentDuration=" + averageTransactionSegmentDuration //
				+ ", maxTransactionSegmentDuration=" + maxTransactionSegmentDuration //
				+ ", transactionSegmentCount=" + transactionSegmentCount //
				+ ", closed=" + closed + "]";
	}

	/**
	 * Gets the {@link Instant} the connection factory initiated this physical
	 * connection.
	 * 
	 * @return {@link Instant}
	 */
	public Instant connectionInitiated() {
		return connectionInitiated;
	}

	/**
	 * Gets the {@link Instant} the connection factory opened this physical
	 * connection.
	 * 
	 * @return {@link Instant}
	 */
	public Instant connectionOpened() {
		return connectionOpened;
	}

	/**
	 * Gets the {@link Instant} the logical connection associated with this physical
	 * connection was opened.
	 * 
	 * @return {@link Instant}
	 */
	public Instant logicalConnectionOpened() {
		return logicalConnectionOpened;
	}

	/**
	 * Gets the start {@link Instant} of the last transaction segment completed on
	 * this physical connection.
	 * 
	 * @return {@link Instant}
	 */
	public Instant lastTransactionSegmentStarted() {
		return lastTransactionSegmentStarted;
	}

	/**
	 * Gets the end {@link Instant} of the last transaction segment completed on
	 * this physical connection.
	 * 
	 * @return {@link Instant}
	 */
	public Instant lastTransactionSegmentEnded() {
		return lastTransactionSegmentEnded;
	}

	/**
	 * Gets the average {@link Duration} of the transaction segments completed via
	 * this physical connection.
	 * 
	 * @return {@link Duration}
	 */
	public Duration averageTransactionSegmentDuration() {
		return averageTransactionSegmentDuration;
	}

	/**
	 * Gets the average {@link Duration} of the transaction segments completed via
	 * this physical connection.
	 * 
	 * @return {@link Duration}
	 */
	public Duration maxTransactionSegmentDuration() {
		return maxTransactionSegmentDuration;
	}

	/**
	 * Gets the number of times this connection has been used.
	 * 
	 * @return {@link long}
	 */
	public long transactionSegmentCount() {
		return transactionSegmentCount;
	}

	/**
	 * Gets the error that invalidated this connection, if invalid due to an error.
	 * 
	 * @return {@link SQLException}; null if the connection has not experienced a
	 *         error
	 */
	Throwable error() {
		return error;
	}

	/**
	 * Pre-emptively establishes and validates the logical connection.
	 * 
	 * @param validationQuery SQL to execute on the connection, <em>should</em>
	 *                        produce at least one row with a non-null value in the
	 *                        first column
	 * @throws SQLException if the logical connection cannot be established or the
	 *                      validation query fails
	 */
	synchronized void validate(String validationQuery) throws SQLException {
		final var c = getConnection();

		try (final var s = c.createStatement(); //
				final var r = s.executeQuery(validationQuery)) {
			if (!r.next() || r.getObject(1) == null) {
				final var error = new SQLException(
						"Validation query failed to produce a non-null result: " + validationQuery + "; " + this);
				connectionErrorOccurred(new ConnectionEvent(this, error));
				throw error;
			}
		}

		validated = true;
	}

	private void handleStatementOpened(PreparedStatement statement) {
		LOG.finer(() -> "jdbc-pool-statement-open:" + connection + ':' + statement + "; " + this);
	}

	private void handleStatementReused(PreparedStatement statement) {
		LOG.finer(() -> "jdbc-pool-statement-reuse:" + connection + ':' + statement + "; " + this);
	}

	private synchronized void afterLogicalClose() {
		Objects.requireNonNull(connection);
		if (reaper != null) {
			reaper.cancel(false);
			reaper = null;
		}

		connection = null;

		lastTransactionSegmentStarted = Objects.requireNonNull(logicalConnectionOpened);
		logicalConnectionOpened = null;

		lastTransactionSegmentEnded = Instant.now();
		final var transactionTime = Duration.between(lastTransactionSegmentStarted, lastTransactionSegmentEnded);
		if (maxTransactionSegmentDuration == null || maxTransactionSegmentDuration.compareTo(transactionTime) < 0)
			maxTransactionSegmentDuration = transactionTime;

		if (averageTransactionSegmentDuration == null)
			averageTransactionSegmentDuration = transactionTime;
		else
			averageTransactionSegmentDuration = (averageTransactionSegmentDuration.multipliedBy(transactionSegmentCount)
					.plus(transactionTime)).dividedBy(transactionSegmentCount + 1);

		transactionSegmentCount++;
	}

	private synchronized void afterPhysicalClose(Throwable error) {
		if (!closed) {
			this.error = error;
			onClose.accept(this);
			closed = true;
		} else
			return;
	}

}
