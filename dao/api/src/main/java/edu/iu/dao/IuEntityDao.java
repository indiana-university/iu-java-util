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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
 * one row, {@link #search(Map, Search)} lists a range,
 * {@link #save(Object, Object)} writes one row whole, {@link #merge} applies a
 * partial input to one row, and {@link #delete(Object)} removes one. A subclass
 * names the key type {@code K}, the contract interface {@code I} its callers
 * read and write, and the entity type {@code E} that stores it.
 * </p>
 *
 * <h2>Entity decorators</h2>
 *
 * <p>
 * The entity opts in to the behavior it needs by implementing decorator
 * interfaces; an entity that doesn't implement one is left untouched by the
 * corresponding operation. {@link SaveableEntity} takes a full input in
 * {@link #save(Object, Object) save}, {@link MergeableEntity} takes a partial
 * input in {@link #merge(Object, Object) merge}, and {@link SequencedEntity}
 * takes the position of a child row saved or merged as part of a child
 * collection.
 * </p>
 *
 * <h2>Hooks</h2>
 *
 * <p>
 * Related rows are handled by overriding {@link #afterLoad},
 * {@link #afterSave}, {@link #afterMerge}, {@link #beforeDelete} and
 * {@link #afterDelete}; {@link #beforeInput} and {@link #afterInput} surround
 * the application of input to an entity, and {@link #afterInput} is where a
 * subclass prepares an entity to be written, i.e., stamps audit columns. The
 * four input-related hooks receive the input given to the save or merge, which
 * is null when the caller passed none, so a parent DAO can read its child
 * collection from it.
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
 * <h2>Parent and child rows</h2>
 *
 * <p>
 * A child DAO maintains the rows that belong to a parent entity from within the
 * parent DAO's {@link #afterSave} or {@link #afterMerge} hook, by passing the
 * input's child collection to {@link #save(Iterable)} or
 * {@link #merge(Iterable)}. The child DAO finds the parent through
 * {@link #parent()} and the parent's input through the {@code input} argument
 * of its {@link #afterSave} or {@link #afterMerge} hook, and opts in by
 * overriding {@link #search(Search)} to select the parent's
 * stored children. Because a parent-driven search decides which stored rows are
 * deleted, the default implementation returns no rows, and a child collection
 * is then saved or merged without removing anything. {@link #delete()} removes
 * all of the parent's children, by default those {@link #search()} returns.
 * Every verb is strict about the parent: unless the entity implements
 * {@link ChildEntity}, or the DAO overrides {@link #tryBinding} (or
 * {@link #bind}) to accept it, an operation on a DAO while a parent is active
 * throws {@link IllegalArgumentException}. A new row is bound to the parent
 * when created, before input is applied, and a stored row is bound again on
 * {@code load}, {@code save}, {@code merge} and {@code delete}, so a stored
 * child of a different parent is rejected.
 * {@link #withParent(Object, Runnable)} and the overloads that take a parent
 * object inject one without a hook.
 * </p>
 *
 * <p>
 * An entity class should override {@code hashCode}, {@code equals} and
 * {@code toString} over its primary key values, as the rejection message
 * identifies the child by its {@code toString}.
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
public abstract class IuEntityDao<K, I, E> {

	private static final ThreadLocal<Object> PARENT = new ThreadLocal<>();

	/**
	 * Passes the post-processing a search applies to the rows it reads.
	 *
	 * <p>
	 * The static factories capture the active {@link IuEntityDao#parent()} of the
	 * calling thread, so a {@code Search} created outside any parent scope carries
	 * a null parent.
	 * </p>
	 *
	 * @param <E>    entity type
	 * @param parent parent to filter results by, as accepted by
	 *               {@link IuEntityDao#tryBinding}; may be null
	 * @param filter additional filter applied after the parent filter; null to
	 *               accept every row
	 * @param from   start of range, inclusive
	 * @param to     end of range, exclusive; null for end of list
	 */
	public record Search<E>(Object parent, Predicate<E> filter, int from, Integer to) {

		/**
		 * Selects every row bound to the active parent.
		 *
		 * @param <E> entity type
		 * @return search for the active {@link IuEntityDao#parent()}, unfiltered and
		 *         unranged
		 */
		public static <E> Search<E> child() {
			return new Search<E>(PARENT.get(), null, 0, null);
		}

		/**
		 * Selects a range of the rows bound to the active parent.
		 *
		 * @param <E>  entity type
		 * @param from start of range, inclusive
		 * @param to   end of range, exclusive; null for end of list
		 * @return search for the active {@link IuEntityDao#parent()}, limited to the
		 *         range
		 */
		public static <E> Search<E> range(int from, Integer to) {
			return new Search<E>(PARENT.get(), null, from, to);
		}

		/**
		 * Selects the rows bound to the active parent that pass a filter.
		 *
		 * @param <E>    entity type
		 * @param filter filter, applied before the range
		 * @return search for the active {@link IuEntityDao#parent()}, filtered
		 */
		public static <E> Search<E> filtered(Predicate<E> filter) {
			return new Search<E>(PARENT.get(), filter, 0, null);
		}

		/**
		 * Selects a range of the rows bound to the active parent that pass a filter.
		 *
		 * @param <E>    entity type
		 * @param filter filter, applied before the range
		 * @param from   start of range, inclusive
		 * @param to     end of range, exclusive; null for end of list
		 * @return search for the active {@link IuEntityDao#parent()}, filtered and
		 *         limited to the range
		 */
		public static <E> Search<E> filteredRange(Predicate<E> filter, int from, Integer to) {
			return new Search<E>(PARENT.get(), filter, from, to);
		}
	}

	/**
	 * Injects a parent object for reference by {@link #parent} within the callback.
	 * 
	 * <p>
	 * The parent must be appropriate for the DAO, and should be validated when
	 * consumed from {@link #parent}.
	 * </p>
	 * 
	 * @param <T>      return type
	 * @param parent   parent to inject
	 * @param supplier callback
	 * @return result
	 */
	public static <T> T withParent(Object parent, Supplier<T> supplier) {
		final var restore = PARENT.get();
		try {
			PARENT.set(parent);

			return supplier.get();

		} finally {
			if (restore == null)
				PARENT.remove();
			else
				PARENT.set(restore);
		}
	}

	/**
	 * Injects a parent object for reference by {@link #parent} within the callback.
	 * 
	 * <p>
	 * The parent must be appropriate for the DAO, and should be validated when
	 * consumed from {@link #parent}.
	 * </p>
	 * 
	 * @param parent   parent to inject
	 * @param runnable callback
	 */
	public static void withParent(Object parent, Runnable runnable) {
		withParent(parent, () -> {
			runnable.run();
			return null;
		});
	}

	/**
	 * Key class.
	 */
	protected final Class<K> keyClass;

	/**
	 * Entity class.
	 */
	protected final Class<I> inputClass;

	/**
	 * Entity class.
	 */
	protected final Class<E> entityClass;

	/**
	 * Default constructor.
	 */
	@SuppressWarnings("unchecked")
	protected IuEntityDao() {
		final var typeArguments = GenericTypes.typeArguments(getClass(), IuEntityDao.class);
		keyClass = (Class<K>) GenericTypes.erase(typeArguments[0]);
		inputClass = (Class<I>) GenericTypes.erase(typeArguments[1]);
		entityClass = (Class<E>) GenericTypes.erase(typeArguments[2]);
	}

	/**
	 * Gets the database access this DAO reads and writes through.
	 *
	 * @return database access
	 */
	protected abstract IuDao dao();

	/**
	 * Gets the parent entity last noted by a load, save, merge, or delete operation
	 * on the current thread.
	 *
	 * <p>
	 * The value is set to the entity being operated on while the
	 * {@link #afterLoad}, {@link #afterSave}, {@link #afterMerge},
	 * {@link #beforeDelete}, and {@link #afterDelete} hooks run, and restored when
	 * the hook returns. The value is shared by all DAOs on the thread, so a child
	 * DAO invoked from one of those hooks sees the parent DAO's entity, and MAY
	 * cast it to the parent entity type.
	 * </p>
	 *
	 * @return parent entity; null when no such hook is running
	 */
	protected Object parent() {
		return PARENT.get();
	}

	/**
	 * Tries binding a child entity to a given parent.
	 * 
	 * <p>
	 * Used by {@link #search} to filter results by {@link #parent()}, and by the
	 * default {@link #bind} to reject an entity that does not implement
	 * {@link ChildEntity}. Delegates to {@link ChildEntity#tryBinding} when child
	 * implements {@link ChildEntity}; else returns true only when parent is null. A
	 * DAO whose entity does not implement {@link ChildEntity} may override only
	 * this method to accept a parent; {@link #bind} need not be overridden.
	 * </p>
	 * 
	 * <p>
	 * Implementations MAY initialize a child entity binding, and SHOULD be
	 * non-destructive and fail fast.
	 * </p>
	 * 
	 * @param parent parent to validate against
	 * @param child  child entity
	 * @return true if the child is, or has just been, bound to the parent; false if
	 *         it cannot be bound to the parent
	 */
	protected boolean tryBinding(Object parent, E child) {
		if (child instanceof ChildEntity e)
			return e.tryBinding(parent);
		else
			return parent == null;
	}

	/**
	 * Binds child to a given parent.
	 * 
	 * <p>
	 * Invoked with {@link #parent()} by {@link #newEntity} for a new row, and by
	 * {@link #load} (and so {@link #merge}), {@link #save} and {@link #delete} for
	 * a stored row, before any hook runs. Delegates to {@link ChildEntity#bind}
	 * when child implements {@link ChildEntity}; else throws
	 * {@link IllegalArgumentException} unless {@link #tryBinding} accepts the
	 * parent. Strict by default: a DAO supports a parent by one of
	 * </p>
	 * <ol>
	 * <li>an entity that implements {@link ChildEntity},</li>
	 * <li>overriding {@link #tryBinding} to accept the parent, or</li>
	 * <li>overriding this method, which may also record the parent on the
	 * child.</li>
	 * </ol>
	 * <p>
	 * Otherwise a save or merge from a parent DAO's {@link #afterSave} or
	 * {@link #afterMerge} fails with {@link IllegalArgumentException}.
	 * </p>
	 *
	 * <p>
	 * The message of the exception includes the parent and the child's
	 * {@link Object#toString()}. As a best practice, an entity class should
	 * override {@code hashCode}, {@code equals} and {@code toString} over its
	 * primary key values, so the message identifies the row.
	 * </p>
	 *
	 * @param parent parent to bind to; null when no parent is active
	 * @param child  child entity
	 * @throws IllegalArgumentException if the child cannot be bound to the parent
	 */
	protected void bind(Object parent, E child) {
		if (child instanceof ChildEntity e)
			e.bind(parent);
		else if (!tryBinding(parent, child))
			throw new IllegalArgumentException("unexpected parent " + parent + " for child " + child);
	}

	/**
	 * Maps a key to the entity's primary key property, as
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
	 * <p>
	 * Distinct from {@link #key(Object)}, which reads the key from input.
	 * </p>
	 *
	 * @param entity entity
	 * @return key
	 */
	protected K entityKey(E entity) {
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
			return entityKey(entityClass.cast(input));

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
	 * @return new entity, {@link #bind bound} to its {@link #parent}
	 * @throws IllegalArgumentException if the entity cannot be bound to the active
	 *                                  parent
	 */
	protected E newEntity(K key) {
		final var newEntity = dao().newBean(entityClass, id(key));
		bind(parent(), newEntity);
		return newEntity;
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
	 * @param input  input provided to {@link #save} or {@link #merge}
	 * @param entity entity about to be merged or applied
	 */
	protected void beforeInput(I input, E entity) {
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
	 * @param input  input provided to {@link #save} or {@link #merge}
	 * @param entity entity about to be saved
	 */
	protected void afterInput(I input, E entity) {
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
	 * @param input  input provided to {@link #save}
	 * @param entity entity as saved
	 */
	protected void afterSave(I input, E entity) {
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
	 * @param input  input provided to {@link #merge}
	 * @param entity entity as saved
	 */
	protected void afterMerge(I input, E entity) {
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
	 * Loads an entity by key.
	 *
	 * @param parent parent object, for child reference
	 * @param key    key
	 * @return loaded entity
	 * @throws EntityNotFoundException  if nothing is registered under the key
	 * @throws IllegalArgumentException if the entity is not a valid child of the
	 *                                  parent, as determined by {@link #bind}
	 */
	public E load(Object parent, K key) throws EntityNotFoundException {
		return withParent(parent, () -> load(key));
	}

	/**
	 * Loads an entity by key.
	 *
	 * @param key key
	 * @return loaded entity
	 * @throws EntityNotFoundException  if nothing is registered under the key
	 * @throws IllegalArgumentException if the entity is not a valid child of the
	 *                                  active parent, as determined by
	 *                                  {@link #bind}
	 */
	public E load(K key) throws EntityNotFoundException {
		final var entity = dao().loadBean(entityClass, id(key));
		bind(parent(), entity);
		withParent(entity, () -> afterLoad(entity));
		return entity;
	}

	/**
	 * Eagerly searches for entities by SQL criteria.
	 * 
	 * <p>
	 * This method is intended for implementing {@link #search(Search)}. The rows
	 * read are filtered by {@link Search#parent()} through {@link #tryBinding} and
	 * by {@link Search#filter()}, ordered, and limited to the range of the
	 * {@code search} before {@link #afterLoad} runs for the rows returned.
	 * </p>
	 *
	 * @param where  where clauses, with {@code ?} placeholders, emitted verbatim
	 * @param args   placeholder values, in order
	 * @param search parent, filter and range to apply to the rows read
	 * @return results, in {@link #order()}
	 */
	protected List<E> searchWhere(Iterable<String> where, Iterable<?> args, Search<E> search) {
		return selectAndPopulate(dao().getBeanQuery(entityClass, where, args).getResults(), search);
	}

	// search() helper
	private List<E> selectAndPopulate(List<E> found, Search<E> search) {
		final var order = order();
		final List<E> results = new ArrayList<>(found.size());
		for (final var child : found)
			if (tryBinding(search.parent, child) && (search.filter == null || search.filter.test(child)))
				results.add(child);

		if (order != null)
			results.sort(order);

		int from = search.from;
		Integer to = search.to;
		final List<E> range;
		if (from == 0 && to == null)
			range = results;
		else {
			var size = results.size();
			from = Integer.max(0, Integer.min(from, size));
			to = Integer.max(from, Integer.min(size, Objects.requireNonNullElse(to, size)));
			range = results.subList(from, to);
		}

		for (final var entity : range)
			withParent(entity, () -> afterLoad(entity));

		return Collections.unmodifiableList(range);
	}

	/**
	 * Eagerly searches for entities by criteria defined by parent reference.
	 * 
	 * <p>
	 * MAY be invoked from within a parent DAO's {@link #afterLoad},
	 * {@link #afterSave}, {@link #afterMerge}, or {@link #afterDelete} method to
	 * look up child rows.
	 * </p>
	 * 
	 * <p>
	 * Subclasses opt in to parent-driven search by overriding this method; by
	 * default no rows are returned. Because the result decides which stored child
	 * rows are deleted by {@link #save(Iterable)}, an override must select only
	 * rows that belong to {@link #parent()}.
	 * </p>
	 *
	 * @param search parent, filter and range, as captured from {@link #parent()}
	 *               by {@link Search#child()} and its sibling factories
	 * @return results, in {@link #order()}; by default, empty
	 */
	public List<E> search(Search<E> search) {
		return List.of();
	}

	/**
	 * Eagerly searches for entities.
	 *
	 * @param params search params, by entity property name; an {@link Iterable}
	 *               value matches any of its elements
	 * @param search parent, filter and range to apply to the rows read
	 * @return results, in {@link #order()}
	 */
	public List<E> search(Map<String, ?> params, Search<E> search) {
		return selectAndPopulate(dao().searchBeans(entityClass, params), search);
	}

	/**
	 * Searches for entities by criteria defined by inspecting {@link #parent()}.
	 * 
	 * <p>
	 * The {@link #parent()} is captured when this method is called, but the search
	 * itself is deferred until the result is first iterated.
	 * </p>
	 * 
	 * <p>
	 * MAY be invoked from within a parent DAO's {@link #afterLoad},
	 * {@link #afterSave}, {@link #afterMerge}, or {@link #afterDelete} method to
	 * look up child rows.
	 * </p>
	 *
	 * <p>
	 * Subclasses opt in to parent-driven search by overriding
	 * {@link #search(Search)}; by default no rows are returned. The
	 * result drives which stored child rows {@link #save(Iterable)} deletes and
	 * {@link #merge(Iterable)} may merge onto, so it must select only rows that
	 * belong to {@link #parent()}.
	 * </p>
	 *
	 * @return results, in {@link #order()}; by default, empty
	 */
	protected Iterable<E> search() {
		final Search<E> search = Search.child();
		return IuIterable.defer(() -> search(search));
	}

	/**
	 * Searches for entities.
	 *
	 * <p>
	 * The {@link #parent()} is captured when this method is called, but the search
	 * itself is deferred until the result is first iterated.
	 * </p>
	 * 
	 * <p>
	 * MAY be invoked from within a parent DAO's {@link #afterLoad},
	 * {@link #afterSave}, {@link #afterMerge}, or {@link #afterDelete} method to
	 * look up child rows.
	 * </p>
	 *
	 * @param params search params, by entity property name; an {@link Iterable}
	 *               value matches any of its elements
	 * @return results, in {@link #order()}
	 */
	public Iterable<E> search(Map<String, ?> params) {
		final Search<E> search = Search.child();
		return IuIterable.defer(() -> search(params, search));
	}

	/**
	 * Saves an entity.
	 *
	 * @param parent parent object
	 * @param key    key to save the input as; may be null if the key is provided by
	 *               passing {@code input} to {@link #key(Object)}
	 * @param input  input properties
	 * @return key under which the input was saved; the key as given, else the key
	 *         carried by the input, else a generated key
	 * @throws IllegalArgumentException      if the entity is not a valid
	 *                                       {@link #bind child} of the active
	 *                                       parent, or both {@code key} and the key
	 *                                       carried by {@code input} are non-null
	 *                                       and not equal
	 * @throws UnsupportedOperationException if neither {@code key} nor
	 *                                       {@code input} provides a key, and key
	 *                                       generation is not supported
	 */
	public K save(Object parent, K key, I input) throws IllegalArgumentException, UnsupportedOperationException {
		return withParent(parent, () -> save(key, input));
	}

	/**
	 * Saves an entity.
	 *
	 * <p>
	 * This method differs from {@link #merge} in two ways. Null values in the input
	 * clear the existing value, where merge preserves it. And when no entity is
	 * stored under the key, a new entity is created, where merge requires an
	 * existing row.
	 * </p>
	 *
	 * <p>
	 * For an existing row, {@link #afterLoad} is invoked after the entity bean is
	 * read and before {@link #beforeInput}, with {@link #parent()} set to the
	 * entity; a new entity is not loaded, so neither hook is invoked. The input is
	 * then applied by {@link SaveableEntity#save}, when the entity implements it,
	 * and the sequence number is injected, when the entity implements
	 * {@link SequencedEntity} and the save is part of a child collection. A null
	 * input is allowed, and is passed to the hooks as given.
	 * </p>
	 *
	 * <p>
	 * An existing row is bound to {@link #parent()} by {@link #bind} before any
	 * hook runs. A new row is created by {@link #newEntity}, which binds the active
	 * parent before the input is applied. A child that cannot be bound is an
	 * {@link IllegalArgumentException}, never an {@link EntityNotFoundException},
	 * and nothing is written. The sequence number is injected after the input is
	 * applied and before {@link #afterInput}.
	 * </p>
	 *
	 * @param key   key to save the input as; may be null if the key is provided by
	 *              passing {@code input} to {@link #key(Object)}
	 * @param input input properties
	 * @return key under which the input was saved; the key as given, else the key
	 *         carried by the input, else a generated key
	 * @throws IllegalArgumentException      if the entity is not a valid
	 *                                       {@link #bind child} of the active
	 *                                       parent, or both {@code key} and the key
	 *                                       carried by {@code input} are non-null
	 *                                       and not equal
	 * @throws UnsupportedOperationException if neither {@code key} nor
	 *                                       {@code input} provides a key, and key
	 *                                       generation is not supported
	 */
	public K save(K key, I input) throws IllegalArgumentException, UnsupportedOperationException {
		return save(key, input, null);
	}

	@SuppressWarnings("unchecked")
	private K save(K key, I input, Integer seq) throws IllegalArgumentException, UnsupportedOperationException {
		final var keyFromInput = key(input);
		final K saveKey;
		if (key == null)
			saveKey = Objects.requireNonNullElseGet(keyFromInput, this::generate);
		else
			saveKey = IuObject.once(key, keyFromInput, "key does not match input");

		final E entity;
		boolean newEntity;
		{
			E loadedEntity;
			try {
				loadedEntity = dao().loadBean(entityClass, id(saveKey));
				newEntity = false;
			} catch (EntityNotFoundException e) {
				loadedEntity = newEntity(saveKey);
				newEntity = true;
			}
			entity = loadedEntity;
		}

		if (!newEntity) {
			bind(parent(), entity);
			withParent(entity, () -> afterLoad(entity));
			beforeInput(input, entity);
		}

		if (input != null && entity instanceof SaveableEntity saveable)
			saveable.save(input);

		if (seq != null && (entity instanceof SequencedEntity se))
			se.setSeq(seq);

		afterInput(input, entity);

		dao().saveBean(entity);

		withParent(entity, () -> afterSave(input, entity));

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
	 * Saves child rows.
	 * 
	 * @param parent   parent object
	 * @param children child collection; null to leave stored child rows unchanged
	 */
	public void save(Object parent, Iterable<? extends I> children) {
		withParent(parent, () -> save(children));
	}

	/**
	 * Saves all child rows.
	 * 
	 * <p>
	 * Intended to be invoked from the parent DAO's {@link #afterSave} or
	 * {@link #afterMerge} hook. The keys of the rows {@link #search()} returns, if
	 * any, are bound to a set and removed as child rows are saved. Any keys left
	 * over (not saved as a child row) are then deleted. With the default
	 * {@link #search(Search)}, nothing is deleted.
	 * </p>
	 *
	 * <p>
	 * When the entity type implements {@link SequencedEntity}, the position of each
	 * child in the collection, starting at 0, is injected before the child is
	 * saved.
	 * </p>
	 *
	 * @param children child collection; null to leave stored child rows unchanged
	 */
	public void save(Iterable<? extends I> children) {
		if (children != null) {
			final Set<K> keys = new HashSet<>();
			search().forEach(ref -> keys.add(entityKey(ref)));

			var seq = 0;
			for (final var child : children)
				keys.remove(save(null, child, seq++));

			keys.forEach(this::delete);
		}
	}

	/**
	 * Merges a partial input onto a stored entity.
	 *
	 * <p>
	 * This method differs from {@link #save(Object, Object, Object) save} in two
	 * ways. Null values in the input preserve the existing value, where save clears
	 * it. And merge requires an existing row, loaded through {@link #load(Object)}
	 * so that {@link #afterLoad} is invoked, which facilitates appending or
	 * modifying specific related values.
	 * </p>
	 *
	 * <p>
	 * The input is applied by {@link MergeableEntity#merge}, when the entity
	 * implements it, and the sequence number is injected, when the entity
	 * implements {@link SequencedEntity} and the merge is part of a child
	 * collection. A null input is allowed, and is passed to the hooks as given.
	 * </p>
	 *
	 * @param parent parent object
	 * @param key    of the row to merge with; may be null if the key is provided by
	 *               passing {@code input} to {@link #key(Object)}
	 * @param input  partial input, naming the key of a stored entity
	 * @return key of the row the input was merged with
	 * @throws EntityNotFoundException  if no entity is stored under the key
	 * @throws IllegalArgumentException if the entity is not a valid {@link #bind
	 *                                  child} of the active parent, or both
	 *                                  {@code key} and the key carried by
	 *                                  {@code input} are non-null and not equal
	 * @throws NullPointerException     if neither {@code key} nor {@code input}
	 *                                  provides a key
	 */
	public K merge(Object parent, K key, I input)
			throws EntityNotFoundException, IllegalArgumentException, NullPointerException {
		return withParent(parent, () -> merge(key, input));
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
	 * <p>
	 * The input is applied by {@link MergeableEntity#merge}, when the entity
	 * implements it, and the sequence number is injected, when the entity
	 * implements {@link SequencedEntity} and the merge is part of a child
	 * collection. A null input is allowed, and is passed to the hooks as given.
	 * </p>
	 *
	 * <p>
	 * The entity is bound to {@link #parent()} by {@link #bind} as it is loaded,
	 * before any hook runs; a child that cannot be bound is an
	 * {@link IllegalArgumentException}, never an {@link EntityNotFoundException}.
	 * The sequence number is injected after the input is applied and before
	 * {@link #afterInput}, as in {@link #save(Object, Object) save}.
	 * </p>
	 *
	 * @param key   of the row to merge with; may be null if the key is provided by
	 *              passing {@code input} to {@link #key(Object)}
	 * @param input partial input, naming the key of a stored entity
	 * @return key of the row the input was merged with
	 * @throws EntityNotFoundException  if no entity is stored under the key
	 * @throws IllegalArgumentException if the entity is not a valid {@link #bind
	 *                                  child} of the active parent, or both
	 *                                  {@code key} and the key carried by
	 *                                  {@code input} are non-null and not equal
	 * @throws NullPointerException     if neither {@code key} nor {@code input}
	 *                                  provides a key
	 */
	public K merge(K key, I input) throws EntityNotFoundException, IllegalArgumentException, NullPointerException {
		return merge(key, input, null);
	}

	@SuppressWarnings("unchecked")
	private K merge(K key, I input, Integer seq)
			throws EntityNotFoundException, IllegalArgumentException, NullPointerException {
		final var mergeKey = IuObject.once(key, key(input), "key");

		final var entity = load(mergeKey);

		beforeInput(input, entity);

		if (input != null && entity instanceof MergeableEntity mergeable)
			mergeable.merge(input);

		if (seq != null && (entity instanceof SequencedEntity se))
			se.setSeq(seq);

		afterInput(input, entity);

		dao().saveBean(entity);

		withParent(entity, () -> afterMerge(input, entity));

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
	 * Merges all child rows.
	 * 
	 * <p>
	 * The collection is validated before anything is written. Stored child rows are
	 * visited in the order {@link #search()} returns them; those the collection
	 * names are merged, and new children are then saved in the order given. When
	 * the entity type implements {@link SequencedEntity}, every stored row is given
	 * its position in that order, including rows the collection does not name
	 * (those are merged with no input), and each new child takes the next position
	 * after the stored rows. Rows that need no change are expected to be skipped by
	 * {@link IuDao#saveBean}, though their hooks still run.
	 * </p>
	 *
	 * @param parent   parent object
	 * @param children child collection; null to leave stored child rows unchanged
	 * @throws IllegalArgumentException if two children carry the same key, or a
	 *                                  child carries a key that is not among the
	 *                                  stored child rows
	 */
	public void merge(Object parent, Iterable<? extends I> children) {
		withParent(parent, () -> merge(children));
	}

	/**
	 * Merges all child rows.
	 * 
	 * <p>
	 * Intended to be invoked from the parent DAO's {@link #afterMerge} hook.
	 * Children that carry a key are merged onto the stored child row with that key,
	 * as found by {@link #search()}; children that carry no key are saved as new
	 * rows. Stored child rows not named in the collection keep their values.
	 * </p>
	 *
	 * <p>
	 * The collection is validated before anything is written. Stored child rows are
	 * visited in the order {@link #search()} returns them; those the collection
	 * names are merged, and new children are then saved in the order given. When
	 * the entity type implements {@link SequencedEntity}, every stored row is given
	 * its position in that order, including rows the collection does not name
	 * (those are merged with no input), and each new child takes the next position
	 * after the stored rows. Rows that need no change are expected to be skipped by
	 * {@link IuDao#saveBean}, though their hooks still run.
	 * </p>
	 *
	 * @param children child collection; null to leave stored child rows unchanged
	 * @throws IllegalArgumentException if two children carry the same key, or a
	 *                                  child carries a key that is not among the
	 *                                  stored child rows
	 */
	public void merge(Iterable<? extends I> children) {
		if (children != null) {
			final Map<K, I> keyedChildren = new LinkedHashMap<>();
			final Queue<I> newChildren = new ArrayDeque<>();
			for (final var child : children) {
				final var key = key(child);
				if (key == null)
					newChildren.add(child);
				else if (keyedChildren.put(key, child) != null)
					throw new IllegalArgumentException("duplicate child row " + key);
			}

			class Pending {
				K key;
				E entity;
				I input;
			}
			final Queue<Pending> pendingChildren = new ArrayDeque<>();
			for (final var child : search()) {
				final var pending = new Pending();
				pending.key = entityKey(child);
				pending.entity = child;
				pending.input = keyedChildren.remove(pending.key);
				pendingChildren.offer(pending);
			}

			if (!keyedChildren.isEmpty())
				throw new IllegalArgumentException("entity not found for child rows " + keyedChildren.keySet());

			var seq = 0;
			for (final var child : pendingChildren) {
				if (child.input != null || child.entity instanceof SequencedEntity)
					merge(child.key, child.input, seq);
				seq++;
			}

			for (final var child : newChildren)
				save(null, child, seq++);
		}
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
	 * @param parent parent object
	 * @param key    key
	 * @throws IllegalArgumentException if the entity is not a valid child of the
	 *                                  active parent, as determined by
	 *                                  {@link #bind}
	 */
	public void delete(Object parent, K key) {
		withParent(parent, () -> delete(key));
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
	 * @throws IllegalArgumentException if the entity is not a valid child of the
	 *                                  active parent, as determined by
	 *                                  {@link #bind}
	 */
	public void delete(K key) {
		final E entity;
		try {
			entity = dao().loadBean(entityClass, id(key));
		} catch (EntityNotFoundException e) {
			return;
		}

		bind(parent(), entity);

		withParent(entity, () -> {
			beforeDelete(entity);
			dao().deleteBean(entity);
			afterDelete(entity);
		});
	}

	/**
	 * Deletes all entities bound to a parent, by default each of the entities
	 * returned from {@link #search(Search)}, via
	 * {@link #delete(Object, Object)}, with the parent injected through
	 * {@link #withParent(Object, Runnable)}.
	 * 
	 * <p>
	 * Subclasses that override {@link #search(Search)} may also
	 * override this method for more efficient bulk delete behavior.
	 * </p>
	 *
	 * @param parent parent object
	 */
	public void deleteChildren(Object parent) {
		search(new Search<E>(parent, null, 0, null)).forEach(ref -> delete(parent, entityKey(ref)));
	}

	/**
	 * Deletes all entities bound to the active {@link #parent()}, by default each
	 * of the entities returned from {@link #search()}, via {@link #delete(Object)}.
	 * 
	 * <p>
	 * Subclasses that override {@link #search(Search)} may also
	 * override this method for more efficient bulk delete behavior.
	 * </p>
	 */
	public void delete() {
		deleteChildren(parent());
	}

}
