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

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuRefreshableCache;
import edu.iu.IuRefreshableCacheConfiguration;
import edu.iu.IuRefreshableCacheHint;
import edu.iu.dao.IuDao;
import edu.iu.dao.IuSqlBuilder;
import edu.iu.dao.SqlQuery;
import edu.iu.dao.SqlStatement;
import edu.iu.dao.TableDefinition;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Adds a process-wide read cache to a {@link IuDao}.
 *
 * <h2>Two caches that never overlap</h2>
 *
 * <p>
 * The delegate keeps its own read cache, scoped to the transaction it was read
 * in. This one is scoped to the process. They are consulted in different
 * situations and never both answer the same read:
 * </p>
 *
 * <dl>
 * <dt>inside a transaction</dt>
 * <dd>The read is delegated, so it is answered by the transaction's own cache or
 * by the database — never by this one. A transaction sees its own writes and a
 * consistent view of everything else, and a value published by some other
 * caller could honor neither.</dd>
 * <dt>outside a transaction</dt>
 * <dd>The read is answered by this cache, which resolves a miss on a pooled
 * thread. That thread is in no transaction, so the delegate reads through to the
 * database and its own cache stays out of the way.</dd>
 * </dl>
 *
 * <p>
 * A transactional read is still worth something to this cache: it has already
 * paid for a value a later non-transactional reader would have to fetch again.
 * It is therefore <em>injected</em> once the transaction commits, which costs no
 * additional query.
 * </p>
 *
 * <h2>Why everything waits for the commit</h2>
 *
 * <p>
 * Invalidations and injections are both buffered and applied from
 * {@link Synchronization#afterCompletion(int)}, only on
 * {@link Status#STATUS_COMMITTED}. Publishing either one earlier would be wrong
 * in a different way.
 * </p>
 *
 * <p>
 * An invalidation raised before its write commits discards entries that a
 * concurrent reader immediately repopulates from the state being replaced,
 * leaving the value the write was meant to supersede in place until it expires.
 * An injection published before commit exposes uncommitted state, and exposes a
 * value that never existed at all if the transaction rolls back.
 * </p>
 *
 * <p>
 * Deferring both also settles the case of a transaction that reads a row it has
 * itself written: at commit, the value it read <em>is</em> the committed one.
 * </p>
 *
 * <h2>Sequence marks</h2>
 *
 * <p>
 * A value read inside a transaction is published long after it was read, so it
 * carries the {@link IuRefreshableCache#mark() mark} taken before the read
 * rather than being stamped at publication. An invalidation raised in between
 * therefore still discards it, instead of being ordered behind a value it
 * predates.
 * </p>
 *
 * <p>
 * A write republishing the row it just wrote wants the opposite order, so it
 * takes its mark <em>after</em> raising its own invalidation.
 * </p>
 *
 * <h2>What a read publishes</h2>
 *
 * <p>
 * A search publishes each row it read as a load of that row's own primary key,
 * so a search populates the individual lookups for free. This needs no
 * declaration: the {@code @Id} mapping already states which columns identify a
 * row, and {@link IuSqlBuilder#getPrimaryKeyProperties(Class)} reads it.
 * </p>
 *
 * <p>
 * Publication is skipped for any row whose mapped key is not fully resolved, so
 * a projection that omits an id column, or an entity that declares no key,
 * publishes nothing rather than publishing a row under a key no load would have
 * produced.
 * </p>
 *
 * <h2>Queries and statements the caller drives</h2>
 *
 * <p>
 * The {@code get*} family hands back an unexecuted operation rather than a
 * result, so there is nothing to cache at the moment of the call. Each is
 * wrapped instead, and acts when the caller runs it.
 * </p>
 *
 * <dl>
 * <dt>{@link #getBeanQuery(Class, Map)} and its siblings</dt>
 * <dd>Read the cursor once into a list, publish every row as a load of its own
 * key, and answer from the list thereafter. These are generated from the entity
 * mapping and select its full column list, so each row is the same complete
 * entity a load would have produced.</dd>
 * <dt>{@link #getQuery(Class, String, Iterable)} and
 * {@link #getFactoryQuery(java.util.function.Function, String, Iterable)}</dt>
 * <dd>Read once into a list and answer from it, but publish nothing. The SQL is
 * the caller's, so a row may be a partial projection, or carry no entity at
 * all.</dd>
 * <dt>{@link #getBeanUpdate(Object)}, {@link #getBeanInsert(Object)} and
 * {@link #getBeanDelete(Object)}</dt>
 * <dd>Invalidate the entity type and the row's own key when executed, on the
 * same terms as {@link #saveBean(Object)} but without republishing.</dd>
 * <dt>{@link #getStatement(String, Iterable)}</dt>
 * <dd>Passed through untouched. Raw SQL names neither an entity type nor a row,
 * so there is nothing to invalidate by; {@link IuDao}'s standing instruction to
 * call {@link #clear()} after modifying rows by other means still applies, and
 * here it applies to a process-wide cache rather than a transaction-scoped
 * one.</dd>
 * </dl>
 *
 * <p>
 * Reading a cursor to exhaustion is what allows a query to publish the rows it
 * carried, and it is also the one place this layer changes a caller's memory
 * profile: {@link SqlQuery#getResults(int)} still pages, but over rows that have
 * already been materialized. A result set too large to hold in memory therefore
 * needs a DAO built without a refresh TTL, where every operation is passed
 * through unwrapped.
 * </p>
 */
final class CachedDao implements IuDao {

	private static final Logger LOG = Logger.getLogger(CachedDao.class.getName());

	/**
	 * Key under which the buffered work of one transaction is held in that
	 * transaction's {@link TransactionSynchronizationRegistry} resources.
	 */
	private static final Object PENDING_RESOURCE = CachedDao.class.getName() + ".pending";

	/**
	 * Work one transaction has buffered for its own commit.
	 *
	 * <p>
	 * Ordered: invalidations are raised before injections, so a row republished by
	 * a write is not discarded by that same write's invalidation. Each injection
	 * carries the mark taken before the read that produced it.
	 * </p>
	 */
	private static final class Pending {
		private final List<IuRefreshableCacheHint<DaoKey, List<?>>> invalidations = new ArrayList<>();
		private final List<Runnable> injections = new ArrayList<>();
	}

	private final IuDao delegate;
	private final IuSqlBuilder sqlBuilder;
	private final TransactionManager transactionManager;
	private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;
	private final Supplier<IuRefreshableCacheConfiguration> config;
	private final IuRefreshableCache<DaoKey, List<?>> cache;

	/**
	 * Constructor.
	 *
	 * @param delegate                           DAO to read and write through
	 * @param sqlBuilder                         builder the delegate generates
	 *                                           statements with, used here to read
	 *                                           an entity's mapped primary key
	 * @param transactionManager                 transaction manager, for detecting
	 *                                           an active transaction
	 * @param transactionSynchronizationRegistry transaction resource registry, for
	 *                                           buffering work until commit
	 * @param config                             supplies the cache configuration in
	 *                                           effect; a null refresh TTL leaves
	 *                                           this layer inert
	 */
	CachedDao(IuDao delegate, IuSqlBuilder sqlBuilder, TransactionManager transactionManager,
			TransactionSynchronizationRegistry transactionSynchronizationRegistry,
			Supplier<IuRefreshableCacheConfiguration> config) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.sqlBuilder = Objects.requireNonNull(sqlBuilder, "sqlBuilder");
		this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
		this.transactionSynchronizationRegistry = Objects.requireNonNull(transactionSynchronizationRegistry,
				"transactionSynchronizationRegistry");

		this.config = Objects.requireNonNull(config, "config");
		this.cache = new IuRefreshableCache<>(config, this::resolve, this::cacheHint);
	}

	/**
	 * Determines whether the configuration in effect enables caching.
	 *
	 * <p>
	 * A null refresh TTL leaves this layer inert rather than merely empty: reads
	 * are delegated on the calling thread, exactly as they would be without it, so
	 * enabling the layer without configuring it changes nothing a caller can
	 * observe — not the thread a read runs on, and not the interval it is bounded
	 * by.
	 * </p>
	 *
	 * @return true if reads should be resolved through the cache
	 */
	private boolean caching() {
		return config.get().getRefreshTtl() != null;
	}

	/**
	 * Resolves a cache miss, on a pooled thread and therefore in no transaction.
	 *
	 * @param key cache key
	 * @return single-element list for a load; matching rows for a search
	 * @throws Exception if the read fails
	 */
	private List<?> resolve(DaoKey key) throws Exception {
		if (key.isLoad())
			return Collections.singletonList(delegate.loadBean(key.type(), key.parameters()));

		return delegate.searchBeans(key.type(), key.parameters(), false, key.maxResults());
	}

	/**
	 * Supplies the hint for a cached read.
	 *
	 * <p>
	 * Reads never invalidate, so only a search declares anything: the rows it read,
	 * published as loads of their own keys.
	 * </p>
	 *
	 * @param key cache key
	 * @return cache hint
	 */
	private IuRefreshableCacheHint<DaoKey, List<?>> cacheHint(DaoKey key) {
		if (key.isLoad())
			return IuRefreshableCacheHint.useDefaults();

		return new IuRefreshableCacheHint<DaoKey, List<?>>() {
			@Override
			public Map<DaoKey, List<?>> inspect(List<?> rows) {
				return embedded(key.type(), rows);
			}
		};
	}

	/**
	 * Reads the rows of a search as loads of their own primary keys.
	 *
	 * <p>
	 * {@link IuDao#searchBeans(Class, Map, boolean, int)} answers with an empty
	 * list rather than null, and materializes every row it returns, so a search
	 * that matched nothing needs no guard of its own: it publishes nothing.
	 * </p>
	 *
	 * @param type entity type the rows were read as
	 * @param rows rows read
	 * @return published entries, empty when the rows carry no resolvable key
	 */
	private Map<DaoKey, List<?>> embedded(Class<?> type, List<?> rows) {
		final Map<DaoKey, List<?>> published = new LinkedHashMap<>();
		for (final var row : rows) {
			final var parameters = primaryKeyParameters(row, type);
			if (parameters != null)
				published.put(new DaoKey(type, parameters, DaoKey.LOAD), Collections.singletonList(row));
		}
		return published;
	}

	/**
	 * Reads an entity's mapped primary-key values into a query-parameter map.
	 *
	 * <p>
	 * Returns null when the entity declares no key, or when any key value is
	 * absent: a row read without its own identity cannot be published as a load of
	 * it. A value that cannot be read at all is treated the same way rather than
	 * failing the read that carried it.
	 * </p>
	 *
	 * @param entity entity instance
	 * @param type   entity type
	 * @return key parameters, or null when the key is not fully resolved
	 */
	private Map<String, Object> primaryKeyParameters(Object entity, Class<?> type) {
		try {
			final Map<String, Object> parameters = new LinkedHashMap<>();
			for (final var property : sqlBuilder.getPrimaryKeyProperties(type)) {
				final var value = sqlBuilder.getForSql(entity, property);
				if (value == null)
					return null;
				parameters.put(property, value);
			}

			return parameters.isEmpty() ? null : parameters;
		} catch (RuntimeException e) {
			LOG.log(Level.FINE, e, () -> "Unable to read the mapped key of " + type.getName() + "; not published");
			return null;
		}
	}

	/**
	 * Determines whether a transaction is active on the calling thread.
	 *
	 * @return true if a transaction is active
	 */
	private boolean transactional() {
		return IuException.unchecked(() -> transactionManager.getStatus() == Status.STATUS_ACTIVE);
	}

	/**
	 * Gets the work buffered by the transaction on the calling thread, registering
	 * the synchronization that applies it on first use.
	 *
	 * @return buffered work
	 */
	private Pending pending() {
		var pending = (Pending) transactionSynchronizationRegistry.getResource(PENDING_RESOURCE);
		if (pending != null)
			return pending;

		final var registered = new Pending();
		transactionSynchronizationRegistry.putResource(PENDING_RESOURCE, registered);
		transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
			@Override
			public void beforeCompletion() {
			}

			@Override
			public void afterCompletion(int status) {
				if (status != Status.STATUS_COMMITTED)
					// the writes were rolled back, so nothing they implied happened, and
					// the values read alongside them may never have existed
					return;

				// invalidations first, so a row republished by a write survives that
				// write's own invalidation of the searches that would have found it
				registered.invalidations.forEach(cache::invalidate);
				registered.injections.forEach(Runnable::run);
			}
		});

		return registered;
	}

	/**
	 * Raises an invalidation, at commit when a transaction is active.
	 *
	 * @param hint invalidation to raise
	 */
	private void invalidate(IuRefreshableCacheHint<DaoKey, List<?>> hint) {
		if (!caching())
			// nothing holds a value to discard, and buffering the hint would register a
			// synchronization on the caller's transaction to do nothing at commit
			return;

		if (transactional())
			pending().invalidations.add(hint);
		else
			cache.invalidate(hint);
	}

	/**
	 * Publishes a value, at commit when a transaction is active.
	 *
	 * @param key   cache key
	 * @param value value read
	 * @param mark  sequence position taken before the read
	 */
	private void publish(DaoKey key, List<?> value, long mark) {
		if (transactional())
			pending().injections.add(() -> cache.publish(key, value, mark));
		else
			cache.publish(key, value, mark);
	}

	/**
	 * Publishes a read, along with the rows it carries, at commit when a
	 * transaction is active.
	 *
	 * @param key  cache key of the read
	 * @param rows rows read
	 * @param mark sequence position taken before the read
	 */
	private void publishRead(DaoKey key, List<?> rows, long mark) {
		publish(key, rows, mark);
		if (!key.isLoad())
			embedded(key.type(), rows).forEach((embeddedKey, value) -> publish(embeddedKey, value, mark));
	}

	/**
	 * Resolves the entity type to key an entity's cache entries by.
	 *
	 * <p>
	 * A proxy resolves to the first interface it implements, matching how the
	 * delegate materializes an interface-mapped row; anything else describes
	 * itself.
	 * </p>
	 *
	 * @param bean entity instance
	 * @return entity type
	 */
	private static Class<?> entityType(Object bean) {
		final var type = bean.getClass();
		return Proxy.isProxyClass(type) //
				? type.getInterfaces()[0]
				: type;
	}

	/**
	 * Builds the invalidation a change to an entity type implies when the rows it
	 * affected cannot be named.
	 *
	 * <p>
	 * Everything cached for that type is discarded, loads included. This is the
	 * honest answer when something changed and the layer cannot say what: a load
	 * left in place would go on serving a row the caller has already replaced.
	 * </p>
	 *
	 * @param type entity type changed
	 * @return invalidation
	 */
	private static IuRefreshableCacheHint<DaoKey, List<?>> clearType(Class<?> type) {
		return new IuRefreshableCacheHint<DaoKey, List<?>>() {
			@Override
			public boolean shouldClear(DaoKey candidate) {
				return candidate.type().equals(type);
			}
		};
	}

	/**
	 * Builds the invalidation a write to one named row implies.
	 *
	 * <p>
	 * Every search of that type is discarded: a write can add a row to a result, or
	 * remove one from it, and the cache has no way to tell which searches the row
	 * would have matched. Loads of other rows are left alone, since a write to one
	 * row says nothing about any other.
	 * </p>
	 *
	 * @param type    entity type written
	 * @param loadKey key of the row written, discarded along with the searches;
	 *                null when the row is being republished, so that the value the
	 *                write itself produced survives its own invalidation
	 * @return invalidation
	 */
	private static IuRefreshableCacheHint<DaoKey, List<?>> writeHint(Class<?> type, DaoKey loadKey) {
		return new IuRefreshableCacheHint<DaoKey, List<?>>() {
			@Override
			public boolean shouldClear(DaoKey candidate) {
				if (!candidate.type().equals(type))
					return false;

				return !candidate.isLoad() //
						|| candidate.equals(loadKey);
			}
		};
	}

	/**
	 * Applies a write's invalidation, and republishes the row it wrote.
	 *
	 * @param bean      entity written
	 * @param republish whether the entity's own state is the state now stored, and
	 *                  so is worth republishing; false for a delete
	 */
	private void wrote(Object bean, boolean republish) {
		if (!caching())
			// checked before the key is read rather than relying on the guard in
			// invalidate: reading a mapped key calls into IuSqlBuilder, which is the
			// caller's to implement and need not be cheap
			return;

		final var type = entityType(bean);
		final var parameters = primaryKeyParameters(bean, type);
		if (parameters == null) {
			// the row cannot be named, so it cannot be republished and its load cannot
			// be singled out; every load of the type is suspect
			invalidate(clearType(type));
			return;
		}

		final var loadKey = new DaoKey(type, parameters, DaoKey.LOAD);

		// a republished row is not invalidated by the write that produced it, so the
		// hint spares its key and the mark below is taken after the hint is raised
		invalidate(writeHint(type, republish ? null : loadKey));

		if (republish)
			publish(loadKey, Collections.singletonList(bean), cache.mark());
	}

	@Override
	public <B> B loadBean(Class<B> beanClass, Map<String, ?> idParams) {
		if (!caching())
			return delegate.loadBean(beanClass, idParams);

		final var key = new DaoKey(beanClass, Objects.requireNonNull(idParams, "idParams"), DaoKey.LOAD);
		if (!transactional())
			return beanClass.cast(IuException.unchecked(() -> cache.apply(key)).get(0));

		final var mark = cache.mark();
		final var bean = delegate.loadBean(beanClass, idParams);
		publishRead(key, Collections.singletonList(bean), mark);
		return bean;
	}

	@Override
	public <B> List<B> searchBeans(Class<B> beanClass, Map<String, ?> idParams, boolean observe, int maxResults) {
		if (!caching())
			// the delegate rejects a negative cap on the same terms, so leave that to it
			return delegate.searchBeans(beanClass, idParams, observe, maxResults);

		if (maxResults < 0)
			throw new IllegalArgumentException("maxResults must not be negative");

		final var key = new DaoKey(beanClass, Objects.requireNonNull(idParams, "idParams"), maxResults);
		if (!transactional())
			return castResults(IuException.unchecked(() -> cache.apply(key)));

		final var mark = cache.mark();
		final var results = delegate.searchBeans(beanClass, idParams, observe, maxResults);
		publishRead(key, results, mark);
		return results;
	}

	/**
	 * Re-types a cached result list.
	 *
	 * <p>
	 * Safe because {@link DaoKey#type()} participates in cache-key equality, so a
	 * hit can only have been stored by a read of the same entity type.
	 * </p>
	 *
	 * @param results cached results
	 * @param <B>     entity type
	 * @return the same list, re-typed
	 */
	@SuppressWarnings("unchecked")
	private static <B> List<B> castResults(List<?> results) {
		return (List<B>) results;
	}

	@Override
	public void updateBean(Object bean) {
		delegate.updateBean(bean);
		wrote(bean, true);
	}

	@Override
	public void saveBean(Object bean) {
		delegate.saveBean(bean);
		wrote(bean, true);
	}

	@Override
	public void deleteBean(Object bean) {
		delegate.deleteBean(bean);
		wrote(bean, false);
	}

	/**
	 * Discards everything this cache holds, after a write too broad to describe.
	 *
	 * <p>
	 * Only this cache: the delegate has already evicted what it changed by the
	 * time a bulk write returns, so routing through {@link #clear()} would clear it
	 * a second time and leave this layer doing work of its own on a DAO configured
	 * with no refresh TTL.
	 * </p>
	 */
	private void invalidateEverything() {
		invalidate(IuRefreshableCacheHint.clearAll());
	}

	@Override
	public void insertBeans(Iterable<?> beans) {
		delegate.insertBeans(beans);
		invalidateEverything();
	}

	@Override
	public void updateBeans(Iterable<?> beans) {
		delegate.updateBeans(beans);
		invalidateEverything();
	}

	@Override
	public void saveBeans(Iterable<?> beans) {
		delegate.saveBeans(beans);
		invalidateEverything();
	}

	@Override
	public void clear() {
		delegate.clear();

		// a caller clearing by hand has changed something this layer cannot describe
		invalidateEverything();
	}

	@Override
	public void clear(Class<?> beanClass) {
		delegate.clear(beanClass);
		invalidate(clearType(Objects.requireNonNull(beanClass, "beanClass")));
	}

	/**
	 * Wraps a query generated from an entity mapping, so that the rows it reads
	 * are published as loads of their own.
	 *
	 * <p>
	 * Safe to publish from because the builder selects the entity's full mapped
	 * column list, so every row materializes as a complete entity — the same thing
	 * a load of that row would have produced.
	 * </p>
	 *
	 * @param beanClass entity type the rows are read as
	 * @param query     query to wrap
	 * @param <B>       entity type
	 * @return wrapped query, or {@code query} when the cache is inert
	 */
	private <B> SqlQuery<B> cachedBeanQuery(Class<B> beanClass, SqlQuery<B> query) {
		if (!caching())
			return query;

		return new CachedSqlQuery<>(query, () -> {
			final var mark = cache.mark();
			return rows -> embedded(beanClass, rows).forEach((key, value) -> publish(key, value, mark));
		});
	}

	/**
	 * Wraps a query built from caller-supplied SQL.
	 *
	 * <p>
	 * The rows are drained and answered from the list, but never published: the
	 * SQL is the caller's, so a row may be a partial projection of an entity, or
	 * carry no entity at all. Publishing one as a load would answer a later
	 * {@link #loadBean(Class, Map)} with a half-populated bean.
	 * </p>
	 *
	 * @param query query to wrap
	 * @param <B>   materialized row type
	 * @return wrapped query, or {@code query} when the cache is inert
	 */
	private <B> SqlQuery<B> cachedQuery(SqlQuery<B> query) {
		if (!caching())
			return query;

		return new CachedSqlQuery<>(query, () -> rows -> {
		});
	}

	/**
	 * Wraps a statement generated from one entity, so that running it invalidates
	 * what it changed.
	 *
	 * <p>
	 * The row is invalidated rather than republished. A generated statement is a
	 * template the caller drives: it may be run more than once, it may not be run
	 * at all, and in the {@link #getBeanUpdate(Object, Supplier)} form it carries
	 * only the columns that differ from a prior state. None of that makes the
	 * bean's own fields a trustworthy account of the row afterwards, which is what
	 * republishing would assert.
	 * </p>
	 *
	 * @param statement statement to wrap
	 * @param bean      entity the statement was generated from
	 * @return wrapped statement, or {@code statement} when the cache is inert
	 */
	private SqlStatement cachedStatement(SqlStatement statement, Object bean) {
		if (!caching())
			return statement;

		return new CachedSqlStatement(statement, () -> wrote(bean, false));
	}

	@Override
	public TableDefinition getTableDefinition(String tableName) {
		return delegate.getTableDefinition(tableName);
	}

	@Override
	public SqlStatement getStatement(String sql, Iterable<?> args) {
		// caller SQL naming neither an entity type nor a row: there is nothing to
		// invalidate by, and clearing the whole cache on every raw statement would
		// make the layer's cost unpredictable. This is the one write path that
		// leaves the cache stale, and the one place IuDao's standing instruction to
		// call clear() after modifying rows by other means still applies.
		return delegate.getStatement(sql, args);
	}

	@Override
	public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where, Iterable<?> args) {
		return cachedBeanQuery(beanClass, delegate.getBeanQuery(beanClass, where, args));
	}

	@Override
	public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Iterable<String> where, Iterable<String> order,
			Iterable<?> args) {
		return cachedBeanQuery(beanClass, delegate.getBeanQuery(beanClass, where, order, args));
	}

	@Override
	public <B> SqlQuery<B> getBeanQuery(Class<B> beanClass, Map<String, ?> idParams) {
		return cachedBeanQuery(beanClass, delegate.getBeanQuery(beanClass, idParams));
	}

	@Override
	public SqlStatement getBeanUpdate(Object bean) {
		return cachedStatement(delegate.getBeanUpdate(bean), bean);
	}

	@Override
	public SqlStatement getBeanUpdate(Object bean, Supplier<?> passive) {
		return cachedStatement(delegate.getBeanUpdate(bean, passive), bean);
	}

	@Override
	public SqlStatement getBeanInsert(Object bean) {
		return cachedStatement(delegate.getBeanInsert(bean), bean);
	}

	@Override
	public SqlStatement getBeanDelete(Object bean) {
		return cachedStatement(delegate.getBeanDelete(bean), bean);
	}

	@Override
	public <B> SqlQuery<B> getQuery(Class<B> beanClass, String sql, Iterable<?> args) {
		return cachedQuery(delegate.getQuery(beanClass, sql, args));
	}

	@Override
	public <B> SqlQuery<B> getFactoryQuery(Function<ResultSet, B> factory, String sql, Iterable<?> args) {
		return cachedQuery(delegate.getFactoryQuery(factory, sql, args));
	}
}
