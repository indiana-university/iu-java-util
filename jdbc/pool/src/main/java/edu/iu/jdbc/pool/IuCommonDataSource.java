package edu.iu.jdbc.pool;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.sql.CommonDataSource;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuUtilityTaskController;
import edu.iu.UnsafeFunction;
import edu.iu.UnsafeRunnable;
import edu.iu.UnsafeSupplier;

/**
 * Abstract generic database connection pool.
 */
public abstract class IuCommonDataSource implements CommonDataSource, ConnectionEventListener, AutoCloseable {

	static {
		Logger.getLogger(IuCommonDataSource.class.getPackageName());
	}
	private static final Logger LOG = Logger.getLogger(IuCommonDataSource.class.getName());

	private final Queue<IuPooledConnection> openConnections = new ConcurrentLinkedQueue<>();
	private final Queue<IuPooledConnection> reusableConnections = new ConcurrentLinkedQueue<>();
	private final UnsafeSupplier<? extends PooledConnection> factory;

	private int loginTimeout = 15;
	private String url;
	private String username;
	private String schema;
	private int maxSize = 16;
	private int maxRetry = 1;
	private long maxConnectionReuseCount = 100;
	private Duration maxConnectionReuseTime = Duration.ofMinutes(15L);
	private Duration abandonedConnectionTimeout = Duration.ofMinutes(30L);
	private Duration shutdownTimeout = Duration.ofSeconds(30L);

	private String validationQuery;
	private Duration validationInterval = Duration.ofSeconds(15L);
	private UnsafeFunction<Connection, Connection> connectionInitializer;
	private UnsafeRunnable onClose;

	private boolean closed;
	private volatile int pendingConnections;

	/**
	 * Default constructor.
	 * 
	 * @param factory {@link UnsafeSupplier} of downstream {@link PooledConnection}
	 *                instances; each {@link UnsafeSupplier#get()} invocation
	 *                <em>must</em> return a newly established physical database
	 *                connection.
	 */
	protected IuCommonDataSource(UnsafeSupplier<? extends PooledConnection> factory) {
		this.factory = factory;
	}

	/**
	 * Checks out a {@link PooledConnection}.
	 * 
	 * <p>
	 * <strong>Implementation Note:</strong> The upstream {@link DataSource}
	 * implementation should discard this instance once the logical
	 * {@link Connection} view has been obtained. Application code will invoke
	 * {@link Connection#close()} to return the connection to the pool to be reused
	 * or retired. Note that invoking {@link PooledConnection#close()} <em>will</em>
	 * close the physical connection and remove it from the pool. This facilitates
	 * ejecting physical connections by an upstream pool manager.
	 * </p>
	 * 
	 * @return {@link PooledConnection}
	 * @throws SQLException if the connection fails due to a database error
	 */
	public IuPooledConnection getPooledConnection() throws SQLException {
		IuPooledConnection iuPooledConnection = null;
		Instant timeout = Instant.now().plusSeconds(loginTimeout);

		var attempt = 0;
		Throwable error = null;
		while (!closed //
				&& attempt <= maxRetry //
				&& timeout.isAfter(Instant.now()))
			try {
				attempt++;
				
				synchronized (this) {
					IuObject.waitFor(this, () -> closed //
							|| !reusableConnections.isEmpty() //
							|| !this.isExhausted(),
							timeout);

					pendingConnections++;
				}

				try {
					while (!reusableConnections.isEmpty()) {
						final var reusableConnection = reusableConnections.poll();

						final var timeSinceInit = Duration.between(reusableConnection.connectionInitiated(),
								Instant.now());
						if (timeSinceInit.compareTo(maxConnectionReuseTime) >= 0) {
							reusableConnection.close();
							LOG.fine(() -> "jdbc-pool-retire-timeout:" + timeSinceInit + " >= " + maxConnectionReuseTime
									+ " " + reusableConnection);
							continue;
						}

						iuPooledConnection = reusableConnection;
						LOG.finer(() -> "jdbc-pool-reuse; " + reusableConnection + "; " + this);
						break;
					}

					if (iuPooledConnection == null)
						iuPooledConnection = openConnection(timeout);

					final var lastUsed = iuPooledConnection.lastTransactionSegmentEnded();
					if (validationQuery != null //
							&& (lastUsed == null //
									|| Duration.between(lastUsed, Instant.now()).compareTo(validationInterval) >= 0))
						iuPooledConnection.validate(validationQuery);

					if (error != null)
						LOG.log(Level.INFO, error, () -> "jdbc-pool-recoverable; " + this);

					return iuPooledConnection;

				} finally {
					synchronized (this) {
						pendingConnections--;
						this.notifyAll();
					}
				}

			} catch (Throwable e) {
				if (iuPooledConnection != null) {
					IuException.suppress(e, iuPooledConnection::close);
					iuPooledConnection = null;
				}

				if (error == null)
					error = e;
				else
					error.addSuppressed(e);
			}

		throw new SQLException("jdbc-pool-fail: attempt=" + attempt + ", timeout=" + timeout + "; " + this, error);
	}

	@Override
	public void connectionClosed(ConnectionEvent event) {
		final var reusableConnection = (IuPooledConnection) event.getSource();

		final var count = reusableConnection.transactionSegmentCount();
		if (count >= maxConnectionReuseCount) {
			try {
				reusableConnection.close();
				LOG.fine(() -> "jdbc-pool-retire-count:" + count + " >= " + maxConnectionReuseCount + " "
						+ reusableConnection);
			} catch (Throwable e) {
				LOG.log(Level.INFO, e, () -> "jdbc-pool-retire-count:" + count + " >= " + maxConnectionReuseCount + " "
						+ reusableConnection);
			}
			return;
		}

		if (!closed) {
			LOG.finer(() -> "jdbc-pool-reusable; " + reusableConnection);
			reusableConnections.offer(reusableConnection);
			synchronized (this) {
				this.notifyAll();
			}
		}
	}

	@Override
	public void connectionErrorOccurred(ConnectionEvent event) {
		reusableConnections.remove((IuPooledConnection) event.getSource());
	}

	@Override
	public Logger getParentLogger() {
		return LogManager.getLogManager().getLogger(IuCommonDataSource.class.getPackageName());
	}

	@Override
	public PrintWriter getLogWriter() throws SQLException {
		return null;
	}

	@Override
	public void setLogWriter(PrintWriter out) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void setLoginTimeout(int seconds) throws SQLException {
		if (seconds < 0)
			throw new IllegalArgumentException();
		else if (seconds == 0)
			loginTimeout = 15;
		else
			loginTimeout = seconds;
	}

	@Override
	public int getLoginTimeout() {
		return loginTimeout;
	}

	/**
	 * Gets the URL used to initialize the downstream connection factory.
	 * 
	 * @return Full JDBC URL
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Sets the URL used to initialize the downstream connection factory.
	 * 
	 * @param url Full JDBC URL
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * Gets the database username used to initialize the downstream connection
	 * factory.
	 * 
	 * @return Database username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Sets the database username used to initialize the downstream connection
	 * factory.
	 * 
	 * @param username Database username
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Gets the database schema used to initialize the downstream connection
	 * factory.
	 * 
	 * @return Database schema
	 */
	public String getSchema() {
		return schema;
	}

	/**
	 * Sets the database schema used to initialize the downstream connection
	 * factory.
	 * 
	 * @param schema Database schema
	 */
	public void setSchema(String schema) {
		this.schema = schema;
	}

	/**
	 * Gets the maximum number of connections to allow in the pool.
	 * 
	 * @return Pool max size
	 */
	public int getMaxSize() {
		return maxSize;
	}

	/**
	 * Sets the maximum number of connections to allow in the pool.
	 * 
	 * @param maxSize Pool max size
	 */
	public void setMaxSize(int maxSize) {
		this.maxSize = maxSize;
	}

	/**
	 * Gets the maximum number of times a connection attempt will be retried before
	 * resulting in failure.
	 * 
	 * @return maximum number of times a connection attempt will be retried before
	 *         resulting in failure.
	 */
	public int getMaxRetry() {
		return maxRetry;
	}

	/**
	 * Gets the maximum number of times a connection attempt will be retried before
	 * resulting in failure.
	 * 
	 * @param maxRetry maximum number of times a connection attempt will be retried
	 *                 before resulting in failure.
	 */
	public void setMaxRetry(int maxRetry) {
		this.maxRetry = maxRetry;
	}

	/**
	 * Gets the maximum number of times a single connection can be used before
	 * ejecting from the pool.
	 * 
	 * @return Per-connection max reuse count
	 */
	public long getMaxConnectionReuseCount() {
		return maxConnectionReuseCount;
	}

	/**
	 * Sets the maximum number of times a single connection can be used before
	 * ejecting from the pool.
	 * 
	 * @param maxConnectionReuseCount Per-connection max reuse count
	 */
	public void setMaxConnectionReuseCount(long maxConnectionReuseCount) {
		this.maxConnectionReuseCount = maxConnectionReuseCount;
	}

	/**
	 * Gets the maximum length of time a single connection can remain open before
	 * ejecting from the pool.
	 * 
	 * @return Per-connection max reuse time
	 */
	public Duration getMaxConnectionReuseTime() {
		return maxConnectionReuseTime;
	}

	/**
	 * Gets the maximum length of time a single connection can remain open before
	 * ejecting from the pool.
	 * 
	 * @param maxConnectionReuseTime Per-connection max reuse time
	 */
	public void setMaxConnectionReuseTime(Duration maxConnectionReuseTime) {
		this.maxConnectionReuseTime = maxConnectionReuseTime;
	}

	/**
	 * Gets the maximum length of time a connection can be checked out from the pool
	 * before attempting to forcibly close and consider it abandoned.
	 * 
	 * @return Abandoned connection timeout interval
	 */
	public Duration getAbandonedConnectionTimeout() {
		return abandonedConnectionTimeout;
	}

	/**
	 * Sets the maximum length of time a connection can be checked out from the pool
	 * before attempting to forcibly close and consider it abandoned.
	 * 
	 * @param abandonedConnectionTimeout Abandoned connection timeout interval
	 */
	public void setAbandonedConnectionTimeout(Duration abandonedConnectionTimeout) {
		this.abandonedConnectionTimeout = abandonedConnectionTimeout;
	}

	/**
	 * Gets the maximum length of time to wait for all connections to close on
	 * shutdown.
	 * 
	 * @return Maximum length of time to wait for all connections to close
	 *         gracefully
	 */
	public Duration getShutdownTimeout() {
		return shutdownTimeout;
	}

	/**
	 * Sets the maximum length of time to wait for all connections to close on
	 * shutdown.
	 * 
	 * @param shutdownTimeout Maximum length of time to wait for all connections to
	 *                        close gracefully
	 */
	protected void setShutdownTimeout(Duration shutdownTimeout) {
		this.shutdownTimeout = shutdownTimeout;
	}

	/**
	 * Gets the query to use for validating connections on creation, and
	 * intermittently before checking out from the pool.
	 * 
	 * @return SQL select statement, <em>must</em> return a single row with a single
	 *         non-null column; may be null to skip query validation
	 */
	public String getValidationQuery() {
		return validationQuery;
	}

	/**
	 * Sets the query to use for validating connections on creation, and
	 * intermittently before checking out from the pool.
	 * 
	 * @param validationQuery SQL select statement, <em>must</em> return a single
	 *                        row with a single non-null column; may be null to skip
	 *                        query validation
	 */
	public void setValidationQuery(String validationQuery) {
		this.validationQuery = validationQuery;
	}

	/**
	 * Gets the frequency at which to validate connections, when
	 * {@link #getValidationQuery()} returns a non-null value.
	 *
	 * @return Frequency at which to validate connections; may be
	 */
	public Duration getValidationInterval() {
		return validationInterval;
	}

	/**
	 * Sets the frequency at which to validate connections, when
	 * {@link #getValidationQuery()} returns a non-null value.
	 *
	 * @param validationInterval Frequency at which to validate connections; may be
	 */
	public void setValidationInterval(Duration validationInterval) {
		this.validationInterval = validationInterval;
	}

	/**
	 * Sets an optional transform function to be apply directly before checking out
	 * a connection from the pool.
	 * 
	 * @param connectionInitializer {@link UnsafeFunction}: accepts and returns a
	 *                              {@link Connection} such that
	 *                              {@link Connection#unwrap(Class)} invoked on the
	 *                              return value delegates to the {@link Connection}
	 *                              passed as an argument; <em>should not</em> throw
	 *                              checked exceptions other than
	 *                              {@link SQLException}; <em>may</em> throw
	 *                              {@link TimeoutException} or
	 *                              {@link InterruptedException}.
	 */
	public void setConnectionInitializer(UnsafeFunction<Connection, Connection> connectionInitializer) {
		this.connectionInitializer = connectionInitializer;
	}

	/**
	 * Sets an optional shutdown hook to be invoked from {@link #close()} after all
	 * physical connections managed by the pool have been closed.
	 * 
	 * @param onClose {@link UnsafeRunnable}
	 */
	public void setOnClose(UnsafeRunnable onClose) {
		this.onClose = onClose;
	}

	/**
	 * Waits for completion and closes all open connections.
	 */
	@Override
	public synchronized void close() throws SQLException {
		if (!closed) {
			closed = true;

			class CloseStatus {
				Throwable error = null;
			}
			final var closeStatus = new CloseStatus();

			while (!reusableConnections.isEmpty())
				closeStatus.error = IuException.suppress(closeStatus.error, () -> reusableConnections.poll().close());

			IuException.suppress(closeStatus.error, () -> IuObject.waitFor(this, () -> {
				for (final var c : openConnections)
					if (c.logicalConnectionOpened() == null)
						closeStatus.error = IuException.suppress(closeStatus.error, () -> c.close());

				return openConnections.isEmpty();
			}, shutdownTimeout));

			if (onClose != null)
				closeStatus.error = IuException.suppress(closeStatus.error, onClose);

			closeStatus.error = IuException.suppress(closeStatus.error, () -> {
				final var size = openConnections.size();
				if (size > 0)
					throw new SQLException(
							size + " connections remaining in the pool after graceful shutdown " + shutdownTimeout);
			});

			if (closeStatus.error != null)
				throw IuException.checked(closeStatus.error, SQLException.class);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" //
				+ "loginTimeout=" + getLoginTimeout() //
				+ ", closed=" + closed //
				+ ", url=" + getUrl() //
				+ ", username=" + getUsername() //
				+ ", schema=" + getSchema() //
				+ ", available=" + reusableConnections.size() //
				+ ", open=" + openConnections.size() //
				+ ", maxSize=" + getMaxSize() //
				+ ", maxRetry=" + getMaxRetry() //
				+ ", maxConnectionReuseCount=" + getMaxConnectionReuseCount() //
				+ ", maxConnectionReuseTime=" + getMaxConnectionReuseTime() //
				+ ", abandonedConnectionTimeout=" + getAbandonedConnectionTimeout() //
				+ ", validationQuery=" + getValidationQuery() //
				+ ", validationInterval=" + getValidationInterval() //
				+ "]";
	}

	private synchronized boolean isExhausted() {
		return openConnections.size() + pendingConnections >= maxSize;
	}

	private IuPooledConnection openConnection(Instant timeout) throws SQLException {
		if (closed)
			throw new SQLException("closed");

		final var initTime = Instant.now();
		final var pooledConnection = IuException.checked(SQLException.class, () -> {
			try {
				return IuUtilityTaskController.getBefore(factory, timeout);
			} catch (TimeoutException e) {
				throw new SQLException(e);
			}
		});

		final var newConnection = new IuPooledConnection(initTime, pooledConnection, connectionInitializer,
				abandonedConnectionTimeout, this::handleClose);
		newConnection.addConnectionEventListener(this);

		openConnections.offer(newConnection);
		LOG.fine(() -> "jdbc-pool-open:" + Duration.between(initTime, Instant.now()) + ":" + pooledConnection + "; "
				+ this);

		return newConnection;
	}

	private void handleClose(IuPooledConnection closedConnection) {
		openConnections.remove(closedConnection);
		synchronized (this) {
			this.notifyAll();
		}

		final var error = closedConnection.error();
		if (error == null)
			LOG.fine(() -> "jdbc-pool-close:" + Duration.between(closedConnection.connectionInitiated(), Instant.now())
					+ ":" + closedConnection + "; " + this);
		else
			LOG.log(Level.WARNING, error,
					() -> "jdbc-pool-close:" + Duration.between(closedConnection.connectionInitiated(), Instant.now())
							+ ":" + closedConnection + "; " + this);
	}

}
