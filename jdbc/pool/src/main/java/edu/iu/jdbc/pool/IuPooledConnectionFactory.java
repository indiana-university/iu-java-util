package edu.iu.jdbc.pool;

import java.sql.SQLException;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import javax.sql.XADataSource;

/**
 * Supplies {@link PooledConnetion} or {@link XAConnection} from an underlying
 * JDBC driver.
 */
public interface IuPooledConnectionFactory {

	/**
	 * Creates a physical {@link PooledConnection} instance.
	 * 
	 * @return {@link PooledConnection}
	 * @throws SQLException if an error occurs
	 * @see ConnectionPoolDataSource#getPooledConnection
	 * @see XADataSource#getXAConnection
	 */
	PooledConnection createPooledConnection() throws SQLException;

	/**
	 * Invoked after all physical connections managed by the pool have been closed.
	 * No-op by default.
	 * 
	 * @throws SQLException if an error occurs
	 */
	default void onShutdown() throws SQLException {
	}

}
