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
package edu.iu.dao;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import edu.iu.dao.spi.IuDaoSpi;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Data-access facade over a single {@link DataSource}, providing generated SQL
 * for JPA-annotated entities alongside ad-hoc JDBC operations.
 *
 * <p>
 * Obtain an instance from
 * {@link #of(DataSource, TransactionManager, TransactionSynchronizationRegistry)}.
 * Entity mapping is driven by the annotations described on {@link IuSqlBuilder},
 * so an entity needs {@link jakarta.persistence.Table @Table} plus
 * {@link jakarta.persistence.Column @Column} or {@link SqlColumn @SqlColumn}
 * getters. An entity type may be a class with a no-argument constructor, which is
 * instantiated and populated through its setters, or an interface, which is
 * materialized as an immutable view of the row it was read from.
 * </p>
 *
 * <p>
 * Methods fall into two families. The {@code get*} family returns an unexecuted
 * {@link ParameterizedSql} the caller drives and closes; see that interface for
 * which accessors close themselves. The remaining methods —
 * {@link #loadBean(Class, Map)}, {@link #searchBeans(Class, Map)},
 * {@link #updateBean(Object)}, {@link #saveBean(Object)},
 * {@link #deleteBean(Object)}, and the bulk variants — execute immediately and
 * leave no resources open.
 * </p>
 *
 * <p>
 * A DAO holds no per-call state and may be shared between threads. Reads
 * performed by {@link #loadBean(Class, Map)} and
 * {@link #searchBeans(Class, Map, boolean, int)} are cached, but only for the
 * duration of an active transaction and only in that transaction's own
 * {@link TransactionSynchronizationRegistry} resources, so cached rows can never
 * outlive the transaction that read them or leak across threads. Writes through
 * this facade evict the affected entity type; direct SQL does not, so call
 * {@link #clear()} after modifying rows by other means.
 * </p>
 *
 * @see IuSqlBuilder
 */
public interface IuDao {

	/**
	 * Creates a DAO from the first {@link IuDaoSpi} provider installed alongside
	 * this API.
	 *
	 * @param dataSource                          JDBC source used for every
	 *                                            operation
	 * @param transactionManager                  transaction manager associated
	 *                                            with {@code dataSource}, used to
	 *                                            detect whether a transaction is
	 *                                            active
	 * @param transactionSynchronizationRegistry  registry in which
	 *                                            transaction-scoped read caches are
	 *                                            stored
	 * @return DAO created by the installed provider
	 * @throws IllegalStateException if no provider is installed
	 * @throws NullPointerException  if any argument is {@code null}
	 */
	static IuDao of(DataSource dataSource, TransactionManager transactionManager,
			TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
		Objects.requireNonNull(dataSource, "dataSource");
		Objects.requireNonNull(transactionManager, "transactionManager");
		Objects.requireNonNull(transactionSynchronizationRegistry, "transactionSynchronizationRegistry");
		return ServiceLoader.load(IuDaoSpi.class, IuDaoSpi.class.getClassLoader()).findFirst()
				.orElseThrow(() -> new IllegalStateException("No IuDao service provider is installed"))
				.create(dataSource, transactionManager, transactionSynchronizationRegistry);
	}

	/**
	 * Reads the database's own metadata for a table, retrying with the upper-case
	 * form of the name when the name as given does not match.
	 *
	 * @param tableName table name, matched case-sensitively before being retried
	 *                  upper-cased
	 * @return metadata for the table and its columns
	 * @throws jakarta.persistence.EntityNotFoundException if no table matches
	 *                                                     either form of the name
	 * @throws IllegalStateException                       if the metadata cannot be
	 *                                                     read
	 * @throws NullPointerException                        if {@code tableName} is
	 *                                                     {@code null}
	 */
	TableDefinition getTableDefinition(String tableName);

	/**
	 * Gets an unexecuted statement for SQL that takes no bind arguments.
	 *
	 * @param sql SQL text to execute
	 * @return unexecuted statement
	 */
	default SqlStatement getStatement(String sql) {
		return getStatement(sql, Collections.emptyList());
	}

	/**
	 * Gets an unexecuted statement for SQL and its bind arguments.
	 *
	 * @param sql  SQL text to execute
	 * @param args bind arguments in placeholder order; may be {@code null} or empty
	 *             when the SQL has no placeholders
	 * @return unexecuted statement
	 */
	SqlStatement getStatement(String sql, Iterable<?> args);

	/**
	 * Gets an unexecuted query selecting an entity's mapped columns, restricted by
	 * caller-supplied predicates that take no bind arguments.
	 *
	 * @param beanClass entity type to select and materialize
	 * @param where     SQL predicates, each without the {@code WHERE} keyword,
	 *                  combined with {@code AND}
	 * @param <B>       entity type
	 * @return unexecuted query
	 */
	default <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where) {
		return getBeanQuery(beanClass, where, Collections.emptyList());
	}

	/**
	 * Gets an unexecuted query selecting an entity's mapped columns, restricted by
	 * caller-supplied predicates.
	 *
	 * @param beanClass entity type to select and materialize
	 * @param where     SQL predicates, each without the {@code WHERE} keyword,
	 *                  combined with {@code AND}
	 * @param args      bind arguments in the order the predicates' placeholders
	 *                  appear
	 * @param <B>       entity type
	 * @return unexecuted query
	 */
	<B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where, Iterable<?> args);

	/**
	 * Gets an unexecuted, ordered query selecting an entity's mapped columns.
	 *
	 * @param beanClass entity type to select and materialize
	 * @param where     SQL predicates, each without the {@code WHERE} keyword,
	 *                  combined with {@code AND}
	 * @param order     {@code ORDER BY} expressions, without the {@code ORDER BY}
	 *                  keywords
	 * @param args      bind arguments in the order the predicates' placeholders
	 *                  appear
	 * @param <B>       entity type
	 * @return unexecuted query
	 */
	<B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where, Iterable<String> order,
			Iterable<?> args);

	/**
	 * Loads the one entity matching a key, reading it from the transaction-scoped
	 * cache when the same key was already loaded in the active transaction.
	 *
	 * @param beanClass entity type to load
	 * @param idParams  key values, each keyed by mapped property name or by column
	 *                  name
	 * @param <B>       entity type
	 * @return the matching entity; never {@code null}
	 * @throws jakarta.persistence.EntityNotFoundException  if no row matches
	 * @throws jakarta.persistence.NonUniqueResultException if more than one row
	 *                                                      matches
	 * @throws NullPointerException                         if {@code idParams} is
	 *                                                      {@code null}
	 */
	<B> B loadBean(Class<B> beanClass, Map<String, ?> idParams);

	/**
	 * Gets an unexecuted query matching an entity by key, bypassing the cache.
	 *
	 * @param beanClass entity type to select and materialize
	 * @param idParams  key values, each keyed by mapped property name or by column
	 *                  name
	 * @param <B>       entity type
	 * @return unexecuted query
	 * @throws NullPointerException if {@code idParams} is {@code null}
	 */
	<B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Map<String, ?> idParams);

	/**
	 * Searches for every entity matching a key.
	 *
	 * @param beanClass entity type to search
	 * @param idParams  key values, each keyed by mapped property name or by column
	 *                  name
	 * @param <B>       entity type
	 * @return unmodifiable matching entities, empty when none match
	 * @throws NullPointerException if {@code idParams} is {@code null}
	 */
	default <B> List<B> searchBeans(Class<B> beanClass, Map<String, ?> idParams) {
		return searchBeans(beanClass, idParams, false, 0);
	}

	/**
	 * Searches for every entity matching a key.
	 *
	 * <p>
	 * The observation flag is accepted for source compatibility and ignored:
	 * tracking loaded entities for later comparison belongs to the application
	 * lifecycle, not to this DAO. Pass the original entity to
	 * {@link #getBeanUpdate(Object, Supplier)} to get a delta update instead.
	 * </p>
	 *
	 * @param beanClass entity type to search
	 * @param idParams  key values, each keyed by mapped property name or by column
	 *                  name
	 * @param observe   ignored
	 * @param <B>       entity type
	 * @return unmodifiable matching entities, empty when none match
	 * @throws NullPointerException if {@code idParams} is {@code null}
	 */
	default <B> List<B> searchBeans(Class<B> beanClass, Map<String, ?> idParams, boolean observe) {
		return searchBeans(beanClass, idParams, observe, 0);
	}

	/**
	 * Searches for the entities matching a key, up to a row limit.
	 *
	 * @param beanClass  entity type to search
	 * @param idParams   key values, each keyed by mapped property name or by column
	 *                   name
	 * @param maxResults maximum number of rows to read; zero for unbounded
	 * @param <B>        entity type
	 * @return unmodifiable matching entities, empty when none match
	 * @throws IllegalArgumentException if {@code maxResults} is negative
	 * @throws NullPointerException     if {@code idParams} is {@code null}
	 */
	default <B> List<B> searchBeans(Class<B> beanClass, Map<String, ?> idParams, int maxResults) {
		return searchBeans(beanClass, idParams, false, maxResults);
	}

	/**
	 * Searches for the entities matching a key, up to a row limit, reading them from
	 * the transaction-scoped cache when the same key and limit were already searched
	 * in the active transaction.
	 *
	 * @param beanClass  entity type to search
	 * @param idParams   key values, each keyed by mapped property name or by column
	 *                   name
	 * @param observe    ignored; see {@link #searchBeans(Class, Map, boolean)}
	 * @param maxResults maximum number of rows to read; zero for unbounded
	 * @param <B>        entity type
	 * @return unmodifiable matching entities, empty when none match
	 * @throws IllegalArgumentException if {@code maxResults} is negative
	 * @throws NullPointerException     if {@code idParams} is {@code null}
	 */
	<B> List<B> searchBeans(Class<B> beanClass, Map<String, ?> idParams, boolean observe, int maxResults);

	/**
	 * Gets an unexecuted update for every mapped non-key column of the entity's
	 * primary table, matched on its key columns.
	 *
	 * @param bean entity carrying both the new values and the key to match
	 * @return unexecuted update statement
	 * @throws IuSqlUnchangedException if the entity's primary table has no mapped
	 *                                non-key column to update
	 * @throws NullPointerException   if {@code bean} is {@code null}
	 */
	SqlStatement getBeanUpdate(Object bean);

	/**
	 * Gets an unexecuted delta update covering only the columns whose values differ
	 * from the entity as originally read.
	 *
	 * <p>
	 * {@code passive} is invoked at most once, and only if a comparison is actually
	 * needed, so callers may supply an original that is itself expensive to load.
	 * </p>
	 *
	 * @param bean    entity carrying both the new values and the key to match
	 * @param passive supplies the entity's original state; when {@code null}, or
	 *                when it supplies {@code null}, every mapped non-key column is
	 *                updated
	 * @return unexecuted update statement
	 * @throws IuSqlUnchangedException if no mapped column differs from the original
	 * @throws NullPointerException    if {@code bean} is {@code null}
	 */
	SqlStatement getBeanUpdate(Object bean, Supplier<?> passive);

	/**
	 * Updates the row matching an entity's key and evicts that entity type from the
	 * transaction-scoped cache.
	 *
	 * <p>
	 * An entity whose mapped columns all match the database is left alone rather
	 * than reported as an error.
	 * </p>
	 *
	 * <p>
	 * An {@link EffectiveDated} entity is not modified in place: a new row
	 * superseding its current one is inserted instead, carrying the changed values
	 * and copying the rest. Nothing matching the entity's key means there is no row
	 * to supersede, which is reported as an error the same way it is for an ordinary
	 * entity.
	 * </p>
	 *
	 * @param bean entity carrying both the new values and the key to match
	 * @throws jakarta.persistence.EntityNotFoundException  if no row matches the
	 *                                                      entity's key
	 * @throws jakarta.persistence.NonUniqueResultException if more than one row was
	 *                                                      updated
	 * @throws NullPointerException                         if {@code bean} is
	 *                                                      {@code null}
	 */
	void updateBean(Object bean);

	/**
	 * Gets an unexecuted insert covering every mapped column of the entity's primary
	 * table.
	 *
	 * @param bean entity carrying the values to insert
	 * @return unexecuted insert statement
	 * @throws NullPointerException if {@code bean} is {@code null}
	 */
	SqlStatement getBeanInsert(Object bean);

	/**
	 * Updates the row matching an entity's key, inserting it instead when no row
	 * matches, and evicts that entity type from the transaction-scoped cache.
	 *
	 * @param bean entity to update or insert
	 * @throws jakarta.persistence.NonUniqueResultException if more than one row was
	 *                                                      affected
	 * @throws NullPointerException                         if {@code bean} is
	 *                                                      {@code null}
	 */
	void saveBean(Object bean);

	/**
	 * Gets an unexecuted delete matching an entity on its key columns.
	 *
	 * @param bean entity carrying the key to match
	 * @return unexecuted delete statement
	 * @throws NullPointerException if {@code bean} is {@code null}
	 */
	SqlStatement getBeanDelete(Object bean);

	/**
	 * Deletes the row matching an entity's key and evicts that entity type from the
	 * transaction-scoped cache.
	 *
	 * @param bean entity carrying the key to match
	 * @throws jakarta.persistence.EntityNotFoundException  if no row matches the
	 *                                                      entity's key
	 * @throws jakarta.persistence.NonUniqueResultException if more than one row was
	 *                                                      deleted
	 * @throws NullPointerException                         if {@code bean} is
	 *                                                      {@code null}
	 */
	void deleteBean(Object bean);

	/**
	 * Gets an unexecuted query over caller-supplied SQL, materializing each row by
	 * matching result-set column labels to the target type's properties.
	 *
	 * @param beanClass type to materialize, which need not be a mapped entity
	 * @param sql       SQL text to execute
	 * @param <B>       result type
	 * @return unexecuted query
	 */
	default <B> SqlQuery<B> getQuery(Class<B> beanClass, String sql) {
		return getQuery(beanClass, sql, Collections.emptyList());
	}

	/**
	 * Gets an unexecuted query over caller-supplied SQL and its bind arguments,
	 * materializing each row by matching result-set column labels to the target
	 * type's properties.
	 *
	 * <p>
	 * A mapped entity's labels are resolved through its
	 * {@link jakarta.persistence.Column @Column} and {@link SqlColumn @SqlColumn}
	 * mappings; any label that resolves to no mapped property, and every label of an
	 * unmapped type, falls back to matching the property name ignoring case and
	 * underscores. Labels matching no property are ignored.
	 * </p>
	 *
	 * <p>
	 * A value is read as the type its column stores and converted to the type its
	 * member declares, reversing what {@link IuSqlBuilder#getForSql(Object, String)}
	 * does when binding the same member: a character column mapped to a boolean reads
	 * back {@code "Y"} as {@code true}, a timestamp column mapped to an
	 * {@link java.time.Instant} reads back as an {@code Instant}, and the single
	 * space standing in for null on a {@link SpaceForNull} column reads back as
	 * {@code null}. A member belonging to no mapped column is read as its own type.
	 * </p>
	 *
	 * <p>
	 * A class is instantiated through its no-argument constructor and populated
	 * through its setters, skipping values that would assign {@code null} to a
	 * primitive property. An interface is instead materialized as an immutable view
	 * of the row's resolved values: its getters return those values, getters for
	 * columns the query did not select return {@code null} or the appropriate
	 * primitive default, its {@code default} methods run as written, and any other
	 * abstract method throws {@link UnsupportedOperationException}. Such a view stays
	 * valid after the query is closed.
	 * </p>
	 *
	 * @param beanClass type to materialize, which need not be a mapped entity
	 * @param sql       SQL text to execute
	 * @param args      bind arguments in placeholder order; may be {@code null} or
	 *                  empty
	 * @param <B>       result type
	 * @return unexecuted query
	 * @throws IllegalArgumentException if {@code beanClass} is a class that cannot be
	 *                                  instantiated, thrown when a row is
	 *                                  materialized
	 */
	<B> SqlQuery<B> getQuery(Class<B> beanClass, String sql, Iterable<?> args);

	/**
	 * Gets an unexecuted query over caller-supplied SQL, materializing each row with
	 * a caller-supplied factory instead of by property mapping.
	 *
	 * @param factory reads the result set's current row and returns the materialized
	 *                value; must not advance the cursor
	 * @param sql     SQL text to execute
	 * @param <B>     result type
	 * @return unexecuted query
	 */
	default <B> SqlQuery<B> getFactoryQuery(Function<ResultSet, B> factory, String sql) {
		return getFactoryQuery(factory, sql, Collections.emptyList());
	}

	/**
	 * Gets an unexecuted query over caller-supplied SQL and its bind arguments,
	 * materializing each row with a caller-supplied factory instead of by property
	 * mapping.
	 *
	 * <p>
	 * A factory returning {@code null} ends the traversal, so it must return a
	 * non-{@code null} value for every row that should be visible to the caller.
	 * </p>
	 *
	 * @param factory reads the result set's current row and returns the materialized
	 *                value; must not advance the cursor
	 * @param sql     SQL text to execute
	 * @param args    bind arguments in placeholder order; may be {@code null} or
	 *                empty
	 * @param <B>     result type
	 * @return unexecuted query
	 */
	<B> SqlQuery<B> getFactoryQuery(Function<ResultSet, B> factory, String sql, Iterable<?> args);

	/**
	 * Discards every cached read for the active transaction, so that subsequent
	 * loads and searches go to the database.
	 *
	 * <p>
	 * Does nothing when no transaction is active, since nothing is cached outside a
	 * transaction.
	 * </p>
	 *
	 * @throws IllegalStateException if the transaction status cannot be determined
	 */
	void clear();

	/**
	 * Discards the active transaction's cached reads for one entity type, leaving
	 * other types cached.
	 *
	 * @param beanClass entity type to evict
	 * @throws IllegalStateException if the transaction status cannot be determined
	 * @throws NullPointerException  if {@code beanClass} is {@code null}
	 */
	void clear(Class<?> beanClass);

	/**
	 * Inserts each entity in turn, then discards every cached read for the active
	 * transaction.
	 *
	 * <p>
	 * The entities are not inserted atomically: on failure the entities already
	 * inserted stay inserted unless an enclosing transaction rolls them back.
	 * </p>
	 *
	 * @param beans entities to insert, which need not share a type
	 * @throws jakarta.persistence.EntityNotFoundException  if an insert affects no
	 *                                                      row
	 * @throws jakarta.persistence.NonUniqueResultException if an insert affects more
	 *                                                      than one row
	 */
	void insertBeans(Iterable<?> beans);

	/**
	 * Updates each entity in turn, as if by {@link #updateBean(Object)}.
	 *
	 * <p>
	 * The entities are not updated atomically: on failure the entities already
	 * updated stay updated unless an enclosing transaction rolls them back.
	 * </p>
	 *
	 * @param beans entities to update, which need not share a type
	 * @throws jakarta.persistence.EntityNotFoundException  if no row matches an
	 *                                                      entity's key
	 * @throws jakarta.persistence.NonUniqueResultException if an update affects more
	 *                                                      than one row
	 */
	void updateBeans(Iterable<?> beans);

	/**
	 * Updates or inserts each entity in turn, as if by {@link #saveBean(Object)}.
	 *
	 * <p>
	 * The entities are not saved atomically: on failure the entities already saved
	 * stay saved unless an enclosing transaction rolls them back.
	 * </p>
	 *
	 * @param beans entities to update or insert, which need not share a type
	 * @throws jakarta.persistence.NonUniqueResultException if a save affects more
	 *                                                      than one row
	 */
	void saveBeans(Iterable<?> beans);
}
