package edu.iu.jdbc.pool;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import edu.iu.UnsafeSupplier;

/**
 * Non-transactional connection pooling data source.
 */
public class IuConnectionPoolDataSource extends IuCommonDataSource implements ConnectionPoolDataSource {

	/**
	 * Creates a new connection pool backed by an externally initialized connection
	 * factory.
	 * 
	 * @param factory {@link ConnectionPoolDataSource} connection factory
	 */
	public IuConnectionPoolDataSource(UnsafeSupplier<PooledConnection> factory) {
		super(factory);
	}

	@Override
	public IuPooledConnection getPooledConnection(String user, String password) throws SQLException {
		throw new SQLFeatureNotSupportedException(
				"connect with username/password not supported, use getPooledConnection()");
	}

}
