/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.iu.jdbc.pool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.util.PSQLException;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuParallelWorkloadController;
import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
@ExtendWith(TestDatabase.class)
public class IuConnectionPoolTest {

	private IuPooledConnectionFactory factory;
	private IuConnectionPoolConfiguration config;
	private IuConnectionPool connectionPool;
	private String descr;

	@BeforeEach
	public void setup() {
		descr = IdGenerator.generateId();
		config = mock(IuConnectionPoolConfiguration.class, CALLS_REAL_METHODS);
		when(config.getDescription()).thenReturn(descr);
		factory = mock(IuPooledConnectionFactory.class, CALLS_REAL_METHODS);
		connectionPool = new IuConnectionPool(factory, config);
	}

	@AfterEach
	public void teardown() throws SQLException {
		connectionPool.close();
	}

	@Test
	public void testCheckoutAndReuse() throws SQLException {
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc1 = connectionPool.checkOut();
		IuTestLogger.assertExpectedMessages();

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc2 = connectionPool.checkOut();
		IuTestLogger.assertExpectedMessages();

		assertNotSame(pc1, pc2);

		connectionPool.reuseOrClose(pc1);

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINER, "jdbc-pool-reuse:" + descr + ":PT.*");
		final var pc3 = connectionPool.checkOut();
		IuTestLogger.assertExpectedMessages();

		assertSame(pc1, pc3);
		connectionPool.reuseOrClose(pc2);
		connectionPool.reuseOrClose(pc3);
	}

	@Test
	public void testCheckoutAndWait() throws SQLException, InterruptedException, TimeoutException {
		when(config.getMaxSize()).thenReturn(2);
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());

		IuTestLogger.allow(IuConnectionPool.class.getName(), Level.FINE);
		final UnsafeRunnable use = () -> {
			final var pc = connectionPool.checkOut();
			Thread.sleep(1000L);
			connectionPool.reuseOrClose(pc);
		};

		IuTestLogger.allow(IuParallelWorkloadController.class.getName(), Level.CONFIG);
		final var c = new IuParallelWorkloadController(descr, 8, Duration.ofSeconds(15));
		for (var i = 0; i < 5; i++)
			c.apply(task -> use.run());
		assertDoesNotThrow(c::await);
		c.close();
	}

	@Test
	public void testRecoverFromInvalidConnection() throws SQLException, InterruptedException, TimeoutException {
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		when(config.getValidationQuery()).thenReturn("this is bad sql", "select 1");
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINER, "jdbc-pool-valid:" + descr + ".*");
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.INFO, "jdbc-pool-recoverable;.*",
				PSQLException.class);
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc = connectionPool.checkOut();
		try (final var c = pc.getConnection(); //
				final var s = c.prepareStatement("select 1"); //
				final var rs = s.executeQuery()) {
			assertTrue(rs.next());
			assertEquals(1, rs.getInt(1));
			assertFalse(rs.next());
		} finally {
			connectionPool.reuseOrClose(pc);
		}
	}

	@Test
	public void testCheckoutError() throws SQLException {
		when(factory.createPooledConnection()).thenThrow(SQLException.class);
		final var error = assertThrows(SQLException.class, connectionPool::checkOut);
		assertTrue(error.getMessage().startsWith("jdbc-pool-fail: attempt=2, "), error::getMessage);
		assertInstanceOf(SQLException.class, error.getCause());
	}

	@Test
	public void testCheckoutTimeout() throws SQLException {
		when(config.getLoginTimeout()).thenReturn(Duration.ofSeconds(1L));
		when(factory.createPooledConnection()).then(i -> {
			Thread.sleep(2000L);
			return TestDatabase.dataSource.getPooledConnection();
		});
		final var error = assertThrows(SQLException.class, connectionPool::checkOut);
		assertTrue(error.getMessage().startsWith("jdbc-pool-fail: attempt=1, "), error::getMessage);
		assertInstanceOf(SQLException.class, error.getCause());
	}

	@Test
	public void testCheckoutClosed() throws SQLException {
		connectionPool.close();
		final var error = assertThrows(SQLException.class, connectionPool::checkOut);
		assertTrue(error.getMessage().startsWith("jdbc-pool-fail: attempt=0, "), error::getMessage);
	}

	@Test
	public void testCheckoutClosedWait() throws SQLException, InterruptedException {
		when(config.getMaxSize()).thenReturn(1);
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		IuTestLogger.allow(IuConnectionPool.class.getName(), Level.FINE);
		final var pc = connectionPool.checkOut();
		final var t = new Thread() {
			Throwable error;

			@Override
			public void run() {
				try {
					Thread.sleep(1000L);
					connectionPool.reuseOrClose(pc);
					connectionPool.close();
				} catch (Throwable e) {
					error = e;
				}
			}
		};
		t.start();
		final var error = assertThrows(SQLException.class, connectionPool::checkOut);
		assertTrue(error.getMessage().startsWith("jdbc-pool-fail: attempt=1, "), error::getMessage);
		t.join(1000L);
		assertNull(t.error, () -> {
			throw IuException.unchecked(t.error);
		});
		assertFalse(t.isAlive());
	}

	@Test
	public void testRetireTimeout() throws SQLException, InterruptedException {
		when(config.getMaxConnectionReuseTime()).thenReturn(Duration.ofSeconds(1L));
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc1 = connectionPool.checkOut();
		connectionPool.reuseOrClose(pc1);
		Thread.sleep(1000L);
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE,
				"jdbc-pool-retire-timeout:" + descr + ":PT.*");
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc2 = connectionPool.checkOut();
		connectionPool.reuseOrClose(pc2);
		assertNotSame(pc1, pc2);
	}

	@Test
	public void testRevalidateTimeout() throws SQLException, InterruptedException {
		when(config.getValidationInterval()).thenReturn(Duration.ofSeconds(2L));
		when(config.getValidationQuery()).thenReturn("select 1");
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINER, "jdbc-pool-valid:" + descr + ".*");
		final var pc1 = connectionPool.checkOut();
		connectionPool.reuseOrClose(pc1);

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINER, "jdbc-pool-reuse:" + descr + ":PT.*");
		assertSame(pc1, connectionPool.checkOut());
		connectionPool.reuseOrClose(pc1);
		Thread.sleep(2000L);

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINER, "jdbc-pool-reuse:" + descr + ":PT.*");
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINER, "jdbc-pool-valid:" + descr + ".*");
		assertSame(pc1, connectionPool.checkOut());
		connectionPool.reuseOrClose(pc1);
	}

	@Test
	public void testAbandoned() throws SQLException, InterruptedException {
		when(config.getAbandonedConnectionTimeout()).thenReturn(Duration.ofSeconds(1L));
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc = connectionPool.checkOut();
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.INFO, "jdbc-pool-abandoned:" + descr + ":" + pc);
		Thread.sleep(2000L);
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.INFO, "jdbc-pool-error:" + descr + ":" + pc,
				PSQLException.class);
		assertThrows(SQLException.class, pc::getConnection);
	}

	@Test
	public void testCloseOrphanedConnection() throws SQLException, InterruptedException {
		final var pc = TestDatabase.dataSource.getPooledConnection();
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.INFO, "jdbc-pool-orphan:" + descr + ":" + pc);
		connectionPool.reuseOrClose(pc);
		assertThrows(SQLException.class, pc::getConnection);
	}

	@Test
	public void testCloseConnectionAfterPoolClose() throws SQLException, InterruptedException {
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc = connectionPool.checkOut();

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-retire:" + descr + ":1 .*");
		final var t = new Thread() {
			Throwable error;

			@Override
			public void run() {
				try {
					Thread.sleep(1000L);
					connectionPool.reuseOrClose(pc);
				} catch (Throwable e) {
					error = e;
				}
			}
		};
		t.start();
		connectionPool.close();
		t.join(1000L);
		assertFalse(t.isAlive());
		assertNull(t.error, () -> {
			throw IuException.unchecked(t.error);
		});
	}

	@Test
	public void testRetireCount() throws SQLException, InterruptedException {
		when(config.getMaxConnectionReuseCount()).thenReturn(2L);
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc = connectionPool.checkOut();
		connectionPool.reuseOrClose(pc);

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINER, "jdbc-pool-reuse:" + descr + ":PT.*");
		assertSame(pc, connectionPool.checkOut());

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-retire:" + descr + ":2 .*");
		connectionPool.reuseOrClose(pc);

		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc2 = connectionPool.checkOut();
		assertNotSame(pc, pc2);
		connectionPool.reuseOrClose(pc2);
	}

	@Test
	public void testCloseAfterShutdown() throws SQLException, InterruptedException {
		when(config.getShutdownTimeout()).thenReturn(Duration.ofMillis(1L));
		when(factory.createPooledConnection()).then(i -> TestDatabase.dataSource.getPooledConnection());
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.FINE, "jdbc-pool-open:" + descr + ":PT.*");
		final var pc = connectionPool.checkOut();
		assertThrows(IllegalStateException.class, connectionPool::close);
		IuTestLogger.expect(IuConnectionPool.class.getName(), Level.INFO, "jdbc-pool-error:" + descr + ":" + pc,
				PSQLException.class);
		assertThrows(SQLException.class, pc::getConnection);
	}

	@Test
	public void testErrorOnClose() throws SQLException, InterruptedException {
		doThrow(SQLException.class).when(factory).onShutdown();
		assertThrows(SQLException.class, connectionPool::close);
	}

}
