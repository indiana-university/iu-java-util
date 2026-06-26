package edu.iu.jdbc.pool;

import java.sql.Connection;

import javax.sql.DataSource;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Application-facing management hooks for managing and optimizing database
 * interactions via {@link IuDataSource}.
 */
public interface IuDataSourceIntegration extends IuPooledConnectionFactory {

	/**
	 * Initializes a logical database connection before return from
	 * {@link DataSource#getConnection()}.
	 * 
	 * @param connection {@link Connection}
	 * @return {@link Connection}, as supplied or a wrapper to extend or modify
	 *         behavior according to application requirements
	 */
	default Connection initializeConnection(Connection connection) {
		return connection;
	}

	/**
	 * Gets the application's transaction manager.
	 * 
	 * @return {@link TransactionManager}; null (default) if XA transactions are not
	 *         supported
	 */
	default TransactionManager getTransactionManager() {
		return null;
	}

	/**
	 * Gets the application's transaction synchronization registry.
	 * 
	 * @return {@link TransactionSynchronizationRegistry}; null (default) if XA transactions are not
	 *         supported
	 */

}
