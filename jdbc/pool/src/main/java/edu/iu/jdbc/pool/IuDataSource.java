package edu.iu.jdbc.pool;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;

/**
 * Wraps a {@link ConnectionPoolDataSource} to provide managed
 * {@link Connection} instances.
 */
public class IuDataSource implements DataSource {

	static {
		Logger.getLogger(IuDataSource.class.getPackageName());
	}

	private final IuConnectionPoolConfiguration config;
	private final IuConnectionPool connectionPool;

	/**
	 * Constructor.
	 * 
	 * @param config
	 */
	public IuDataSource(IuConnectionPoolConfiguration config) {
		this.config = config;
		this.connectionPool = new IuConnectionPool(config);
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		throw new SQLFeatureNotSupportedException();
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
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int getLoginTimeout() throws SQLException {
		return (int) config.getLoginTimeout().getSeconds();
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return LogManager.getLogManager().getLogger(IuDataSource.class.getPackageName());
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return false;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Connection getConnection() throws SQLException {
		return connectionPool.checkOut().getConnection();
	}

}
