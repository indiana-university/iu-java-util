package edu.iu.jdbc.pool;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.sql.DataSource;
import javax.sql.XAConnection;

import edu.iu.IuException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

/**
 * JTA-aware {@link DataSource} backed by an {@link IuConnectionPool}.
 *
 * <p>
 * Accepts an {@link IuDataSourceIntegration} for hooking into the application's
 * transaction manager and an {@link IuConnectionPoolConfiguration} for tuning
 * pool behavior. The pool is created on construction and closed when
 * {@link #close()} is called; callers are responsible for lifecycle management,
 * typically via a try-with-resources block or a dependency-injection container.
 * </p>
 *
 * <h2>Connection dispatch</h2>
 * <ul>
 * <li><strong>No active JTA transaction</strong> – {@link #getConnection()}
 * checks a connection out of the pool and returns it directly. The caller is
 * responsible for closing the connection, which returns it to the pool.</li>
 * <li><strong>Active JTA transaction (non-XA)</strong> – the first call to
 * {@link #getConnection()} within a transaction checks out a connection,
 * disables auto-commit, sets isolation to
 * {@link Connection#TRANSACTION_SERIALIZABLE}, and registers a JTA
 * {@link Synchronization} that commits on {@code beforeCompletion} and rolls
 * back on {@code afterCompletion} if the transaction did not commit. Subsequent
 * calls within the same transaction return the same connection wrapped in a
 * proxy that silently ignores {@link Connection#close()}, deferring the actual
 * close to the synchronization callback.</li>
 * <li><strong>Active JTA transaction (XA-capable)</strong> – when the pooled
 * connection implements {@link XAConnection}, its
 * {@link javax.transaction.xa.XAResource} is enlisted with the transaction
 * manager. Commit and rollback are driven by the transaction manager; the
 * connection is closed in {@code afterCompletion}.</li>
 * </ul>
 *
 * <h2>Unsupported operations</h2>
 * <p>
 * {@link #getConnection(String, String)}, {@link #setLogWriter(PrintWriter)},
 * {@link #setLoginTimeout(int)}, and {@link #unwrap(Class)} all throw
 * {@link SQLFeatureNotSupportedException}.
 * </p>
 */
public class IuDataSource implements DataSource, AutoCloseable {

	static {
		Logger.getLogger(IuDataSource.class.getPackageName());
	}

	private static final Object CONNECTION_KEY = new Object();

	private final IuDataSourceIntegration integration;
	private final IuConnectionPoolConfiguration config;
	private final IuConnectionPool connectionPool;

	/**
	 * Constructor.
	 * 
	 * @param integration {@link IuDataSourceIntegration}
	 * @param config      {@link IuConnectionPoolConfiguration}
	 */
	public IuDataSource(IuDataSourceIntegration integration, IuConnectionPoolConfiguration config) {
		this.integration = integration;
		this.config = config;
		this.connectionPool = new IuConnectionPool(integration, config);
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
		final var transactionSynchronizationRegistry = integration.getTransactionSynchronizationRegistry();
		if (transactionSynchronizationRegistry == null //
				|| transactionSynchronizationRegistry.getTransactionStatus() != Status.STATUS_ACTIVE)
			return connectionPool.checkOut().getConnection();

		final var activeConnection = (Connection) transactionSynchronizationRegistry.getResource(CONNECTION_KEY);
		if (activeConnection != null)
			return activeConnection;

		final var pooledConnection = connectionPool.checkOut();
		final var managedConnection = integration.initializeConnection(pooledConnection.getConnection());

		if (pooledConnection instanceof XAConnection xaConnection)
			IuException.unchecked(() -> {
				final var xaResource = xaConnection.getXAResource();
				xaResource.setTransactionTimeout((int) config.getAbandonedConnectionTimeout().getSeconds());
				integration.getTransactionManager().getTransaction().enlistResource(xaResource);
			});
		else {
			managedConnection.setAutoCommit(false);
			managedConnection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		}

		transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
			@Override
			public void beforeCompletion() {
				if (!(pooledConnection instanceof XAConnection))
					IuException.unchecked(managedConnection::commit);
			}

			@Override
			public void afterCompletion(int status) {
				IuException.unchecked(() -> {
					if (!(pooledConnection instanceof XAConnection) //
							&& status != Status.STATUS_COMMITTED)
						managedConnection.rollback();
					managedConnection.close();
				});
			}
		});

		final var protectedConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "close":
						return null;
					default:
						return IuException.checkedInvocation(() -> method.invoke(managedConnection, args));
					}
				});
		transactionSynchronizationRegistry.putResource(CONNECTION_KEY, protectedConnection);

		return protectedConnection;
	}

	@Override
	public void close() throws SQLException {
		connectionPool.close();
	}

}
