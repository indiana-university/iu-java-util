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
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import edu.iu.IuException;
import edu.iu.IuUtilityTaskController;
import edu.iu.UnsafeFunction;
import edu.iu.UnsafeSupplier;

/**
 * Abstract generic database connection pool.
 * 
 * @param <F> factory data source type
 */
public abstract class IuCommonDataSource<F extends CommonDataSource> implements CommonDataSource {

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

	private String validationQuery;
	private Duration validationInterval = Duration.ofSeconds(15L);
	private UnsafeFunction<Connection, Connection> connectionInitializer;

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

		while (!reusableConnections.isEmpty()) {
			final var reusableConnection = reusableConnections.poll();

			final var count = reusableConnection.transactionSegmentCount();
			if (count >= maxConnectionReuseCount) {
				LOG.fine(() -> "jdbc-pool-retire-count:" + count + " >= " + maxConnectionReuseCount + " "
						+ reusableConnection);
				reusableConnection.close();
				continue;
			}

			final var timeSinceInit = Duration.between(reusableConnection.connectionInitiated(), Instant.now());
			if (timeSinceInit.compareTo(maxConnectionReuseTime) >= 0) {
				LOG.fine(() -> "jdbc-pool-retire-timeout:" + timeSinceInit + " >= " + maxConnectionReuseTime + " "
						+ reusableConnection);
				reusableConnection.close();
				continue;
			}

			LOG.fine(() -> "jdbc-pool-reuse:" + count + ':' + timeSinceInit + ":" + reusableConnection + "; " + this);
			iuPooledConnection = reusableConnection;
		}

		if (iuPooledConnection != null) {
			// TODO: check for max size
			// TODO: prune open connections once max size is reached

			final var newConnection = openConnection();

			openConnections.offer(newConnection);
			iuPooledConnection = newConnection;
		}

		// TODO: validate connection, if validation timeout is expired

		return iuPooledConnection;
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
	 * @return the maxRetry
	 */
	public int getMaxRetry() {
		return maxRetry;
	}

	/**
	 * @param maxRetry the maxRetry to set
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

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" //
				+ "loginTimeout=" + getLoginTimeout() //
				+ ", url=" + getUrl() //
				+ ", username=" + getUsername() //
				+ ", schema=" + getSchema() //
				+ ", maxSize=" + getMaxSize() //
				+ ", maxRetry=" + getMaxRetry() //
				+ ", maxConnectionReuseCount=" + getMaxConnectionReuseCount() //
				+ ", maxConnectionReuseTime=" + getMaxConnectionReuseTime() //
				+ ", abandonedConnectionTimeout=" + getAbandonedConnectionTimeout() //
				+ ", validationQuery=" + getValidationQuery() //
				+ ", validationInterval=" + getValidationInterval() //
				+ "]";
	}

	private IuPooledConnection openConnection() throws SQLException {
		final var initTime = Instant.now();
		final var pooledConnection = IuException.checked(SQLException.class, () -> {
			try {
				return IuUtilityTaskController.getBefore(factory, initTime.plusSeconds(getLoginTimeout()));
			} catch (TimeoutException e) {
				throw new SQLException(e);
			}
		});

		final var iuPooledConnection = new IuPooledConnection(initTime, pooledConnection, connectionInitializer,
				abandonedConnectionTimeout, this::handleClose);

		LOG.fine(() -> "jdbc-pool-open:" + Duration.between(initTime, Instant.now()) + ":" + pooledConnection + "; "
				+ this);

		return iuPooledConnection;
	}

	private void handleClose(IuPooledConnection closedConnection) {
		openConnections.remove(closedConnection);

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
