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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import edu.iu.GenericTypes;
import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import jakarta.persistence.EntityNotFoundException;

/**
 * Abstract base class for a DAO resource with load, search, save, and delete
 * operations.
 *
 * <p>
 * Operations are suitable for a keyed collection: {@link #load(Object)} reads
 * one row, {@link #search(Map, int, Integer)} lists a range,
 * {@link #save(Object)} writes one row whole, and {@link #delete(Object)}
 * removes one. A subclass names the key type {@code K}, the contract interface
 * {@code I} its callers read and write, and the entity type {@code T} that
 * stores it; the entity implements the contract and takes a contract value's
 * own columns through {@link Consumer#accept(Object)}.
 * </p>
 *
 * <h2>Hooks</h2>
 *
 * <p>
 * Related rows are handled by overriding {@link #afterLoad}, {@link #afterSave}
 * and {@link #beforeDelete}; {@link #beforeSave} is where a subclass prepares
 * an entity to be saved, i.e., stamps audit columns.
 * </p>
 *
 * <h2>Writes</h2>
 *
 * <p>
 * A save applies its input onto the row already stored under that key when
 * there is one, so columns the input does not carry -- when the row was
 * created, which parent it belongs to -- survive an update.
 * </p>
 *
 * <h2>Listing</h2>
 *
 * <p>
 * Search reads every matching row, orders them by {@link #order()}, and
 * populates related rows for the requested range only. Range and order
 * operations are suitable only for tables backed by a caching DAO with fewer
 * than 10k rows.
 * </p>
 *
 * <h2>Access from sibling DAOs</h2>
 *
 * <p>
 * The key methods are protected, so a DAO in another package than this class
 * may not call them on a sibling DAO. A family of DAOs that do so, such as a
 * parent generating keys for a child's rows, redeclares them in a common base
 * class in its own package.
 * </p>
 *
 * @param <K> key type
 * @param <I> contract interface type
 * @param <E> entity type
 */
public abstract class IuEntityDao<K, I, E extends Consumer<I>> {

	private final Class<K> keyClass;
	private final Class<E> entityClass;

	/**
	 * Default constructor.
	 */
	@SuppressWarnings("unchecked")
	protected IuEntityDao() {
		final var typeArguments = GenericTypes.typeArguments(getClass(), IuEntityDao.class);
		keyClass = (Class<K>) GenericTypes.erase(typeArguments[0]);
		entityClass = (Class<E>) GenericTypes.erase(typeArguments[2]);
	}

	/**
	 * Gets the database access this DAO reads and writes through.
	 *
	 * @return database access
	 */
	protected abstract IuDao dao();

	/**
	 * Maps a key to the entity's key properties, as
	 * {@link IuDao#loadBean(Class, Map)} takes them.
	 *
	 * @param key key, already validated
	 * @return key properties, by entity property name
	 */
	protected Map<String, Object> id(K key) {
		return Map.of(IuIterable.single(dao().getPrimaryKeyProperties(entityClass)), key);
	}

	/**
	 * Reads the key a stored entity is registered under.
	 *
	 * @param entity entity
	 * @return key
	 */
	protected K key(E entity) {
		return keyClass.cast(IuIterable.single(dao().getBeanKey(entity).values()));
	}

	/**
	 * Reads the key from input, when the input carries one.
	 *
	 * <p>
	 * Most contract interfaces do not name their own key, so by default a key is
	 * only reachable from input that is itself a key, or an entity read back from
	 * this DAO. A DAO whose contract names the key overrides this.
	 * </p>
	 *
	 * @param input input
	 * @return key; null if the input does not carry one
	 */
	protected K key(I input) {
		if (keyClass.isInstance(input))
			return keyClass.cast(input);

		if (entityClass.isInstance(input))
			return key(entityClass.cast(input));

		return null;
	}

	/**
	 * Generates a unique key, if supported.
	 *
	 * @return generated unique key; for a String key, an {@link IdGenerator} ID
	 * @throws UnsupportedOperationException if not supported
	 */
	protected K generate() throws UnsupportedOperationException {
		if (keyClass == String.class)
			return keyClass.cast(IdGenerator.generateId());
		else
			throw new UnsupportedOperationException();
	}

	/**
	 * Creates an entity carrying only its key, for a row not yet written.
	 *
	 * @param key key, already validated
	 * @return new entity
	 */
	protected E newEntity(K key) {
		return dao().newBean(entityClass, id(key));
	}

	/**
	 * Gets the order a search answers entities in.
	 *
	 * @return natural order when the entity is {@link Comparable}; otherwise null,
	 *         leaving results in the order the database answered them
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Comparator<? super E> order() {
		if (Comparable.class.isAssignableFrom(entityClass))
			return (a, b) -> ((Comparable) a).compareTo(b);
		else
			return null;
	}

	/**
	 * Performs secondary lookups and populates related values on an entity just
	 * read.
	 *
	 * @param entity entity
	 */
	protected void afterLoad(E entity) {
	}

	/**
	 * Prepares an entity for writing, after the input has been applied and before
	 * the row is saved; i.e., the place to stamp audit columns.
	 *
	 * @param entity entity about to be saved
	 */
	protected void beforeSave(E entity) {
	}

	/**
	 * Performs secondary writes after the row itself is saved.
	 *
	 * <p>
	 * Related values are read from the input rather than from the entity: an entity
	 * only takes its own columns from the input, so a child collection the input
	 * carries is not on the entity.
	 * </p>
	 *
	 * @param entity entity as saved
	 */
	protected void afterSave(E entity) {
	}

	/**
	 * Performs secondary deletes before the row itself is deleted.
	 *
	 * @param entity entity being deleted
	 */
	protected void beforeDelete(E entity) {
	}

	/**
	 * Searches for entities by SQL criteria.
	 *
	 * <p>
	 * Not public, and not named {@code search}: the clause is emitted into SQL
	 * verbatim, and a {@code search(String, ...)} overload beside a subclass's
	 * {@code search(String parentId, ...)} made a call passing {@code null}
	 * ambiguous. Qualify the entity's own columns with its table alias; see
	 * {@link IuSqlBuilder#getTableAlias(Class, String)}. Unlike
	 * {@link #search(Map)}, this is not served from the transaction's read cache.
	 * </p>
	 *
	 * @param where where clause, with {@code ?} placeholders
	 * @param args  placeholder values, in order
	 * @return results, in {@link #order()}
	 */
	protected Iterable<E> searchWhere(String where, Object... args) {
		return searchWhere(IuIterable.iter(where), IuIterable.iter(args), 0, null);
	}

	/**
	 * Searches for entities by SQL criteria.
	 *
	 * @param where where clauses, with {@code ?} placeholders, emitted verbatim
	 * @param args  placeholder values, in order
	 * @param from  start of range, inclusive
	 * @param to    end of range, exclusive; null for end of list
	 * @return results, in {@link #order()}
	 */
	protected Iterable<E> searchWhere(Iterable<String> where, Iterable<?> args, int from, Integer to) {
		return selectAndPopulate(dao().getBeanQuery(entityClass, where, args).getResults(), from, to);
	}

	// search() helper
	private Iterable<E> selectAndPopulate(List<E> found, int from, Integer to) {
		final var order = order();
		final List<E> results;
		if (order == null)
			results = found;
		else {
			results = new ArrayList<>(found);
			results.sort(order);
		}

		final List<E> range;
		if (from == 0 && to == null)
			range = results;
		else {
			final var size = results.size();
			range = results.subList( //
					Integer.max(0, Integer.min(from, size)), //
					Integer.max(from, Integer.min(size, Objects.requireNonNullElse(to, size))) //
			);
		}

		for (final var entity : range)
			afterLoad(entity);

		return range::iterator;
	}

	/**
	 * Loads an entity by key.
	 *
	 * @param key key
	 * @return loaded entity
	 * @throws EntityNotFoundException if nothing is registered under the key
	 */
	public E load(K key) throws EntityNotFoundException {
		final var entity = dao().loadBean(entityClass, id(key));
		afterLoad(entity);
		return entity;
	}

	/**
	 * Searches for entities.
	 *
	 * @param params search params, by entity property name; an {@link Iterable}
	 *               value matches any of its elements
	 * @return results, in {@link #order()}
	 */
	public Iterable<E> search(Map<String, ?> params) {
		return search(params, 0, null);
	}

	/**
	 * Searches for entities.
	 *
	 * @param params search params, by entity property name; an {@link Iterable}
	 *               value matches any of its elements
	 * @param from   start of range, inclusive
	 * @param to     end of range, exclusive; null for end of list
	 * @return results, in {@link #order()}
	 */
	public Iterable<E> search(Map<String, ?> params, int from, Integer to) {
		return selectAndPopulate(dao().searchBeans(entityClass, params), from, to);
	}

	/**
	 * Saves an entity.
	 *
	 * @param input input properties
	 * @return key under which the input was saved; generated if the input didn't
	 *         carry a key
	 */
	public K save(I input) throws IllegalArgumentException {
		final var key = Objects.requireNonNullElseGet(key(input), this::generate);

		E entity;
		try {
			entity = dao().loadBean(entityClass, id(key));
		} catch (EntityNotFoundException e) {
			entity = newEntity(key);
		}

		entity.accept(input);
		beforeSave(entity);
		dao().saveBean(entity);
		afterSave(entity);

		return key;
	}

	/**
	 * Deletes an entity.
	 *
	 * @param key key
	 */
	public void delete(K key) {
		try {
			final var entity = dao().loadBean(entityClass, id(key));
			beforeDelete(entity);
			dao().deleteBean(entity);
		} catch (EntityNotFoundException e) {
		}
	}

}
