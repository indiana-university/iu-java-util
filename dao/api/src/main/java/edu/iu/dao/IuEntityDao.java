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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import jakarta.persistence.EntityNotFoundException;

/**
 * Keyed CRUD over one entity type, on top of an {@link IuDao}.
 *
 * <p>
 * The verbs line up with a keyed collection: {@link #load(Object)} reads one
 * row, {@link #search(Map, int, Integer)} lists a range, {@link #save(Object,
 * Object)} writes one row whole, and {@link #delete(Object)} removes one. A
 * subclass names the key type {@code K}, the contract interface {@code I} its
 * callers read and write, and the entity type {@code T} that stores it; the
 * entity implements the contract and takes a contract value's own columns
 * through {@link Consumer#accept(Object)}.
 * </p>
 *
 * <p>
 * This is not an ORM, any more than {@link IuDao} is: there is no persistence
 * context, dirty checking, or lazy loading. Each verb is an explicit read or
 * write through the {@link IuDao}, and a save is an explicit merge: the row
 * already stored under the key is read, the input applied onto it, and the
 * result written back. Call the verbs inside a JTA transaction: a save reads
 * before it writes, and related rows are written by separate statements.
 * </p>
 *
 * <p>
 * Default behavior suits an entity class with a single {@code @Id} property. A
 * DAO with a composite key overrides {@link #id(Object)},
 * {@link #key(Object)} for its entity, and {@link #validate(Object)}.
 * {@link EffectiveDated} entities and interface or record entities are not
 * supported: a save would supersede rather than update, and an entity that
 * cannot be populated cannot take its input.
 * </p>
 *
 * <h2>Hooks</h2>
 *
 * <p>
 * Related rows are handled by overriding {@link #afterLoad}, {@link #beforeSave},
 * {@link #afterSave} and {@link #beforeDelete}; {@link #beforeSave} is also
 * where a subclass stamps audit columns. None of these shares a name with a
 * public verb: when one did, a call such as {@code childDao.delete(childEntity)}
 * resolved to the more specific hook rather than the verb, and ran the hook
 * without deleting the row.
 * </p>
 *
 * <h2>Writes</h2>
 *
 * <p>
 * A save applies its input onto the row already stored under that key when
 * there is one, so columns the input does not carry -- when the row was
 * created, which parent it belongs to -- survive an update. A row belonging to
 * a parent is written through {@link #save(Object, Object, Consumer)}, whose
 * binding step names the parent on a new row and is expected to refuse moving
 * an existing one to a different parent.
 * </p>
 *
 * <h2>Listing</h2>
 *
 * <p>
 * {@link #search(Map, int, Integer)} reads every matching row, orders them by
 * {@link #order()}, and populates related rows for the requested range only.
 * That suits tables of registrations, not tables that grow without bound.
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
 * @param <T> entity type
 */
public abstract class IuEntityDao<K, I, T extends Consumer<I>> {

	/**
	 * Default constructor.
	 */
	protected IuEntityDao() {
	}

	/**
	 * Gets the database access this DAO reads and writes through.
	 *
	 * @return database access
	 */
	protected abstract IuDao dao();

	/**
	 * Gets the key class.
	 *
	 * @return key class
	 */
	protected abstract Class<K> keyClass();

	/**
	 * Gets the entity class.
	 *
	 * @return entity class
	 */
	protected abstract Class<T> entityClass();

	/**
	 * Maps a key to the entity's key properties, as {@link IuDao#loadBean(Class,
	 * Map)} takes them.
	 *
	 * @param key key, already validated
	 * @return key properties, by entity property name
	 */
	protected Map<String, Object> id(K key) {
		return Map.of(IuIterable.single(dao().getPrimaryKeyProperties(entityClass())), key);
	}

	/**
	 * Reads the key a stored entity is registered under.
	 *
	 * @param entity entity
	 * @return key
	 */
	protected K key(T entity) {
		return keyClass().cast(IuIterable.single(dao().getBeanKey(entity).values()));
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
		final var keyClass = keyClass();
		if (keyClass.isInstance(input))
			return keyClass.cast(input);

		final var entityClass = entityClass();
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
		if (keyClass() == String.class)
			return keyClass().cast(IdGenerator.generateId());
		else
			throw new UnsupportedOperationException();
	}

	/**
	 * Validates a key before passing it to the database.
	 *
	 * @param key key
	 * @throws IllegalArgumentException if invalid; by default, only if missing
	 */
	protected void validate(K key) throws IllegalArgumentException {
		if (key == null)
			throw new IllegalArgumentException("missing key");
	}

	/**
	 * Creates an entity carrying only its key, for a row not yet written.
	 *
	 * @param key key, already validated
	 * @return new entity
	 */
	protected T newEntity(K key) {
		return dao().newBean(entityClass(), id(key));
	}

	/**
	 * Gets the order a search answers entities in.
	 *
	 * @return natural order when the entity is {@link Comparable}; otherwise null,
	 *         leaving results in the order the database answered them
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Comparator<? super T> order() {
		if (Comparable.class.isAssignableFrom(entityClass()))
			return (a, b) -> ((Comparable) a).compareTo(b);
		else
			return null;
	}

	/**
	 * Performs secondary lookups and populates related values on an entity just
	 * read.
	 *
	 * @param key    key the entity was read by
	 * @param entity entity
	 */
	protected void afterLoad(K key, T entity) {
	}

	/**
	 * Prepares an entity for writing, after the input has been applied and before
	 * the row is saved; the place to stamp audit columns.
	 *
	 * @param key    key the entity is being saved under
	 * @param entity entity about to be saved
	 * @param input  input the entity was saved from
	 */
	protected void beforeSave(K key, T entity, I input) {
	}

	/**
	 * Performs secondary writes after the row itself is saved.
	 *
	 * <p>
	 * Related values are read from the input rather than from the entity: an
	 * entity only takes its own columns from the input, so a child collection the
	 * input carries is not on the entity.
	 * </p>
	 *
	 * @param key    key the entity was saved under
	 * @param entity entity as saved
	 * @param input  input the entity was saved from
	 */
	protected void afterSave(K key, T entity, I input) {
	}

	/**
	 * Performs secondary deletes before the row itself is deleted.
	 *
	 * @param key    key being deleted
	 * @param entity entity being deleted
	 */
	protected void beforeDelete(K key, T entity) {
	}

	/**
	 * Loads an entity by key.
	 *
	 * @param key key
	 * @return loaded entity
	 * @throws EntityNotFoundException if nothing is registered under the key
	 */
	public T load(K key) throws EntityNotFoundException {
		return load(key, null);
	}

	/**
	 * Loads an entity by key, binding it to context the row does not store before
	 * its related rows are read.
	 *
	 * @param key  key
	 * @param bind applied to the entity before {@link #afterLoad}; may be null
	 * @return loaded entity
	 * @throws EntityNotFoundException if nothing is registered under the key
	 */
	protected T load(K key, Consumer<T> bind) throws EntityNotFoundException {
		validate(key);
		final var entity = dao().loadBean(entityClass(), id(key));
		if (bind != null)
			bind.accept(entity);
		afterLoad(key, entity);
		return entity;
	}

	/**
	 * Searches for entities.
	 *
	 * @param params search params, by entity property name; an {@link Iterable}
	 *               value matches any of its elements
	 * @return results, in {@link #order()}
	 */
	public Iterable<T> search(Map<String, ?> params) {
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
	public Iterable<T> search(Map<String, ?> params, int from, Integer to) {
		return selectAndPopulate(dao().searchBeans(entityClass(), params), from, to, null);
	}

	/**
	 * Searches for entities belonging to something that supplies context the rows
	 * do not store, such as the resource root a path is resolved against.
	 *
	 * @param params search params, by entity property name
	 * @param bind   applied to each result before its related rows are read
	 * @return results, in {@link #order()}
	 */
	protected Iterable<T> search(Map<String, ?> params, Consumer<T> bind) {
		return selectAndPopulate(dao().searchBeans(entityClass(), params), 0, null, bind);
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
	protected Iterable<T> searchWhere(String where, Object... args) {
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
	protected Iterable<T> searchWhere(Iterable<String> where, Iterable<?> args, int from, Integer to) {
		return selectAndPopulate(dao().getBeanQuery(entityClass(), where, args).getResults(), from, to, null);
	}

	// search() helper
	private Iterable<T> selectAndPopulate(List<T> found, int from, Integer to, Consumer<T> bind) {
		// a copy: the DAO may answer an unmodifiable list
		final List<T> results = new ArrayList<>(found);
		final var order = order();
		if (order != null)
			results.sort(order);

		from = Integer.max(0, Integer.min(from, results.size()));
		final List<T> range;
		if (to != null)
			range = results.subList(from, Integer.max(from, Integer.min(to, results.size())));
		else
			range = results.subList(from, results.size());

		// populate related rows on range only
		for (final var entity : range) {
			if (bind != null)
				bind.accept(entity);
			afterLoad(key(entity), entity);
		}

		return range;
	}

	/**
	 * Saves an entity with a generated key.
	 *
	 * @param input input properties
	 * @return saved entity, with generated key populated
	 */
	public T create(I input) {
		return save(generate(), input);
	}

	/**
	 * Saves an entity whose key the input carries.
	 *
	 * @param input input properties, must carry a key
	 * @return saved entity
	 * @throws IllegalArgumentException if the input carries no key
	 */
	public T save(I input) throws IllegalArgumentException {
		final var key = key(input);
		if (key == null)
			throw new IllegalArgumentException("missing key");
		return save(key, input);
	}

	/**
	 * Saves an entity with a known key.
	 *
	 * @param key   key, must be valid and non-null
	 * @param input input properties
	 * @return saved entity
	 */
	public T save(K key, I input) {
		return save(key, input, null);
	}

	/**
	 * Saves an entity with a known key, binding it to what it belongs to.
	 *
	 * @param key   key, must be valid and non-null
	 * @param input input properties
	 * @param bind  applied to the entity before the input is, so a new row names
	 *              its parent; expected to refuse an existing row that names a
	 *              different one. May be null.
	 * @return saved entity
	 */
	protected T save(K key, I input, Consumer<T> bind) {
		if (input == null)
			throw new IllegalArgumentException("missing input");
		validate(key);

		var entity = find(key);
		if (entity == null)
			entity = newEntity(key);

		if (bind != null)
			bind.accept(entity);
		entity.accept(input);
		beforeSave(key, entity, input);

		dao().saveBean(entity);
		afterSave(key, entity, input);
		return load(key, bind);
	}

	/**
	 * Saves related input, generating a key for input that carries none.
	 *
	 * @param input input properties
	 * @param bind  see {@link #save(Object, Object, Consumer)}
	 * @return key the input was saved under
	 */
	protected K saveOrCreate(I input, Consumer<T> bind) {
		var key = key(input);
		if (key == null)
			key = generate();
		save(key, input, bind);
		return key;
	}

	/**
	 * Deletes an entity.
	 *
	 * <p>
	 * Deleting a key nothing is registered under does nothing.
	 * </p>
	 *
	 * @param key key
	 */
	public void delete(K key) {
		validate(key);
		final var entity = find(key);
		if (entity != null) {
			beforeDelete(key, entity);
			dao().deleteBean(entity);
		}
	}

	/**
	 * Replaces a set of related rows with those an input names.
	 *
	 * <p>
	 * A null {@code desired} leaves the related rows alone, which is what an input
	 * that does not carry them means. An empty one removes them all.
	 * </p>
	 *
	 * @param <C>     related input type
	 * @param <R>     related key type
	 * @param desired related input the rows should now match; may be null
	 * @param current keys of the related rows as they stand
	 * @param save    saves one related input, answering the key it was saved
	 *                under
	 * @param remove  removes the related row with a key no longer named
	 */
	protected static <C, R> void replace(Iterable<? extends C> desired, Iterable<R> current, Function<C, R> save,
			Consumer<R> remove) {
		if (desired == null)
			return;

		final Set<R> stale = new LinkedHashSet<>();
		current.forEach(stale::add);

		for (final C related : desired)
			stale.remove(save.apply(related));

		stale.forEach(remove);
	}

	private T find(K key) {
		try {
			return dao().loadBean(entityClass(), id(key));
		} catch (EntityNotFoundException e) {
			return null;
		}
	}

}
