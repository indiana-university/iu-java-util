package edu.iu.jdbc.pool;

import java.time.Duration;

import javax.sql.CommonDataSource;

/**
 * Provides application-facing configuration to {@link IuConnectionPool}
 */
public interface IuConnectionPoolConfiguration {

	/**
	 * Gets a human-readlable description of this database pool (i.e.
	 * database/username).
	 * 
	 * @return description
	 */
	String getDescription();

	/**
	 * Gets the login timeout.
	 * 
	 * @return login timeout
	 * @see CommonDataSource#getLoginTimeout
	 * @see CommonDataSource#setLoginTimeout(int)
	 */
	default Duration getLoginTimeout() {
		return Duration.ofSeconds(15L);
	}

	/**
	 * Gets the maximum number of connections to maintain in the pool.
	 * 
	 * @return maximum number of connections to maintain in the pool; default: 16
	 */
	default int getMaxSize() {
		return 16;
	}

	/**
	 * Gets the maximum number of times a connection attempt will be retried before
	 * resulting in failure.
	 * 
	 * @return maximum retry count; default: 1
	 */
	default int getMaxRetry() {
		return 1;
	}

	/**
	 * Gets the maximum number of times a single connection can be used before
	 * ejecting from the pool.
	 * 
	 * @return per-connection max reuse count; default: 100
	 */
	default long getMaxConnectionReuseCount() {
		return 100;
	}

	/**
	 * Gets the maximum length of time a single connection can remain open before
	 * ejecting from the pool.
	 * 
	 * @return per-connection max reuse time; default: 15 minutes
	 */
	default Duration getMaxConnectionReuseTime() {
		return Duration.ofMinutes(15L);
	}

	/**
	 * Gets the maximum length of time a connection can be checked out from the pool
	 * before attempting to forcibly close and consider it abandoned.
	 * 
	 * @return abandoned connection timeout interval; default: 30 minutes
	 */
	default Duration getAbandonedConnectionTimeout() {
		return Duration.ofMinutes(30L);
	}

	/**
	 * Gets the number of threads to allocation for reaping abandoned connections.
	 * 
	 * @return number of threads; default: 4
	 */
	default int getAbandonedConnectionThreads() {
		return 4;
	}

	/**
	 * Gets the maximum length of time to wait for all connections to close on
	 * shutdown. Note that the application should attempt to terminate all requests
	 * and complete all transactions prior to shutting down the database pool.
	 * 
	 * @return maximum length of time to wait for all connections to close
	 *         gracefully; default: 30 seconds
	 */
	default Duration getShutdownTimeout() {
		return Duration.ofSeconds(30L);
	}

	/**
	 * Gets the query to use for validating connections on creation and
	 * intermittently before checking out from the pool.
	 * 
	 * @return SQL select statement; <em>must</em> return a single row with a single
	 *         non-null column; may be null (default) to skip query validation
	 */
	default String getValidationQuery() {
		return null;
	}

	/**
	 * Gets the frequency at which to validate connections, when
	 * {@link #getValidationQuery()} returns a non-null value.
	 * 
	 * @return frequency at which to validate connections; default: 15 seconds
	 */
	default Duration getValidationInterval() {
		return Duration.ofSeconds(15L);
	}

}
