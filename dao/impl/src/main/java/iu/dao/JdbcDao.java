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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.io.CharArrayReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import edu.iu.dao.ColumnDefinition;
import edu.iu.dao.IuDao;
import edu.iu.dao.IuSqlBuilder;
import edu.iu.dao.IuSqlUnchangedException;
import edu.iu.dao.ParameterizedSql;
import edu.iu.dao.SqlQuery;
import edu.iu.dao.SqlStatement;
import edu.iu.dao.TableDefinition;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Default JDBC implementation of {@link IuDao}.
 *
 * <p>
 * Connections are obtained only when an operation executes and are returned as
 * soon as its result is consumed, so no connection or result-set state is shared
 * between DAO calls. Read results are cached only while a transaction is active,
 * in that transaction's own {@link TransactionSynchronizationRegistry} resources;
 * this avoids stale process-wide data and makes {@link #clear()} deterministic.
 * </p>
 *
 * <p>
 * The DAO itself is immutable and safe to share between threads. The
 * {@link ParameterizedSql} instances it returns own JDBC resources and are not.
 * </p>
 */
public final class JdbcDao implements IuDao {

	/**
	 * Key under which the transaction-scoped read cache is stored in the
	 * {@link TransactionSynchronizationRegistry}.
	 *
	 * <p>
	 * Deliberately a {@link String} rather than a bare {@link Object} so that every
	 * DAO in the process shares one cache per transaction, which keeps eviction by
	 * {@link #clear(Class)} correct when an application holds more than one DAO over
	 * the same data source.
	 * </p>
	 */
	private static final Object CACHE_RESOURCE = JdbcDao.class.getName() + ".cache";

	/**
	 * Identifies one cached read.
	 *
	 * <p>
	 * The parameter map is copied and wrapped on construction so that a caller
	 * mutating the map it passed to {@link #searchBeans(Class, Map, boolean, int)}
	 * cannot corrupt the key's hash code. {@code maxResults} participates in
	 * equality because a capped search and an uncapped one over the same key are
	 * different results; {@link #loadBean(Class, Map)} uses {@code -1} so that its
	 * single-row entries can never collide with a search.
	 *
	 * @param type       cached entity type
	 * @param parameters key values the read was performed with
	 * @param maxResults row cap the read was performed with, or {@code -1} for a
	 *                   single-entity load
	 */
	private record EntityKey(Class<?> type, Map<String, ?> parameters, int maxResults) {
		private EntityKey {
			parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
		}
	}

	private final DataSource dataSource;
	private final TransactionManager transactionManager;
	private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;
	private final IuSqlBuilder sqlBuilder;

	/**
	 * Creates a DAO using the default SQL builder.
	 *
	 * @param dataSource                         JDBC source
	 * @param transactionManager                 transaction manager associated with
	 *                                           the source
	 * @param transactionSynchronizationRegistry transaction resource registry
	 */
	public JdbcDao(DataSource dataSource, TransactionManager transactionManager,
			TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
		this(dataSource, transactionManager, transactionSynchronizationRegistry, new IuSqlBuilderImpl());
	}

	/**
	 * Creates a DAO with an explicit SQL builder, primarily for integration
	 * adapters that generate dialect-specific SQL.
	 *
	 * @param dataSource                         JDBC source
	 * @param transactionManager                 transaction manager associated with
	 *                                           the source
	 * @param transactionSynchronizationRegistry transaction resource registry
	 * @param sqlBuilder                         SQL generator for mapped entities
	 */
	public JdbcDao(DataSource dataSource, TransactionManager transactionManager,
			TransactionSynchronizationRegistry transactionSynchronizationRegistry, IuSqlBuilder sqlBuilder) {
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
		this.transactionSynchronizationRegistry = Objects.requireNonNull(transactionSynchronizationRegistry,
				"transactionSynchronizationRegistry");
		this.sqlBuilder = Objects.requireNonNull(sqlBuilder, "sqlBuilder");
	}

	@Override
	public TableDefinition getTableDefinition(String tableName) {
		Objects.requireNonNull(tableName, "tableName");
		try (var connection = dataSource.getConnection()) {
			final var metadata = connection.getMetaData();

			// Most databases fold unquoted identifiers to upper case, so a lower-case
			// name from the caller only matches on the second attempt.
			TableMeta table = findTable(metadata, tableName);
			if (table == null)
				table = findTable(metadata, tableName.toUpperCase(Locale.ROOT));
			if (table == null)
				throw new EntityNotFoundException("Table " + tableName);

			// Narrow the column lookup to the table actually found; passing the caller's
			// name would re-match any same-named table in another catalog or schema.
			final var columns = new ArrayList<ColumnDefinition>();
			try (var results = metadata.getColumns(table.catalog, table.schema, table.name, null)) {
				while (results.next())
					columns.add(new ColumnMeta(results));
			}
			return new TableMeta(table.catalog, table.schema, table.name, table.type, List.copyOf(columns));
		} catch (SQLException e) {
			throw new IllegalStateException("Unable to read metadata for table " + tableName, e);
		}
	}

	/**
	 * Reads the first table matching a name pattern, without its columns.
	 *
	 * @param metadata  live database metadata
	 * @param tableName table name to match
	 * @return metadata for the first matching table, or {@code null} when none
	 *         matches
	 * @throws SQLException if the metadata query fails
	 */
	private static TableMeta findTable(DatabaseMetaData metadata, String tableName) throws SQLException {
		final var esc = metadata.getSearchStringEscape();
		final var pattern = tableName.replace(esc, esc + esc)
		        .replace("_", esc + "_").replace("%", esc + "%");
		try (var results = metadata.getTables(null, null, pattern, null)) {
			return results.next() ? new TableMeta(results, List.of()) : null;
		}
	}

	@Override
	public SqlStatement getStatement(String sql, Iterable<?> args) {
		return new StatementOperation(sql, args);
	}

	/**
	 * Materializes the result set's current row as an instance of the entity type.
	 *
	 * <p>
	 * A class is instantiated through its no-argument constructor, which may be
	 * non-public, and populated through its setters. An interface cannot be
	 * instantiated, so it is proxied over an immutable snapshot of the row's resolved
	 * values; see {@link ResolvedRow}. Taking the snapshot rather than holding the
	 * result set is what allows a materialized row to outlive the cursor it came
	 * from, which every accessor that accumulates rows depends on.
	 * </p>
	 *
	 * @param entityClass   entity type to materialize
	 * @param columnMembers member each column supplies, in column order,
	 *                      {@code null} where the column maps to nothing
	 * @param resultSet     result set positioned at the row to materialize
	 * @param <B>           entity type
	 * @return the materialized row
	 * @throws IllegalArgumentException if {@code entityClass} is a class that cannot
	 *                                  be instantiated
	 * @throws SQLException             if a column cannot be read
	 */
	private static <B> B getEntityInstance(Class<B> entityClass, ColumnMember[] columnMembers, ResultSet resultSet)
			throws SQLException {
		if (entityClass.isInterface())
			return entityClass.cast(Proxy.newProxyInstance(entityClass.getClassLoader(),
					new Class<?>[] { entityClass }, new ResolvedRow(entityClass, resolvedValues(columnMembers, resultSet))));

		final B bean;
		try {
			final var constructor = entityClass.getDeclaredConstructor();
			if (!constructor.canAccess(null))
				constructor.setAccessible(true);
			bean = constructor.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("Cannot instantiate " + entityClass.getName(), e);
		}

		for (int i = 0; i < columnMembers.length; i++) {
			final var member = columnMembers[i];
			if (member != null)
				member.set(bean, member.fromSql(columnValue(resultSet, i + 1, member.sqlType())));
		}
		return bean;
	}

	/**
	 * Reads every mapped column of the current row into an immutable map keyed by
	 * member name.
	 *
	 * @param columnMembers member each column supplies, in column order,
	 *                      {@code null} where the column maps to nothing
	 * @param resultSet     result set positioned at the row to read
	 * @return resolved values by member name, retaining {@code null} column values
	 * @throws SQLException if a column cannot be read
	 */
	private static Map<String, Object> resolvedValues(ColumnMember[] columnMembers, ResultSet resultSet)
			throws SQLException {
		final Map<String, Object> values = new LinkedHashMap<>();
		for (int i = 0; i < columnMembers.length; i++) {
			final var member = columnMembers[i];
			if (member != null)
				values.put(member.name(), member.fromSql(columnValue(resultSet, i + 1, member.sqlType())));
		}
		return Collections.unmodifiableMap(values);
	}

	/**
	 * One entity member a result-set column can be assigned to.
	 *
	 * <p>
	 * A member is normally a bean property, written through its setter. A column
	 * mapped on a field with no setter — either a field with no bean property at all,
	 * or a read-only property — is written through the field instead.
	 * </p>
	 *
	 * @param name   property or field name
	 * @param type   declared type of the member, which the column value is read as
	 * @param setter setter to write through, or {@code null} to write the field
	 * @param field  field to write through when there is no setter
	 */
	private record ColumnMember(String name, Class<?> type, Method setter, Field field, ColumnMetaData column) {

		/**
		 * Gets the type this member's column value is read from the result set as.
		 *
		 * <p>
		 * For a mapped column that is its declared SQL type, which is not always the
		 * member's own type — a flag stored in a character column reads as text and is
		 * converted afterwards by {@link #fromSql(Object)}. Reading it as a boolean
		 * instead would parse {@code "Y"} as {@code false}, leaving nothing to convert.
		 * </p>
		 *
		 * @return type to read the column as
		 */
		private Class<?> sqlType() {
			return column == null //
					? type
					: column.sqlType;
		}

		/**
		 * Converts a value read from the database to the type this member expects.
		 *
		 * @param value value read as {@link #sqlType()}
		 * @return value converted for this member, unchanged when the member belongs to
		 *         no mapped column and so has no declared SQL type to convert from
		 */
		private Object fromSql(Object value) {
			return column == null //
					? value
					: column.normalizeResult(value);
		}

		/**
		 * Assigns one column value to this member.
		 *
		 * <p>
		 * A null column targeting a primitive is skipped rather than rejected, leaving
		 * the member at its default: SQL nullability and Java primitiveness are
		 * independent, and a nullable column mapped to {@code int} is common enough that
		 * failing would be unhelpful.
		 * </p>
		 *
		 * @param entity entity being populated
		 * @param value  value to assign; may be {@code null}
		 * @throws IllegalArgumentException if the member is inaccessible or the write
		 *                                  throws
		 */
		private void set(Object entity, Object value) {
			if (value == null && type.isPrimitive())
				return;
			try {
				if (setter == null)
					field.set(entity, value);
				else
					setter.invoke(entity, value);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new IllegalArgumentException("Unable to set " + name + " on " + entity.getClass().getName(), e);
			}
		}
	}

	@Override
	public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where, Iterable<?> args) {
		return getQuery(beanClass, sqlBuilder.getSelectStatement(beanClass, where), args);
	}

	@Override
	public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where, Iterable<String> order,
			Iterable<?> args) {
		return getQuery(beanClass, sqlBuilder.getOrderedSelectStatement(beanClass, where, order), args);
	}

	@Override
	public <B> B loadBean(Class<B> beanClass, Map<String, ?> idParams) {
		final var key = new EntityKey(beanClass, requireParameters(idParams), -1);
		final var cached = cached(key);
		if (cached != null)
			return beanClass.cast(cached.get(0));
		final var bean = getBeanQuery(beanClass, idParams).getSingleResult();
		cache(key, List.of(bean));
		return bean;
	}

	@Override
	public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Map<String, ?> idParams) {
		final var parameters = requireParameters(idParams);
		return getBeanQuery(beanClass, sqlBuilder.getBeanKeyCriteria(beanClass, parameters),
				sqlBuilder.getBeanKeyArgs(beanClass, parameters));
	}

	@Override
	public <B> List<B> searchBeans(Class<B> beanClass, Map<String, ?> idParams, boolean observe, int maxResults) {
		if (maxResults < 0)
			throw new IllegalArgumentException("maxResults must not be negative");
		final var parameters = requireParameters(idParams);
		final var key = new EntityKey(beanClass, parameters, maxResults);
		final var cached = cached(key);
		if (cached != null)
			return castResults(cached);

		// Snapshot the builder's lazy iterables: the query is read once here, but the
		// cached result must not depend on iterables that may not replay.
		final var criteria = new ArrayList<String>();
		for (final var criterion : sqlBuilder.getBeanKeyCriteria(beanClass, parameters))
			criteria.add(criterion);
		final var args = snapshot(sqlBuilder.getBeanKeyArgs(beanClass, parameters));

		final List<B> results;
		try (var query = getBeanQuery(beanClass, criteria, args)) {
			// Stopping the cursor early avoids materializing rows beyond the caller's
			// limit; getResults(int) leaves the query open, hence try-with-resources.
			results = maxResults == 0 ? query.getResults() : query.getResults(maxResults);
		}
		final var limited = Collections.unmodifiableList(results);
		cache(key, limited);
		return limited;
	}

	/**
	 * Re-types a cached result list.
	 *
	 * <p>
	 * Safe because {@link EntityKey#type()} participates in cache-key equality, so a
	 * hit can only have been stored by a read of the same entity type.
	 * </p>
	 *
	 * @param results cached results
	 * @param <B>     entity type
	 * @return the same list, typed
	 */
	@SuppressWarnings("unchecked")
	private static <B> List<B> castResults(List<?> results) {
		return (List<B>) results;
	}

	@Override
	public SqlStatement getBeanUpdate(Object bean) {
		final var entity = Objects.requireNonNull(bean, "bean");
		final var properties = snapshot(sqlBuilder.getUpdateProperties(entity));
		return getStatement(sqlBuilder.getUpdateStatement(entityType(entity), properties),
				sqlBuilder.getUpdateArguments(entity, properties));
	}

	@Override
	public SqlStatement getBeanUpdate(Object bean, Supplier<?> passive) {
		final var entity = Objects.requireNonNull(bean, "bean");
		// The wrapping lambda adapts Supplier<?> to the builder's Supplier<Object>
		// without eagerly resolving the original entity.
		final var properties = snapshot(
				sqlBuilder.getUpdateProperties(entity, passive == null ? null : () -> passive.get()));
		return getStatement(sqlBuilder.getUpdateStatement(entityType(entity), properties),
				sqlBuilder.getUpdateArguments(entity, properties));
	}

	@Override
	public void updateBean(Object bean) {
		Objects.requireNonNull(bean, "bean");
		try {
			assertExactlyOne(getBeanUpdate(bean).execute(), "update", bean);
		} catch (IuSqlUnchangedException e) {
			// An entity that matches the database needs no statement; this is a normal
			// outcome, not a failure.
			return;
		} finally {
			clear(entityType(bean));
		}
	}

	@Override
	public SqlStatement getBeanInsert(Object bean) {
		final var entity = Objects.requireNonNull(bean, "bean");
		return getStatement(sqlBuilder.getInsertStatement(entityType(entity)), sqlBuilder.getInsertArguments(entity));
	}

	@Override
	public void saveBean(Object bean) {
		try {
			updateBean(bean);
		} catch (EntityNotFoundException e) {
			// No row to update, so the entity is new. updateBean() already evicted the
			// type, but the insert changes the data again and must evict once more.
			assertExactlyOne(getBeanInsert(bean).execute(), "insert", bean);
			clear(entityType(bean));
		}
	}

	@Override
	public SqlStatement getBeanDelete(Object bean) {
		final var entity = Objects.requireNonNull(bean, "bean");
		return getStatement(sqlBuilder.getDeleteStatement(entityType(entity)), sqlBuilder.getDeleteArguments(entity));
	}

	@Override
	public void deleteBean(Object bean) {
		Objects.requireNonNull(bean, "bean");
		try {
			assertExactlyOne(getBeanDelete(bean).execute(), "delete", bean);
		} finally {
			clear(entityType(bean));
		}
	}

	/**
	 * Verifies that a key-matched statement affected exactly one row.
	 *
	 * <p>
	 * A count other than one means the generated key criteria did not identify a
	 * single row, which is a mapping or data error rather than a SQL failure.
	 * </p>
	 *
	 * @param count     affected-row count reported by the driver
	 * @param operation operation name, for the failure message
	 * @param bean      entity the statement was generated from
	 * @throws EntityNotFoundException  if no row was affected
	 * @throws NonUniqueResultException if more than one row was affected
	 */
	private static void assertExactlyOne(int count, String operation, Object bean) {
		if (count == 0)
			throw new EntityNotFoundException("No row found on " + operation + " " + bean);
		if (count != 1)
			throw new NonUniqueResultException("Expected one row on " + operation + ", found " + count);
	}

	@Override
	public <B> SqlQuery<B> getQuery(Class<B> beanClass, String sql, Iterable<?> args) {
		Objects.requireNonNull(beanClass, "beanClass");
		return new QueryOperation<>(sql, args, new BeanRowMapper<>(beanClass));
	}

	@Override
	public <B> SqlQuery<B> getFactoryQuery(Function<ResultSet, B> factory, String sql, Iterable<?> args) {
		return new QueryOperation<>(sql, args, Objects.requireNonNull(factory, "factory"));
	}

	@Override
	public void clear() {
		final var cache = cache();
		if (cache != null)
			cache.clear();
	}

	@Override
	public void clear(Class<?> beanClass) {
		Objects.requireNonNull(beanClass, "beanClass");
		final var cache = cache();
		if (cache != null)
			cache.keySet().removeIf(key -> key.type == beanClass);
	}

	@Override
	public void insertBeans(Iterable<?> beans) {
		for (final var bean : beans)
			assertExactlyOne(getBeanInsert(bean).execute(), "insert", bean);
		// The entities need not share a type, so evict everything rather than tracking
		// which types were touched.
		clear();
	}

	@Override
	public void updateBeans(Iterable<?> beans) {
		for (final var bean : beans)
			updateBean(bean);
	}

	@Override
	public void saveBeans(Iterable<?> beans) {
		for (final var bean : beans)
			saveBean(bean);
	}

	/**
	 * Reads one entry from the transaction-scoped cache.
	 *
	 * @param key cache key
	 * @return cached results, or {@code null} when not cached or no transaction is
	 *         active
	 */
	private List<?> cached(EntityKey key) {
		final var cache = cache();
		return cache == null ? null : cache.get(key);
	}

	/**
	 * Stores one entry in the transaction-scoped cache, silently skipping the write
	 * when no transaction is active.
	 *
	 * @param key     cache key
	 * @param results results to cache
	 */
	private void cache(EntityKey key, List<?> results) {
		final var cache = cache();
		if (cache != null)
			cache.put(key, results);
	}

	/**
	 * Gets the active transaction's read cache, creating and registering it on first
	 * use.
	 *
	 * <p>
	 * Returns {@code null} outside an active transaction, which disables caching
	 * entirely: a cache with no defined end of life would serve stale rows
	 * indefinitely. Because the cache is registered as a transaction resource, the
	 * transaction manager discards it on commit or rollback, and it is never visible
	 * to another transaction or thread — so the plain {@link HashMap} needs no
	 * synchronization.
	 * </p>
	 *
	 * @return read cache for the active transaction, or {@code null} when no
	 *         transaction is active
	 * @throws IllegalStateException if the transaction status cannot be determined
	 */
	@SuppressWarnings("unchecked")
	private Map<EntityKey, List<?>> cache() {
		try {
			if (transactionManager.getStatus() != Status.STATUS_ACTIVE)
				return null;
		} catch (SystemException e) {
			throw new IllegalStateException("Unable to determine transaction status", e);
		}
		var cache = (Map<EntityKey, List<?>>) transactionSynchronizationRegistry.getResource(CACHE_RESOURCE);
		if (cache == null) {
			cache = new HashMap<>();
			transactionSynchronizationRegistry.putResource(CACHE_RESOURCE, cache);
		}
		return cache;
	}

	/**
	 * Gets the type whose mapping describes an entity instance.
	 *
	 * <p>
	 * An entity read as an interface is a {@link Proxy}, and a proxy class carries
	 * none of the mapping annotations its interface declares — nor is it what
	 * {@link #loadBean(Class, Map)} and {@link #searchBeans(Class, Map, boolean, int)}
	 * key the read cache by. The interface is, so a proxy resolves to the first
	 * interface it implements. Anything else describes itself.
	 * </p>
	 *
	 * @param bean entity instance
	 * @return type to resolve entity metadata and cache entries by
	 */
	private static Class<?> entityType(Object bean) {
		final var type = bean.getClass();
		return Proxy.isProxyClass(type) //
				? type.getInterfaces()[0]
				: type;
	}

	/**
	 * Rejects a {@code null} key-parameter map, naming the API-level parameter in
	 * the failure message.
	 *
	 * @param parameters key parameters supplied by the caller
	 * @return the same map
	 * @throws NullPointerException if {@code parameters} is {@code null}
	 */
	private static Map<String, ?> requireParameters(Map<String, ?> parameters) {
		return Objects.requireNonNull(parameters, "idParams");
	}

	/**
	 * Copies an iterable into a list so that it can be traversed more than once.
	 *
	 * <p>
	 * {@link IuSqlBuilder} returns lazily generated iterables, several of which are
	 * consumed twice — once to build the SQL and once to bind arguments — and a
	 * {@code null} iterable is accepted because the API permits omitting arguments.
	 * </p>
	 *
	 * @param values values to copy; may be {@code null}
	 * @param <T>    value type
	 * @return mutable copy, empty when {@code values} is {@code null}
	 */
	private static <T> List<T> snapshot(Iterable<T> values) {
		final var copy = new ArrayList<T>();
		if (values != null)
			values.forEach(copy::add);
		return copy;
	}

	/**
	 * A row-modifying operation, and the base for {@link QueryOperation}.
	 *
	 * <p>
	 * Holds the connection and statement it opens so that {@link #close()} can
	 * release both, and re-prepares from scratch on each
	 * {@link #getPreparedStatement()} call rather than reusing a statement whose
	 * cursor state is unknown.
	 * </p>
	 */
	private class StatementOperation implements SqlStatement {
		private final String query;
		private final List<?> args;
		private Connection connection;
		private PreparedStatement statement;

		/**
		 * Captures the SQL and its arguments without touching the database.
		 *
		 * @param query SQL text
		 * @param args  bind arguments in placeholder order; may be {@code null}
		 */
		private StatementOperation(String query, Iterable<?> args) {
			this.query = Objects.requireNonNull(query, "sql");
			this.args = snapshot(args);
		}

		@Override
		public String getQuery() {
			return query;
		}

		@Override
		public Iterable<?> getArguments() {
			return Collections.unmodifiableList(args);
		}

		@Override
		public PreparedStatement getPreparedStatement() {
			close();
			try {
				connection = dataSource.getConnection();
				statement = prepare(connection, query, args);
				return statement;
			} catch (SQLException e) {
				// Do not leak a connection that was opened before preparation failed.
				close();
				throw new IllegalStateException("Unable to prepare SQL", e);
			}
		}

		@Override
		public int execute() {
			try {
				return getPreparedStatement().executeUpdate();
			} catch (SQLException e) {
				throw new IllegalStateException("SQL execution failed: " + query, e);
			} finally {
				close();
			}
		}

		@Override
		public void close() {
			closeQuietly(statement);
			closeQuietly(connection);
			statement = null;
			connection = null;
		}

		/**
		 * Describes this operation for diagnostics, without its argument values, which
		 * may be sensitive.
		 *
		 * @return SQL text preceded by the argument count
		 */
		@Override
		public String toString() {
			return "SQL (" + args.size() + " args): " + query;
		}
	}

	/**
	 * A row-returning operation that reads forward through one open cursor.
	 *
	 * <p>
	 * The result set is retained between accessor calls so that
	 * {@link #getResults(int)} and {@link #getResultStream()} resume where the
	 * previous call stopped rather than re-executing.
	 * </p>
	 *
	 * @param <B> materialized row type
	 */
	private final class QueryOperation<B> extends StatementOperation implements SqlQuery<B> {
		private final Function<ResultSet, B> factory;
		private ResultSet resultSet;

		/**
		 * Captures the SQL, its arguments, and the row materializer without touching
		 * the database.
		 *
		 * @param query   SQL text
		 * @param args    bind arguments in placeholder order; may be {@code null}
		 * @param factory materializes the result set's current row
		 */
		private QueryOperation(String query, Iterable<?> args, Function<ResultSet, B> factory) {
			super(query, args);
			this.factory = factory;
		}

		@Override
		public ResultSet getResultSet() {
			if (resultSet == null)
				try {
					resultSet = getPreparedStatement().executeQuery();
				} catch (SQLException e) {
					close();
					throw new IllegalStateException("SQL query failed: " + getQuery(), e);
				}
			return resultSet;
		}

		/**
		 * Advances the cursor one row and materializes it.
		 *
		 * @return the materialized row, or {@code null} once the cursor is exhausted
		 * @throws IllegalStateException if the cursor cannot be advanced
		 */
		private B next() {
			try {
				return getResultSet().next() ? factory.apply(resultSet) : null;
			} catch (SQLException e) {
				throw new IllegalStateException("Unable to read SQL result", e);
			}
		}

		@Override
		public B getSingleResult() {
			try {
				final var result = next();
				if (result == null)
					throw new EntityNotFoundException("No result for SQL: " + getQuery());
				if (getResultSet().next())
					throw new NonUniqueResultException("Expected one result for SQL: " + getQuery());
				return result;
			} catch (SQLException e) {
				throw new IllegalStateException("Unable to inspect SQL result", e);
			} finally {
				close();
			}
		}

		@Override
		public B getFirstRecord() {
			try {
				final var result = next();
				if (result == null)
					throw new EntityNotFoundException("No result for SQL: " + getQuery());
				return result;
			} finally {
				close();
			}
		}

		@Override
		public List<B> getResults() {
			try {
				final var results = new ArrayList<B>();
				B result;
				while ((result = next()) != null)
					results.add(result);
				return results;
			} finally {
				close();
			}
		}

		@Override
		public List<B> getResults(int maxRows) {
			if (maxRows < 1)
				throw new IllegalArgumentException("maxRows must be positive");
			final var results = new ArrayList<B>(maxRows);
			// Deliberately leaves the cursor open so that the next call returns the next
			// page; the caller closes this query.
			while (results.size() < maxRows) {
				final var result = next();
				if (result == null)
					break;
				results.add(result);
			}
			return results;
		}

		@Override
		public Stream<B> getResultStream() {
			// Reads exactly one row per advance, so a short-circuiting terminal operation
			// does not pull the whole cursor into memory. Size is reported as unknown
			// because a forward-only cursor cannot be counted without consuming it.
			final var spliterator = new Spliterators.AbstractSpliterator<B>(Long.MAX_VALUE,
					Spliterator.ORDERED | Spliterator.NONNULL) {
				@Override
				public boolean tryAdvance(Consumer<? super B> action) {
					final var result = QueryOperation.this.next();
					if (result == null) {
						// Release resources as soon as the cursor is exhausted, so a fully
						// consumed stream needs no explicit close.
						QueryOperation.this.close();
						return false;
					}
					action.accept(result);
					return true;
				}
			};
			return StreamSupport.stream(spliterator, false).onClose(this::close);
		}

		@Override
		public void close() {
			closeQuietly(resultSet);
			resultSet = null;
			super.close();
		}
	}

	/**
	 * Materializes result-set rows as instances of a bean type by assigning each
	 * column to the property it maps to.
	 *
	 * <p>
	 * Column-to-property resolution is performed once, on the first row, and reused:
	 * bean introspection and entity-metadata lookup are far more expensive than the
	 * per-row assignments, and a query's column layout cannot change between its
	 * rows. One mapper is created per query, so a mapper is confined to that query's
	 * thread.
	 * </p>
	 *
	 * @param <B> materialized row type
	 */
	private final class BeanRowMapper<B> implements Function<ResultSet, B> {
		private final Class<B> beanClass;
		private ColumnMember[] columnMembers;

		/**
		 * Creates a mapper for one bean type.
		 *
		 * @param beanClass type to materialize
		 */
		private BeanRowMapper(Class<B> beanClass) {
			this.beanClass = beanClass;
		}

		@Override
		public B apply(ResultSet resultSet) {
			try {
				if (columnMembers == null)
					columnMembers = resolveColumnMembers(resultSet.getMetaData());
				return getEntityInstance(beanClass, columnMembers, resultSet);
			} catch (SQLException | IntrospectionException e) {
				throw new IllegalStateException("Unable to map SQL result to " + beanClass.getName(), e);
			}
		}

		/**
		 * Determines which entity member, if any, each result-set column supplies.
		 *
		 * <p>
		 * A column label is first resolved through the entity's
		 * {@link jakarta.persistence.Column @Column} mappings, which is the only way to
		 * reach a member whose name does not resemble its column name. Labels that
		 * resolve to no mapping — every label of an unmapped type, and the
		 * name-derived aliases generated for {@link edu.iu.dao.SqlColumn @SqlColumn}
		 * expressions — fall back to matching the member name ignoring case and
		 * underscores.
		 * </p>
		 *
		 * <p>
		 * A class is populated through its setters, and through its declared fields for
		 * whatever has no setter — a column mapped on a field alone, or on a read-only
		 * property. An interface is not populated at all, only read from, so its members
		 * are its readable properties and it has no fields to consider.
		 * </p>
		 *
		 * @param metadata metadata of the executing query
		 * @return one entry per column, in column order, {@code null} where the column
		 *         maps to no usable member
		 * @throws SQLException           if the metadata cannot be read
		 * @throws IntrospectionException if the bean type cannot be introspected
		 */
		private ColumnMember[] resolveColumnMembers(ResultSetMetaData metadata)
				throws SQLException, IntrospectionException {
			final var readOnly = beanClass.isInterface();
			// Resolved leniently: a type that maps nothing simply contributes no
			// conversions, which is what an ad-hoc query over a plain bean wants.
			final var mappedColumns = EntityMetaData.resolve(beanClass).columns;
			final Map<String, ColumnMember> byName = new HashMap<>();
			final Map<String, ColumnMember> byNormalizedName = new HashMap<>();

			for (final var property : Introspector.getBeanInfo(beanClass).getPropertyDescriptors())
				if (readOnly ? property.getReadMethod() != null : property.getWriteMethod() != null)
					register(byName, byNormalizedName,
							new ColumnMember(property.getName(), property.getPropertyType(),
									property.getWriteMethod(), null, mappedColumns.get(property.getName())));

			if (!readOnly)
				for (final var field : DaoUtils.getAllDeclaredFields(beanClass))
					if (!byName.containsKey(field.getName()))
						register(byName, byNormalizedName, new ColumnMember(field.getName(), field.getType(), null,
								DaoUtils.accessible(field), mappedColumns.get(field.getName())));

			final var resolved = new ColumnMember[metadata.getColumnCount()];
			for (int i = 0; i < resolved.length; i++) {
				final var label = metadata.getColumnLabel(i + 1);
				final var mapped = sqlBuilder.getPropertyNameFromBean(beanClass, label);
				resolved[i] = mapped == null ? byNormalizedName.get(normalize(label)) : byName.get(mapped);
			}
			return resolved;
		}

		/**
		 * Indexes one member by its name and by its normalized name.
		 *
		 * <p>
		 * Only the first member to claim a normalized name keeps it, so that a field
		 * cannot displace the property a label already resolves to.
		 * </p>
		 *
		 * @param byName           index by exact name
		 * @param byNormalizedName index by normalized name
		 * @param member           member to index
		 */
		private void register(Map<String, ColumnMember> byName, Map<String, ColumnMember> byNormalizedName,
				ColumnMember member) {
			byName.put(member.name(), member);
			byNormalizedName.putIfAbsent(normalize(member.name()), member);
		}
	}

	/**
	 * Backs an interface-typed entity with an immutable snapshot of one row's
	 * resolved column values.
	 *
	 * <p>
	 * The snapshot is taken when the row is materialized, so the view neither holds
	 * the result set open nor changes as the cursor advances. It is a read-only view:
	 * getters answer from the snapshot, {@code default} methods run as written
	 * against those getters, and no other abstract method can be honored.
	 * </p>
	 *
	 * @param entityClass proxied entity interface
	 * @param values      resolved column values by property name
	 */
	private record ResolvedRow(Class<?> entityClass, Map<String, Object> values) implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Only equals, hashCode, and toString are dispatched here from Object.
			if (method.getDeclaringClass() == Object.class)
				return switch (method.getName()) {
				case "hashCode" -> values.hashCode();
				case "toString" -> entityClass.getSimpleName() + values;
				default -> args[0] != null //
						&& Proxy.isProxyClass(args[0].getClass()) //
						&& equals(Proxy.getInvocationHandler(args[0]));
				};

			if (method.isDefault())
				return InvocationHandler.invokeDefault(proxy, method, args);

			final var property = propertyName(method);
			if (property == null)
				throw new UnsupportedOperationException(
						method.getName() + " is not a property of " + entityClass.getName());

			// A column the query did not select is indistinguishable from a null one, and
			// both must answer with something the return type accepts.
			final var value = values.get(property);
			return value == null ? unset(method.getReturnType()) : value;
		}

		/**
		 * Derives the property name a method reads, using the same conventions bean
		 * introspection applies.
		 *
		 * @param method method being invoked
		 * @return property name, or {@code null} when the method is not a getter
		 */
		private static String propertyName(Method method) {
			if (method.getParameterCount() > 0)
				return null;
			final var name = method.getName();
			if (name.length() > 3 && name.startsWith("get"))
				return Introspector.decapitalize(name.substring(3));
			if (name.length() > 2 && name.startsWith("is") && method.getReturnType() == boolean.class)
				return Introspector.decapitalize(name.substring(2));
			return null;
		}

		/**
		 * Gets the value a getter must answer with when its column carries no value.
		 *
		 * <p>
		 * A primitive-returning getter cannot answer {@code null} — the proxy would
		 * reject it — so it answers with the same zero value an unassigned field of that
		 * type would hold, obtained from a one-element array rather than enumerated by
		 * type.
		 * </p>
		 *
		 * @param type getter's return type
		 * @return the type's zero value, or {@code null} for a reference type
		 */
		private static Object unset(Class<?> type) {
			return type.isPrimitive() ? Array.get(Array.newInstance(type, 1), 0) : null;
		}
	}

	/**
	 * Prepares a forward-only, read-only statement and binds its arguments.
	 *
	 * <p>
	 * Character and binary arguments are bound through the typed setters their
	 * values imply, because {@code setObject} does not reliably map a Java array or
	 * stream to a large-object column.
	 * </p>
	 *
	 * @param connection open connection
	 * @param sql        SQL text
	 * @param args       bind arguments in placeholder order
	 * @return prepared statement with all arguments bound
	 * @throws SQLException if preparation or binding fails
	 */
	private static PreparedStatement prepare(Connection connection, String sql, Iterable<?> args) throws SQLException {
		final var statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		int index = 1;
		for (final var arg : args) {
			if (arg instanceof char[] chars)
				statement.setClob(index++, new CharArrayReader(chars));
			else if (arg instanceof Reader reader)
				statement.setCharacterStream(index++, reader);
			else if (arg instanceof byte[] bytes)
				statement.setBytes(index++, bytes);
			else
				statement.setObject(index++, arg);
		}
		return statement;
	}

	/**
	 * Closes a JDBC resource, discarding any failure.
	 *
	 * <p>
	 * Cleanup runs from {@code finally} blocks that may already be propagating an
	 * application failure; a close failure there is noise that would mask the real
	 * cause.
	 * </p>
	 *
	 * @param closeable resource to close; may be {@code null}
	 */
	private static void closeQuietly(AutoCloseable closeable) {
		if (closeable != null)
			try {
				closeable.close();
			} catch (Exception e) {
				/* cleanup must not mask application failures */
			}
	}

	/**
	 * Reduces a column or property name to a form that ignores case and word
	 * separators, so that {@code FIRST_NAME} and {@code firstName} compare equal.
	 *
	 * @param name name to reduce
	 * @return normalized name
	 */
	private static String normalize(String name) {
		return name.replace("_", "").toLowerCase(Locale.ROOT);
	}

	/**
	 * Reads one column as the type its target property expects.
	 *
	 * <p>
	 * Types with a dedicated JDBC getter are read through it, since
	 * {@code getObject} returns driver-specific representations for character,
	 * binary, and temporal columns. Anything else is read as an object and coerced
	 * only when the driver's type does not already fit the property.
	 * </p>
	 *
	 * @param resultSet result set positioned at the row to read
	 * @param index     one-based column index
	 * @param type      target property type
	 * @return column value as {@code type}, or {@code null} when the column is null
	 * @throws SQLException if the column cannot be read
	 */
	private static Object columnValue(ResultSet resultSet, int index, Class<?> type) throws SQLException {
		if (type == String.class)
			return resultSet.getString(index);
		if (type == byte[].class)
			return resultSet.getBytes(index);
		if (Reader.class.isAssignableFrom(type))
			return resultSet.getCharacterStream(index);
		if (type == java.sql.Date.class)
			return resultSet.getDate(index);
		if (type == java.sql.Time.class)
			return resultSet.getTime(index);
		if (type == java.sql.Timestamp.class || type == java.util.Date.class)
			return resultSet.getTimestamp(index);

		return DaoUtils.coerce(type, resultSet.getObject(index));
	}

	/**
	 * Detached {@link ColumnDefinition} read from a database metadata row.
	 *
	 * @param name         column name
	 * @param dataType     JDBC type code
	 * @param typeName     vendor type name
	 * @param columnSize   declared column size
	 * @param decimalDigits decimal scale
	 * @param numPrecRadix numeric radix
	 */
	private record ColumnMeta(String name, int dataType, String typeName, int columnSize, int decimalDigits,
			int numPrecRadix) implements ColumnDefinition {

		/**
		 * Copies the metadata row the result set is positioned at.
		 *
		 * @param results metadata result set from
		 *                {@link DatabaseMetaData#getColumns(String, String, String, String)}
		 * @throws SQLException if a column cannot be read
		 */
		private ColumnMeta(ResultSet results) throws SQLException {
			this(results.getString("COLUMN_NAME"), results.getInt("DATA_TYPE"), results.getString("TYPE_NAME"),
					results.getInt("COLUMN_SIZE"), results.getInt("DECIMAL_DIGITS"), results.getInt("NUM_PREC_RADIX"));
		}

		@Override
		public String getColumnName() {
			return name;
		}

		@Override
		public int getDataType() {
			return dataType;
		}

		@Override
		public String getTypeName() {
			return typeName;
		}

		@Override
		public int getColumnSize() {
			return columnSize;
		}

		@Override
		public int getDecimalDigits() {
			return decimalDigits;
		}

		@Override
		public int getNumPrecRadix() {
			return numPrecRadix;
		}
	}

	/**
	 * Detached {@link TableDefinition} read from a database metadata row.
	 *
	 * @param catalog catalog name, or {@code null}
	 * @param schema  schema name, or {@code null}
	 * @param name    table name
	 * @param type    table type
	 * @param columns table columns in database order
	 */
	private record TableMeta(String catalog, String schema, String name, String type,
			Iterable<? extends ColumnDefinition> columns) implements TableDefinition {

		/**
		 * Copies the metadata row the result set is positioned at, attaching columns
		 * read separately.
		 *
		 * @param results metadata result set from
		 *                {@link DatabaseMetaData#getTables(String, String, String, String[])}
		 * @param columns table columns, empty when they have not been read yet
		 * @throws SQLException if a column cannot be read
		 */
		private TableMeta(ResultSet results, Iterable<? extends ColumnDefinition> columns) throws SQLException {
			this(results.getString("TABLE_CAT"), results.getString("TABLE_SCHEM"), results.getString("TABLE_NAME"),
					results.getString("TABLE_TYPE"), Collections.unmodifiableList(snapshot(columns)));
		}

		@Override
		public String getTableCat() {
			return catalog;
		}

		@Override
		public String getTableSchem() {
			return schema;
		}

		@Override
		public String getTableName() {
			return name;
		}

		@Override
		public String getTableType() {
			return type;
		}

		@Override
		public Iterable<? extends ColumnDefinition> getColumns() {
			return columns;
		}
	}
}
