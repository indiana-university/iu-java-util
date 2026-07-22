package edu.iu.jdbc.pool;

import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import javax.sql.PooledConnection;

/**
 * Holds a {@link PooledConnection}.
 */
class PooledConnectionHolder {

	/** {@link PooledConnection} instance. */
	final PooledConnection pooledConnection;

	/** {@link Instant} the connection was initiated. */
	final Instant initiated;

	private volatile Instant lastUse;
	private volatile int usageCount;
	private volatile ScheduledFuture<?> reaperTask;

	/**
	 * Constructor
	 * 
	 * @param pooledConnection {@link PooledConnection} instance
	 * @param initiated        {@link Instant} the connection was initiated.
	 */
	PooledConnectionHolder(PooledConnection pooledConnection, Instant initiated) {
		this.initiated = initiated;
		this.pooledConnection = pooledConnection;
	}

	/**
	 * Invoked when a connection is checked out from the pool.
	 * 
	 * @param reaperTask abandoned connection reaper task to be canceled by
	 *                   {@link #usageComplete()}
	 */
	void usageStarted(ScheduledFuture<?> reaperTask) {
		this.reaperTask = reaperTask;
	}

	/**
	 * Increments the number of times the connection has been used, and sets the
	 * last usage time to the current time.
	 */
	synchronized void usageComplete() {
		final var reaperTask = this.reaperTask;
		this.reaperTask = null;
		if (reaperTask != null)
			reaperTask.cancel(false);

		lastUse = Instant.now();
		usageCount++;
	}

	/**
	 * Gets the last time the connection was used.
	 * 
	 * @return last time the connection was used
	 */
	Instant getLastUse() {
		return lastUse;
	}

	/**
	 * Gets the number of times the connection has been used.
	 * 
	 * @return number of times the connection has been used
	 */
	int getUsageCount() {
		return usageCount;
	}

	/**
	 * Validates {@link #pooledConnection} as connected and usable.
	 * 
	 * @param validationQuery SQL to execute on the connection, <em>should</em>
	 *                        produce at least one row with a non-null value in the
	 *                        first column
	 * @throws SQLException if the logical connection cannot be established or the
	 *                      validation query fails
	 */
	void validate(String validationQuery) throws SQLException {
		try (final var connection = pooledConnection.getConnection(); //
				final var statement = connection.createStatement(); //
				final var resultSet = statement.executeQuery(validationQuery)) {
			if (!resultSet.next() || resultSet.getObject(1) == null)
				throw new SQLException(
						"Validation query failed to produce a non-null result: " + validationQuery + "; " + this);
		}
	}

}
