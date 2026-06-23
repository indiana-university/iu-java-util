package edu.iu.jdbc.pool;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;

import javax.sql.ConnectionPoolDataSource;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.postgresql.ds.PGConnectionPoolDataSource;

import edu.iu.IuException;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTestLogger;

@SuppressWarnings({ "javadoc", "exports" })
public class TestDatabase implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

	private static ThreadLocal<Deque<UnsafeRunnable>> teardown = new ThreadLocal<>();

	static ConnectionPoolDataSource dataSource;

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		final var dataSource = new PGConnectionPoolDataSource();
		dataSource.setUrl("jdbc:postgresql://" + IuRuntimeEnvironment.env("postgres.host") + ":"
				+ IuRuntimeEnvironment.env("postgres.port") + "/postgres");
		dataSource.setUser("postgres");
		dataSource.setPassword(IuRuntimeEnvironment.env("postgres.password"));
		TestDatabase.dataSource = dataSource;
	}

	@Override
	public void afterAll(ExtensionContext context) throws Exception {
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		IuTestLogger.allow("org.postgresql", Level.FINE);
		teardown.set(new ArrayDeque<>());
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		Throwable error = context.getExecutionException().orElse(null);
		final var q = teardown.get();
		if (q != null)
			while (!q.isEmpty())
				error = IuException.suppress(error, q.pop());

		if (error != null)
			throw IuException.checked(error);
	}

}