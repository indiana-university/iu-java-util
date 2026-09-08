package edu.iu.jdbc.pool;

import java.util.logging.Level;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.XADataSource;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.postgresql.ds.PGConnectionPoolDataSource;
import org.postgresql.xa.PGXADataSource;

import edu.iu.IuRuntimeEnvironment;
import edu.iu.test.IuTestLogger;

@SuppressWarnings({ "javadoc", "exports" })
public class TestDatabase implements BeforeAllCallback, BeforeEachCallback {

	static ConnectionPoolDataSource dataSource;
	static XADataSource xaDataSource;

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		final var dataSource = new PGConnectionPoolDataSource();
		dataSource.setUrl("jdbc:postgresql://" + IuRuntimeEnvironment.env("postgres.host") + ":"
				+ IuRuntimeEnvironment.env("postgres.port") + "/postgres");
		dataSource.setUser("postgres");
		dataSource.setPassword(IuRuntimeEnvironment.env("postgres.password"));
		TestDatabase.dataSource = dataSource;

		final var xaDataSource = new PGXADataSource();
		xaDataSource.setUrl("jdbc:postgresql://" + IuRuntimeEnvironment.env("postgres.host") + ":"
				+ IuRuntimeEnvironment.env("postgres.port") + "/postgres");
		xaDataSource.setUser("postgres");
		xaDataSource.setPassword(IuRuntimeEnvironment.env("postgres.password"));
		TestDatabase.xaDataSource = xaDataSource;
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		IuTestLogger.allow("org.postgresql", Level.FINE);
	}

}