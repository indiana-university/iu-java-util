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
package iu.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuRefreshableCacheConfiguration;
import edu.iu.dao.IuDao;
import edu.iu.dao.IuSqlBuilder;
import edu.iu.dao.SqlQuery;
import edu.iu.dao.SqlStatement;
import edu.iu.dao.TableDefinition;
import edu.iu.test.IuTestLogger;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Covers the cache layer without a database: the delegate, the SQL builder, and
 * the transaction infrastructure are all stubbed, so what is exercised is the
 * dispatch, the buffering, and what gets published.
 */
@SuppressWarnings("javadoc")
public class CachedDaoTest {

	/** Entity type used as a cache-key discriminator; never instantiated by SQL. */
	public static class Bean {
		private final String id;
		private final String value;

		Bean(String id, String value) {
			this.id = id;
			this.value = value;
		}

		@Override
		public String toString() {
			return id + "=" + value;
		}
	}

	/** A second type, to show invalidation does not cross type boundaries. */
	public static class Other {
	}

	/** An interface-mapped entity, materialized by the delegate as a proxy. */
	public interface BeanView {
		/**
		 * Gets the mapped key.
		 *
		 * @return key
		 */
		String getId();
	}

	/**
	 * Delegate that counts reads and serves whatever the test last stored, so a
	 * cache hit is observable as a read that did not reach it.
	 */
	private static class StubDao implements IuDao {
		private final AtomicInteger loads = new AtomicInteger();
		private final AtomicInteger searches = new AtomicInteger();
		private final List<String> writes = new ArrayList<>();
		private final List<String> passthrough = new ArrayList<>();
		private final List<String> executions = new ArrayList<>();
		private final AtomicInteger queryReads = new AtomicInteger();
		private final Map<String, Bean> rows = new LinkedHashMap<>();

		/** Records a write; only {@link Bean} instances are stored. */
		private void store(Object bean) {
			if (bean instanceof Bean row)
				rows.put(row.id, row);
		}

		/**
		 * Records a call the cache layer wraps or passes through, and answers with a
		 * query over every row this delegate holds.
		 *
		 * @param call      description of the call, asserted by the test
		 * @param beanClass row type
		 * @return query over every stored row
		 */
		private <B> SqlQuery<B> query(String call, Class<B> beanClass) {
			passthrough.add(call);
			final List<B> results = new ArrayList<>();
			rows.values().forEach(row -> results.add(beanClass.cast(row)));
			return new StubQuery<>(call, results, queryReads);
		}

		/**
		 * Records a call the cache layer wraps or passes through, and answers with a
		 * statement that reports one affected row.
		 *
		 * @param call description of the call, asserted by the test
		 * @return statement
		 */
		private SqlStatement statement(String call) {
			passthrough.add(call);
			return new StubStatement(call, executions);
		}

		@Override
		public <B> B loadBean(Class<B> beanClass, Map<String, ?> idParams) {
			loads.incrementAndGet();
			return beanClass.cast(rows.get(idParams.get("id")));
		}

		@Override
		public <B> List<B> searchBeans(Class<B> beanClass, Map<String, ?> idParams, boolean observe, int maxResults) {
			searches.incrementAndGet();
			final List<B> results = new ArrayList<>();
			rows.values().forEach(row -> results.add(beanClass.cast(row)));
			return Collections.unmodifiableList(results);
		}

		@Override
		public void saveBean(Object bean) {
			writes.add("save");
			store(bean);
		}

		@Override
		public void updateBean(Object bean) {
			writes.add("update");
			store(bean);
		}

		@Override
		public void deleteBean(Object bean) {
			writes.add("delete");
			if (bean instanceof Bean row)
				rows.remove(row.id);
		}

		@Override
		public void clear() {
			writes.add("clear");
		}

		@Override
		public void clear(Class<?> beanClass) {
			writes.add("clear:" + beanClass.getSimpleName());
		}

		@Override
		public void insertBeans(Iterable<?> beans) {
			writes.add("insertBeans");
		}

		@Override
		public void updateBeans(Iterable<?> beans) {
			writes.add("updateBeans");
		}

		@Override
		public void saveBeans(Iterable<?> beans) {
			writes.add("saveBeans");
		}

		@Override
		public TableDefinition getTableDefinition(String tableName) {
			passthrough.add("getTableDefinition:" + tableName);
			return null;
		}

		@Override
		public SqlStatement getStatement(String sql, Iterable<?> args) {
			return statement("getStatement:" + sql + args);
		}

		@Override
		public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where, Iterable<?> args) {
			return query("getBeanQuery:" + beanClass.getSimpleName() + where + args, beanClass);
		}

		@Override
		public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where, Iterable<String> order,
				Iterable<?> args) {
			return query("getBeanQuery:" + beanClass.getSimpleName() + where + order + args, beanClass);
		}

		@Override
		public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Map<String, ?> idParams) {
			return query("getBeanQuery:" + beanClass.getSimpleName() + idParams, beanClass);
		}

		@Override
		public SqlStatement getBeanUpdate(Object bean) {
			return statement("getBeanUpdate:" + bean);
		}

		@Override
		public SqlStatement getBeanUpdate(Object bean, Supplier<?> passive) {
			return statement("getBeanUpdate:" + bean + ":" + passive.get());
		}

		@Override
		public SqlStatement getBeanInsert(Object bean) {
			return statement("getBeanInsert:" + bean);
		}

		@Override
		public SqlStatement getBeanDelete(Object bean) {
			return statement("getBeanDelete:" + bean);
		}

		@Override
		public <B> SqlQuery<B> getQuery(Class<B> beanClass, String sql, Iterable<?> args) {
			return query("getQuery:" + beanClass.getSimpleName() + ":" + sql + args, beanClass);
		}

		@Override
		public <B> SqlQuery<B> getFactoryQuery(Function<ResultSet, B> factory, String sql, Iterable<?> args) {
			final var call = "getFactoryQuery:" + sql + args;
			passthrough.add(call);
			return new StubQuery<>(call, List.of(factory.apply(null)), queryReads);
		}
	}

	/**
	 * A query over a fixed list of rows, counting how many times its cursor is
	 * read so that a test can tell a drained result from a re-read one.
	 *
	 * <p>
	 * Only the accessors the cache layer actually calls are implemented; the rest
	 * are the layer's own job, and answering them here would hide whether it does
	 * that job.
	 * </p>
	 */
	private static class StubQuery<B> implements SqlQuery<B> {
		private final String sql;
		private final List<B> rows;
		private final AtomicInteger reads;
		private int position;

		StubQuery(String sql, List<B> rows, AtomicInteger reads) {
			this.sql = sql;
			this.rows = rows;
			this.reads = reads;
		}

		@Override
		public List<B> getResults() {
			reads.incrementAndGet();
			final var remaining = List.copyOf(rows.subList(position, rows.size()));
			position = rows.size();
			return remaining;
		}

		@Override
		public ResultSet getResultSet() {
			// stands in for a caller reading the driver's cursor and consuming a row
			position = Math.min(position + 1, rows.size());
			return null;
		}

		@Override
		public String getQuery() {
			return sql;
		}

		@Override
		public Iterable<?> getArguments() {
			return List.of("arg:" + sql);
		}

		@Override
		public PreparedStatement getPreparedStatement() {
			return null;
		}

		@Override
		public void close() {
			// a closed operation may be used again, re-reading from the first row
			position = 0;
		}

		@Override
		public B getSingleResult() {
			throw new UnsupportedOperationException();
		}

		@Override
		public B getFirstRecord() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<B> getResults(int maxRows) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Stream<B> getResultStream() {
			throw new UnsupportedOperationException();
		}
	}

	/** A statement that reports one affected row and records each execution. */
	private static class StubStatement implements SqlStatement {
		private final String sql;
		private final List<String> executions;

		StubStatement(String sql, List<String> executions) {
			this.sql = sql;
			this.executions = executions;
		}

		@Override
		public int execute() {
			executions.add(sql);
			return 1;
		}

		@Override
		public String getQuery() {
			return sql;
		}

		@Override
		public Iterable<?> getArguments() {
			return List.of("arg:" + sql);
		}

		@Override
		public PreparedStatement getPreparedStatement() {
			return null;
		}

		@Override
		public void close() {
			executions.add("close:" + sql);
		}
	}

	/**
	 * Reads {@link Bean#id} as the mapped key, as the real builder would.
	 *
	 * <p>
	 * A proxy rather than an implementation: the cache layer calls two of this
	 * interface's methods, and stubbing the rest by hand would say nothing about
	 * what is under test.
	 * </p>
	 */
	private static class StubBuilder {
		private boolean keyless;

		/** Fails the test on any call, to assert the layer never reaches the builder. */
		private boolean forbidden;

		/** Reads the mapped key of an entity, as the real builder would. */
		private Function<Object, Object> key = bean -> ((Bean) bean).id;

		IuSqlBuilder builder() {
			return (IuSqlBuilder) Proxy.newProxyInstance(IuSqlBuilder.class.getClassLoader(),
					new Class<?>[] { IuSqlBuilder.class }, (proxy, method, args) -> {
						if (forbidden)
							throw new AssertionError("the cache layer called IuSqlBuilder." + method.getName());

						switch (method.getName()) {
						case "getPrimaryKeyProperties":
							return keyless ? List.of() : List.of("id");
						case "getForSql":
							return key.apply(args[0]);
						default:
							throw new UnsupportedOperationException(method.getName());
						}
					});
		}
	}

	/** Transaction state the test drives by hand. */
	private static class StubTransaction implements TransactionManager, TransactionSynchronizationRegistry {
		private int status = Status.STATUS_NO_TRANSACTION;
		private final Map<Object, Object> resources = new LinkedHashMap<>();
		private final List<Synchronization> synchronizations = new ArrayList<>();
		private final AtomicInteger statusChecks = new AtomicInteger();

		/** Fails the test if the layer buffers work on the caller's transaction. */
		private boolean forbidden;

		void beginTransaction() {
			status = Status.STATUS_ACTIVE;
		}

		void complete(int completion) {
			final var toComplete = List.copyOf(synchronizations);
			synchronizations.clear();

			// a real manager runs beforeCompletion while the transaction is still
			// active, and only when it is still on course to commit
			if (completion == Status.STATUS_COMMITTED)
				toComplete.forEach(Synchronization::beforeCompletion);

			status = Status.STATUS_NO_TRANSACTION;
			resources.clear();
			toComplete.forEach(synchronization -> synchronization.afterCompletion(completion));
		}

		@Override
		public int getStatus() {
			statusChecks.incrementAndGet();
			return status;
		}

		@Override
		public Object getResource(Object key) {
			return resources.get(key);
		}

		@Override
		public void putResource(Object key, Object value) {
			if (forbidden)
				throw new AssertionError("the cache layer stored a resource on the transaction");

			resources.put(key, value);
		}

		@Override
		public void registerInterposedSynchronization(Synchronization sync) {
			if (forbidden)
				throw new AssertionError("the cache layer registered a synchronization");

			synchronizations.add(sync);
		}

		@Override
		public Object getTransactionKey() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getTransactionStatus() {
			return status;
		}

		@Override
		public boolean getRollbackOnly() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setRollbackOnly() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void begin() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void commit() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void rollback() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setTransactionTimeout(int seconds) {
			throw new UnsupportedOperationException();
		}

		@Override
		public jakarta.transaction.Transaction getTransaction() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void resume(jakarta.transaction.Transaction transaction) {
			throw new UnsupportedOperationException();
		}

		@Override
		public jakarta.transaction.Transaction suspend() {
			throw new UnsupportedOperationException();
		}
	}

	private StubDao delegate;
	private StubBuilder sqlBuilder;
	private StubTransaction transaction;
	private Duration refreshTtl;
	private CachedDao dao;

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("edu.iu.IuRefreshableCache", Level.FINE);
		IuTestLogger.allow("iu.dao.CachedDao", Level.FINE);

		delegate = new StubDao();
		sqlBuilder = new StubBuilder();
		transaction = new StubTransaction();
		refreshTtl = Duration.ofMinutes(5L);

		final IuRefreshableCacheConfiguration config = new IuRefreshableCacheConfiguration() {
			@Override
			public Duration getRefreshTtl() {
				return refreshTtl;
			}
		};
		dao = new CachedDao(delegate, sqlBuilder.builder(), transaction, transaction, () -> config);
	}

	private static Map<String, Object> id(String id) {
		return Map.of("id", id);
	}

	@Test
	public void testNonTransactionalReadIsServedFromCache() {
		delegate.rows.put("a", new Bean("a", "one"));

		final var first = dao.loadBean(Bean.class, id("a"));
		assertEquals("a=one", first.toString());
		assertSame(first, dao.loadBean(Bean.class, id("a")));
		assertEquals(1, delegate.loads.get(), "a cached load reached the delegate");
	}

	@Test
	public void testSearchPublishesItsRowsAsLoads() {
		delegate.rows.put("a", new Bean("a", "one"));
		delegate.rows.put("b", new Bean("b", "two"));

		final var results = dao.searchBeans(Bean.class, Map.of(), false, 0);
		assertEquals(2, results.size());

		// the search already read these rows, so loading one individually is answered
		// without any further call
		assertSame(results.get(0), dao.loadBean(Bean.class, id("a")));
		assertSame(results.get(1), dao.loadBean(Bean.class, id("b")));
		assertEquals(0, delegate.loads.get(), "a row published by a search was loaded again");
		assertEquals(1, delegate.searches.get());
	}

	@Test
	public void testRowsWithNoResolvableKeyArePublishedAsNothing() {
		sqlBuilder.keyless = true;
		delegate.rows.put("a", new Bean("a", "one"));

		assertEquals(1, dao.searchBeans(Bean.class, Map.of(), false, 0).size());

		// nothing was published under a key, so the load resolves on its own
		assertEquals("a=one", dao.loadBean(Bean.class, id("a")).toString());
		assertEquals(1, delegate.loads.get());
	}

	@Test
	public void testTransactionalReadIsDelegatedAndInjectedOnCommit() {
		delegate.rows.put("a", new Bean("a", "one"));

		transaction.beginTransaction();
		final var inTransaction = dao.loadBean(Bean.class, id("a"));
		final var again = dao.loadBean(Bean.class, id("a"));
		assertEquals(2, delegate.loads.get(), "a transactional read was answered by the process-wide cache");

		transaction.complete(Status.STATUS_COMMITTED);

		// the value it paid for is now serving readers outside the transaction
		assertSame(again, dao.loadBean(Bean.class, id("a")));
		assertEquals(2, delegate.loads.get());
		assertNotSame(null, inTransaction);
	}

	@Test
	public void testRolledBackReadIsNotInjected() {
		delegate.rows.put("a", new Bean("a", "one"));

		transaction.beginTransaction();
		dao.loadBean(Bean.class, id("a"));
		transaction.complete(Status.STATUS_ROLLEDBACK);

		dao.loadBean(Bean.class, id("a"));
		assertEquals(2, delegate.loads.get(), "a rolled back read was published");
	}

	@Test
	public void testWriteInvalidatesSearchesAndRepublishesTheRowOnCommit() {
		delegate.rows.put("a", new Bean("a", "one"));
		assertEquals(1, dao.searchBeans(Bean.class, Map.of(), false, 0).size());

		final var replacement = new Bean("a", "two");
		transaction.beginTransaction();
		dao.saveBean(replacement);

		// a read inside the transaction is delegated, so it says nothing about what
		// the cache holds
		assertEquals(1, dao.searchBeans(Bean.class, Map.of(), false, 0).size());
		assertEquals(2, delegate.searches.get());

		transaction.complete(Status.STATUS_COMMITTED);

		// the write invalidated the search cached before it, and also outranks the
		// injection of the search read inside the transaction, which was read before
		// the write committed
		assertEquals(1, dao.searchBeans(Bean.class, Map.of(), false, 0).size());
		assertEquals(3, delegate.searches.get());
		assertSame(replacement, dao.loadBean(Bean.class, id("a")));
		assertEquals(0, delegate.loads.get(), "the row a write republished was loaded again");
	}

	@Test
	public void testDeleteInvalidatesTheRowItRemoved() {
		final var row = new Bean("a", "one");
		delegate.rows.put("a", row);
		assertSame(row, dao.loadBean(Bean.class, id("a")));

		dao.deleteBean(row);

		assertEquals(null, dao.loadBean(Bean.class, id("a")));
		assertEquals(2, delegate.loads.get());
	}

	@Test
	public void testInvalidationDoesNotCrossEntityTypes() {
		delegate.rows.put("a", new Bean("a", "one"));
		final var cached = dao.loadBean(Bean.class, id("a"));

		dao.clear(Other.class);

		assertSame(cached, dao.loadBean(Bean.class, id("a")));
		assertEquals(1, delegate.loads.get());
		assertEquals(List.of("clear:Other"), delegate.writes);
	}

	@Test
	public void testAWriteDoesNotDiscardAnotherTypeReadInTheSameTransaction() {
		delegate.rows.put("a", new Bean("a", "one"));

		transaction.beginTransaction();
		// read first, so its injection carries a mark older than the write below
		dao.loadBean(Other.class, id("x"));
		dao.updateBean(new Bean("a", "two"));
		transaction.complete(Status.STATUS_COMMITTED);

		// the write is newer than the read, but says nothing about the read's type,
		// so the value the transaction paid for still reaches the cache
		dao.loadBean(Other.class, id("x"));
		assertEquals(1, delegate.loads.get(), "a write to one type discarded a read of another");
	}

	@Test
	public void testClearingATypeDiscardsItsLoadsAndNotOnlyItsSearches() {
		delegate.rows.put("a", new Bean("a", "one"));
		dao.loadBean(Bean.class, id("a"));
		dao.searchBeans(Bean.class, Map.of(), false, 0);

		dao.clear(Bean.class);

		// a load left behind by clear() would go on serving a row the caller has
		// already told this layer it no longer knows anything about
		dao.loadBean(Bean.class, id("a"));
		dao.searchBeans(Bean.class, Map.of(), false, 0);
		assertEquals(2, delegate.loads.get(), "clear(Class) left a load cached");
		assertEquals(2, delegate.searches.get(), "clear(Class) left a search cached");
	}

	@Test
	public void testAWriteWithNoResolvableKeyDiscardsEveryLoadOfItsType() {
		delegate.rows.put("a", new Bean("a", "one"));
		dao.loadBean(Bean.class, id("a"));

		// nothing can be republished and no single load can be named, so the write
		// has to discard every load of the type rather than leave a stale one
		sqlBuilder.keyless = true;
		dao.updateBean(new Bean("a", "two"));

		assertEquals("a=two", dao.loadBean(Bean.class, id("a")).toString());
		assertEquals(2, delegate.loads.get(), "a keyless write left a stale load cached");
	}

	@Test
	public void testARowWhoseKeyIsNullIsNotPublished() {
		sqlBuilder.key = bean -> null;
		delegate.rows.put("a", new Bean("a", "one"));

		assertEquals(1, dao.searchBeans(Bean.class, Map.of(), false, 0).size());

		// a row read without its own identity cannot be published as a load of it
		assertEquals("a=one", dao.loadBean(Bean.class, id("a")).toString());
		assertEquals(1, delegate.loads.get());
	}

	@Test
	public void testARowWhoseKeyCannotBeReadIsNotPublished() {
		sqlBuilder.key = bean -> {
			throw new IllegalStateException("not mapped");
		};
		delegate.rows.put("a", new Bean("a", "one"));

		// the read that carried the row still succeeds; only the publication is lost
		assertEquals(1, dao.searchBeans(Bean.class, Map.of(), false, 0).size());
		assertEquals("a=one", dao.loadBean(Bean.class, id("a")).toString());
		assertEquals(1, delegate.loads.get());
	}

	@Test
	public void testAProxiedEntityIsKeyedByTheInterfaceItWasReadAs() {
		final var view = (BeanView) Proxy.newProxyInstance(BeanView.class.getClassLoader(),
				new Class<?>[] { BeanView.class }, (proxy, method, args) -> "a");
		sqlBuilder.key = bean -> ((BeanView) bean).getId();

		dao.updateBean(view);

		// keyed by the proxy's own class the republished row would be filed under a
		// key no load could ever ask for
		assertSame(view, dao.loadBean(BeanView.class, id("a")));
		assertEquals(0, delegate.loads.get(), "the row a write republished was loaded again");
	}

	@Test
	public void testNegativeMaxResultsIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> dao.searchBeans(Bean.class, Map.of(), false, -1));
		assertEquals(0, delegate.searches.get());
	}

	@Test
	public void testEveryQueryAndStatementReachesTheDelegateWithItsArguments() {
		assertNull(dao.getTableDefinition("T"));
		dao.getStatement("sql", List.of("a"));
		dao.getBeanQuery(Bean.class, List.of("w"), List.of("a"));
		dao.getBeanQuery(Bean.class, List.of("w"), List.of("o"), List.of("a"));
		dao.getBeanQuery(Bean.class, id("a"));
		dao.getBeanUpdate("bean");
		dao.getBeanUpdate("bean", () -> "passive");
		dao.getBeanInsert("bean");
		dao.getBeanDelete("bean");
		dao.getQuery(Bean.class, "sql", List.of("a"));
		dao.getFactoryQuery(rs -> "factory", "sql", List.of("a"));

		assertEquals(List.of( //
				"getTableDefinition:T", //
				"getStatement:sql[a]", //
				"getBeanQuery:Bean[w][a]", //
				"getBeanQuery:Bean[w][o][a]", //
				"getBeanQuery:Bean{id=a}", //
				"getBeanUpdate:bean", //
				"getBeanUpdate:bean:passive", //
				"getBeanInsert:bean", //
				"getBeanDelete:bean", //
				"getQuery:Bean:sql[a]", //
				"getFactoryQuery:sql[a]"), delegate.passthrough);
	}

	@Test
	public void testAWrappedOperationCarriesTheDelegatesSqlAndArguments() {
		final var query = dao.getBeanQuery(Bean.class, id("a"));
		assertEquals("getBeanQuery:Bean{id=a}", query.getQuery());
		assertEquals(List.of("arg:getBeanQuery:Bean{id=a}"), query.getArguments());
		assertNull(query.getPreparedStatement());

		final var statement = dao.getBeanInsert("bean");
		assertEquals("getBeanInsert:bean", statement.getQuery());
		assertEquals(List.of("arg:getBeanInsert:bean"), statement.getArguments());
		assertNull(statement.getPreparedStatement());

		statement.close();
		assertEquals(List.of("close:getBeanInsert:bean"), delegate.executions);
	}

	@Test
	public void testABeanQueryPublishesTheRowsItRead() {
		delegate.rows.put("a", new Bean("a", "one"));
		delegate.rows.put("b", new Bean("b", "two"));

		final List<Bean> results;
		try (var query = dao.getBeanQuery(Bean.class, Map.of())) {
			results = query.getResults();
		}
		assertEquals(2, results.size());

		// the query already read these rows, so loading one costs nothing further
		assertSame(results.get(0), dao.loadBean(Bean.class, id("a")));
		assertSame(results.get(1), dao.loadBean(Bean.class, id("b")));
		assertEquals(0, delegate.loads.get(), "a row published by a query was loaded again");
	}

	@Test
	public void testABeanQueryReadsItsCursorOnceAndPagesOverWhatItRead() {
		delegate.rows.put("a", new Bean("a", "one"));
		delegate.rows.put("b", new Bean("b", "two"));
		delegate.rows.put("c", new Bean("c", "three"));

		try (var query = dao.getBeanQuery(Bean.class, Map.of())) {
			assertEquals(2, query.getResults(2).size());
			assertEquals(1, query.getResults(2).size());

			// a page smaller than the limit means the cursor is exhausted
			assertEquals(0, query.getResults(2).size());
		}

		assertEquals(1, delegate.queryReads.get(), "paging read the cursor more than once");
	}

	@Test
	public void testAClosedQueryReplaysItsRowsWithoutReadingAgain() {
		delegate.rows.put("a", new Bean("a", "one"));
		delegate.rows.put("b", new Bean("b", "two"));

		try (var query = dao.getBeanQuery(Bean.class, Map.of())) {
			// getResults is terminal and closes, so the next call starts over
			assertEquals(2, query.getResults().size());
			assertEquals(2, query.getResults().size());
		}

		assertEquals(1, delegate.queryReads.get(), "a reopened query went back to the database");
	}

	@Test
	public void testTheTerminalSingleRowAccessors() {
		delegate.rows.put("a", new Bean("a", "one"));
		assertEquals("a=one", dao.getBeanQuery(Bean.class, id("a")).getSingleResult().toString());
		assertEquals("a=one", dao.getBeanQuery(Bean.class, id("a")).getFirstRecord().toString());

		delegate.rows.put("b", new Bean("b", "two"));
		assertEquals("a=one", dao.getBeanQuery(Bean.class, Map.of()).getFirstRecord().toString());
		assertThrows(NonUniqueResultException.class, () -> dao.getBeanQuery(Bean.class, Map.of()).getSingleResult());

		delegate.rows.clear();
		assertThrows(EntityNotFoundException.class, () -> dao.getBeanQuery(Bean.class, Map.of()).getSingleResult());
		assertThrows(EntityNotFoundException.class, () -> dao.getBeanQuery(Bean.class, Map.of()).getFirstRecord());
	}

	@Test
	public void testTheResultStreamTakesEveryRemainingRow() {
		delegate.rows.put("a", new Bean("a", "one"));
		delegate.rows.put("b", new Bean("b", "two"));

		try (var query = dao.getBeanQuery(Bean.class, Map.of())) {
			assertEquals(1, query.getResults(1).size());
			assertEquals(List.of("b=two"), query.getResultStream().map(Object::toString).collect(Collectors.toList()));
			assertEquals(0, query.getResults(1).size(), "the stream left rows behind it");
		}

		assertThrows(IllegalArgumentException.class, () -> dao.getBeanQuery(Bean.class, Map.of()).getResults(0));
	}

	@Test
	public void testTakingTheResultSetLeavesTheCursorToTheCaller() {
		delegate.rows.put("a", new Bean("a", "one"));
		delegate.rows.put("b", new Bean("b", "two"));

		try (var query = dao.getBeanQuery(Bean.class, Map.of())) {
			// the caller takes the driver's cursor and consumes a row from it
			assertNull(query.getResultSet());

			// no list can stand in for that, so what follows reads forward from where
			// the caller left the cursor rather than replaying the row they already saw
			final var rest = query.getResults();
			assertEquals(1, rest.size());
			assertEquals("b=two", rest.get(0).toString());
		}

		assertEquals(1, delegate.queryReads.get());
	}

	@Test
	public void testTakingTheResultSetDiscardsWhatWasAlreadyDrained() {
		delegate.rows.put("a", new Bean("a", "one"));
		delegate.rows.put("b", new Bean("b", "two"));

		try (var query = dao.getBeanQuery(Bean.class, Map.of())) {
			assertEquals(2, query.getResults(2).size());

			// a drained list can no longer say where the caller's cursor is, so it is
			// dropped and the next accessor goes back to the delegate — which has by
			// now been read to the end
			assertNull(query.getResultSet());
			assertEquals(0, query.getResults().size());
		}

		assertEquals(2, delegate.queryReads.get(), "the discarded list was replayed");
	}

	@Test
	public void testARawQueryIsDrainedButPublishesNothing() {
		delegate.rows.put("a", new Bean("a", "one"));

		try (var query = dao.getQuery(Bean.class, "select id", List.of())) {
			assertEquals(1, query.getResults().size());
			assertEquals(1, query.getResults().size());
		}
		assertEquals(1, delegate.queryReads.get());

		// the projection is the caller's, so a row it carried is not a row this
		// layer can claim a load would have produced
		assertEquals("a=one", dao.loadBean(Bean.class, id("a")).toString());
		assertEquals(1, delegate.loads.get(), "a raw query published a row");
	}

	@Test
	public void testAFactoryQueryIsDrainedButPublishesNothing() {
		try (var query = dao.getFactoryQuery(rs -> "row", "select 1", List.of())) {
			assertEquals(List.of("row"), query.getResults());
			assertEquals(List.of("row"), query.getResults());
		}
		assertEquals(1, delegate.queryReads.get());
	}

	@Test
	public void testAGeneratedStatementInvalidatesTheRowItWrites() {
		delegate.rows.put("a", new Bean("a", "one"));
		final var cached = dao.loadBean(Bean.class, id("a"));
		assertSame(cached, dao.loadBean(Bean.class, id("a")));

		delegate.rows.put("a", new Bean("a", "two"));
		assertEquals(1, dao.getBeanUpdate(new Bean("a", "x")).execute());

		// invalidated rather than republished: the statement is a template the
		// caller drives, not an account of the row it left behind
		assertEquals("a=two", dao.loadBean(Bean.class, id("a")).toString());
		assertEquals(2, delegate.loads.get());
	}

	@Test
	public void testEveryGeneratedStatementInvalidatesWhenExecuted() {
		for (final var generator : List.<Function<Object, SqlStatement>>of( //
				dao::getBeanUpdate, //
				bean -> dao.getBeanUpdate(bean, () -> "passive"), //
				dao::getBeanInsert, //
				dao::getBeanDelete)) {
			dao.clear();
			delegate.rows.put("a", new Bean("a", "one"));
			final var statement = generator.apply(new Bean("a", "x"));

			final var before = dao.loadBean(Bean.class, id("a"));
			assertSame(before, dao.loadBean(Bean.class, id("a")), "the row was not cached before the write");

			final var replacement = new Bean("a", "two");
			delegate.rows.put("a", replacement);
			assertEquals(1, statement.execute());

			assertSame(replacement, dao.loadBean(Bean.class, id("a")), statement.getQuery());
		}
	}

	@Test
	public void testARawStatementLeavesTheCacheAlone() {
		delegate.rows.put("a", new Bean("a", "one"));
		final var cached = dao.loadBean(Bean.class, id("a"));

		assertEquals(1, dao.getStatement("delete from bean", List.of()).execute());

		// raw SQL names neither an entity type nor a row, so there is nothing to
		// invalidate by; the caller still has to clear() after modifying rows this way
		assertSame(cached, dao.loadBean(Bean.class, id("a")));
		assertEquals(1, delegate.loads.get());
		assertEquals(List.of("getStatement:delete from bean[]"), delegate.executions);
	}

	@Test
	public void testQueriesAndStatementsArePassedThroughWhenTheLayerIsInert() {
		refreshTtl = null;
		delegate.rows.put("a", new Bean("a", "one"));

		assertInstanceOf(StubQuery.class, dao.getBeanQuery(Bean.class, Map.of()));
		assertInstanceOf(StubQuery.class, dao.getQuery(Bean.class, "sql", List.of()));
		assertInstanceOf(StubStatement.class, dao.getBeanInsert(new Bean("a", "x")));
	}

	@Test
	public void testEveryOperationIsPassedThroughWhenTheLayerIsInert() {
		refreshTtl = null;

		// a null refresh TTL is the whole of the opt-out, so nothing below may read a
		// mapped key, consult the transaction, or leave anything behind on it
		sqlBuilder.forbidden = true;
		transaction.forbidden = true;
		transaction.beginTransaction();

		delegate.rows.put("a", new Bean("a", "one"));
		final var row = new Bean("a", "two");

		dao.loadBean(Bean.class, id("a"));
		dao.searchBeans(Bean.class, Map.of(), false, 0);
		dao.updateBean(row);
		dao.saveBean(row);
		dao.deleteBean(row);
		dao.insertBeans(List.of(row));
		dao.updateBeans(List.of(row));
		dao.saveBeans(List.of(row));
		dao.clear(Bean.class);
		dao.clear();

		assertInstanceOf(StubQuery.class, dao.getBeanQuery(Bean.class, Map.of()));
		assertInstanceOf(StubQuery.class, dao.getBeanQuery(Bean.class, List.of(), List.of()));
		assertInstanceOf(StubQuery.class, dao.getBeanQuery(Bean.class, List.of(), List.of(), List.of()));
		assertInstanceOf(StubQuery.class, dao.getQuery(Bean.class, "sql", List.of()));
		assertInstanceOf(StubQuery.class, dao.getFactoryQuery(rs -> "row", "sql", List.of()));
		assertInstanceOf(StubStatement.class, dao.getBeanUpdate(row));
		assertInstanceOf(StubStatement.class, dao.getBeanUpdate(row, () -> "passive"));
		assertInstanceOf(StubStatement.class, dao.getBeanInsert(row));
		assertInstanceOf(StubStatement.class, dao.getBeanDelete(row));
		assertInstanceOf(StubStatement.class, dao.getStatement("sql", List.of()));

		// every read reached the delegate, and every write reached it exactly once
		assertEquals(1, delegate.loads.get());
		assertEquals(1, delegate.searches.get());
		assertEquals(List.of("update", "save", "delete", "insertBeans", "updateBeans", "saveBeans", "clear:Bean",
				"clear"), delegate.writes);

		// the transaction was never even asked whether it was active
		assertEquals(0, transaction.statusChecks.get());
		assertEquals(List.of(), transaction.synchronizations);
	}

	@Test
	public void testClearDiscardsEverything() {
		delegate.rows.put("a", new Bean("a", "one"));
		dao.loadBean(Bean.class, id("a"));

		dao.clear();

		dao.loadBean(Bean.class, id("a"));
		assertEquals(2, delegate.loads.get());
		assertEquals(List.of("clear"), delegate.writes);
	}

	@Test
	public void testBulkWritesDiscardEverything() {
		delegate.rows.put("a", new Bean("a", "one"));

		dao.loadBean(Bean.class, id("a"));
		dao.insertBeans(List.of());
		dao.loadBean(Bean.class, id("a"));

		dao.updateBeans(List.of());
		dao.loadBean(Bean.class, id("a"));

		dao.saveBeans(List.of());
		dao.loadBean(Bean.class, id("a"));

		assertEquals(4, delegate.loads.get());

		// the delegate evicts what a bulk write changed on its own, so this layer
		// invalidates only its own cache rather than clearing the delegate again
		assertEquals(List.of("insertBeans", "updateBeans", "saveBeans"), delegate.writes);
	}

	@Test
	public void testUpdateRepublishesTheRow() {
		delegate.rows.put("a", new Bean("a", "one"));
		dao.loadBean(Bean.class, id("a"));

		final var replacement = new Bean("a", "two");
		dao.updateBean(replacement);

		assertSame(replacement, dao.loadBean(Bean.class, id("a")));
		assertEquals(1, delegate.loads.get());
	}

	@Test
	public void testNullRefreshTtlLeavesTheLayerInert() {
		refreshTtl = null;
		delegate.rows.put("a", new Bean("a", "one"));

		dao.loadBean(Bean.class, id("a"));
		dao.loadBean(Bean.class, id("a"));
		dao.searchBeans(Bean.class, Map.of(), false, 0);
		dao.searchBeans(Bean.class, Map.of(), false, 0);

		assertEquals(2, delegate.loads.get(), "a read was cached while caching was disabled");
		assertEquals(2, delegate.searches.get());
	}

	@Test
	public void testCachingCanBeEnabledInPlace() {
		refreshTtl = null;
		delegate.rows.put("a", new Bean("a", "one"));
		dao.loadBean(Bean.class, id("a"));
		dao.loadBean(Bean.class, id("a"));
		assertEquals(2, delegate.loads.get());

		refreshTtl = Duration.ofMinutes(5L);
		final var cached = dao.loadBean(Bean.class, id("a"));
		assertSame(cached, dao.loadBean(Bean.class, id("a")));
		assertEquals(3, delegate.loads.get());
	}
}
