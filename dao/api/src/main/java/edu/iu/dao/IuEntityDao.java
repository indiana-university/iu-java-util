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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import edu.iu.GenericTypes;
import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import jakarta.persistence.EntityNotFoundException;

/**
 * Abstract base class for a DAO resource with load, search, save, and delete
 * operations.
 *
 * <p>
 * Operations are suitable for a keyed collection: {@link #load(Object)} reads
 * one row, {@link #search(Map, int, Integer)} lists a range,
 * {@link #save(Object, Object)} writes one row whole, {@link #merge} applies a
 * partial input to one row, and {@link #delete(Object)} removes one. A subclass
 * names the key type {@code K}, the contract interface {@code I} its callers
 * read and write, and the entity type {@code T} that stores it; the entity
 * implements the contract and takes a contract value's own columns through
 * {@link Consumer#accept(Object)}.
 * </p>
 *
 * <h2>Hooks</h2>
 *
 * <p>
 * Related rows are handled by overriding {@link #afterLoad},
 * {@link #afterSave}, {@link #afterMerge}, {@link #beforeDelete} and
 * {@link #afterDelete}; {@link #beforeInput} and {@link #afterInput} surround
 * the application of input to an entity, and {@link #afterInput} is where a
 * subclass prepares an entity to be written, i.e., stamps audit columns.
 * </p>
 *
 * <h2>Writes</h2>
 *
 * <p>
 * A save applies its input onto the row already stored under that key when
 * there is one, so columns the input does not carry -- when the row was
 * created, which parent it belongs to -- survive an update. A merge applies
 * only the values a partial input carries.
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
public abstract class IuEntityDao<K, I, E extends Consumer<I> & MergeableEntity<I>> {

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
	 * Performs actions on an entity before input is merged or applied, for example
	 * to take a snapshot of data prior to modification.
	 * 
	 * <p>
	 * This event is invoked by both {@link #save} and {@link #merge} after loading
	 * the existing entity, not invoked when saving a new entity.
	 * </p>
	 *
	 * @param entity entity about to be merged or applied
	 */
	protected void beforeInput(E entity) {
	}

	/**
	 * Prepares an entity for writing, after the input has been applied or merged
	 * and before the row is saved; i.e., the place to stamp audit columns.
	 *
	 * <p>
	 * The event is invoked by both {@link #save} and {@link #merge} after applying
	 * or merging input, on all entities, prior to {@link IuDao#saveBean}.
	 * </p>
	 *
	 * @param entity entity about to be saved
	 */
	protected void afterInput(E entity) {
	}

	/**
	 * Performs secondary writes after the row itself is saved.
	 * 
	 * <p>
	 * This event is invoked by {@link #save} after {@link IuDao#saveBean} completes
	 * successfully. Only this method or {@link #afterMerge} will be invoked, not
	 * both.
	 * </p>
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
	 * Performs secondary writes after the row itself is written in a merge
	 * operation.
	 *
	 * <p>
	 * This event is invoked by {@link #merge} after {@link IuDao#saveBean}
	 * completes successfully. Only this method or {@link #afterSave} will be
	 * invoked, not both.
	 * </p>
	 * 
	 * @param entity entity as saved
	 */
	protected void afterMerge(E entity) {
	}

	/**
	 * Performs secondary deletes before the row itself is deleted.
	 *
	 * <p>
	 * Not invoked when no row exists under the key.
	 * </p>
	 *
	 * @param entity entity being deleted
	 */
	protected void beforeDelete(E entity) {
	}

	/**
	 * Performs secondary actions after the row itself is deleted.
	 *
	 * <p>
	 * Not invoked when no row exists under the key.
	 * </p>
	 *
	 * @param entity entity just deleted
	 */
	protected void afterDelete(E entity) {
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
	protected List<E> searchWhere(String where, Object... args) {
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
	protected List<E> searchWhere(Iterable<String> where, Iterable<?> args, int from, Integer to) {
		return selectAndPopulate(dao().getBeanQuery(entityClass, where, args).getResults(), from, to);
	}

	// search() helper
	private List<E> selectAndPopulate(List<E> found, int from, Integer to) {
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

		return Collections.unmodifiableList(range);
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
	public List<E> search(Map<String, ?> params) {
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
	public List<E> search(Map<String, ?> params, int from, Integer to) {
		return selectAndPopulate(dao().searchBeans(entityClass, params), from, to);
	}

	/**
	 * Saves an entity.
	 *
	 * <p>
	 * This method differs from {@link #merge} in two ways. Null values in the input
	 * clear the existing value, where merge preserves it. And when no entity is
	 * stored under the key, a new entity is created, where merge requires an
	 * existing row. For an existing row, only the entity bean is loaded, without
	 * invoking {@link #afterLoad}, which facilitates creating and replacing related
	 * values.
	 * </p>
	 *
	 * @param key   key to save the input as; may be null if the key is provided by
	 *              passing {@code input} to {@link #key(Object)}
	 * @param input input properties
	 * @return key under which the input was saved; the key as given, else the key
	 *         carried by the input, else a generated key
	 * @throws IllegalArgumentException      if both {@code key} and the key carried
	 *                                       by {@code input} are non-null and not
	 *                                       equal
	 * @throws UnsupportedOperationException if neither {@code key} nor
	 *                                       {@code input} provides a key, and key
	 *                                       generation is not supported
	 */
	public K save(K key, I input) throws IllegalArgumentException, UnsupportedOperationException {
		final var keyFromInput = key(input);
		final K saveKey;
		if (key == null)
			saveKey = Objects.requireNonNullElseGet(keyFromInput, this::generate);
		else
			saveKey = IuObject.once(key, keyFromInput, "key does not match input");

		E entity;
		boolean newEntity;
		try {
			entity = dao().loadBean(entityClass, id(saveKey));
			newEntity = false;
		} catch (EntityNotFoundException e) {
			entity = newEntity(saveKey);
			newEntity = true;
		}

		if (!newEntity)
			beforeInput(entity);

		entity.accept(input);
		afterInput(entity);

		dao().saveBean(entity);

		afterSave(entity);

		return saveKey;
	}

	/**
	 * Saves an entity for an input that carries its own key.
	 * 
	 * <p>
	 * Equivalent to {@link #save(Object, Object) save(null, input)}
	 * </p>
	 * 
	 * @param input input
	 * @return key under which the input was saved; the key carried by the input,
	 *         else a generated key
	 */
	public K save(I input) {
		return save(null, input);
	}

	/**
	 * Merges a partial input onto a stored entity: the alternative to
	 * {@link #save(Object, Object) save} for {@code PATCH}.
	 *
	 * <p>
	 * This method differs from {@link #save(Object, Object) save} in two ways. Null
	 * values in the input preserve the existing value, where save clears it. And
	 * merge requires an existing row, loaded through {@link #load(Object)} so that
	 * {@link #afterLoad} is invoked, which facilitates appending or modifying
	 * specific related values.
	 * </p>
	 *
	 * @param key   of the row to merge with; may be null if the key is provided by
	 *              passing {@code input} to {@link #key(Object)}
	 * @param input partial input, naming the key of a stored entity
	 * @return key of the row the input was merged with
	 * @throws EntityNotFoundException  if no entity is stored under the key
	 * @throws IllegalArgumentException if both {@code key} and the key carried by
	 *                                  {@code input} are non-null and not equal
	 * @throws NullPointerException     if neither {@code key} nor {@code input}
	 *                                  provides a key
	 */
	public K merge(K key, I input) throws EntityNotFoundException, IllegalArgumentException, NullPointerException {
		final var mergeKey = IuObject.once(key, key(input), "key");

		final var entity = load(mergeKey);

		beforeInput(entity);
		entity.merge(input);
		afterInput(entity);

		dao().saveBean(entity);

		afterMerge(entity);

		return mergeKey;
	}

	/**
	 * Merges an entity for an input that carries its own key.
	 * 
	 * <p>
	 * Equivalent to {@link #merge(Object, Object) merge(null, input)}
	 * </p>
	 * 
	 * @param input input
	 * @return key of the row the input was merged with
	 */
	public K merge(I input) {
		return merge(null, input);
	}

	/**
	 * Deletes an entity, if one exists under the key.
	 *
	 * <p>
	 * Invokes {@link #beforeDelete}, deletes the row, then invokes
	 * {@link #afterDelete}, without invoking {@link #afterLoad}. Does nothing when
	 * no entity is stored under the key.
	 * </p>
	 *
	 * @param key key
	 */
	public void delete(K key) {
		final E entity;
		try {
			entity = dao().loadBean(entityClass, id(key));
		} catch (EntityNotFoundException e) {
			return;
		}

		beforeDelete(entity);
		dao().deleteBean(entity);
		afterDelete(entity);
	}

}
