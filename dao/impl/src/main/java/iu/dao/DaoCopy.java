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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import edu.iu.IuException;

/**
 * Copies entities crossing between a cache and the application that reads it.
 *
 * <h2>Why a copy is needed at all</h2>
 *
 * <p>
 * A cached read hands the same instance to every caller, so an application that
 * modifies what it was given would be modifying what the next caller is about to
 * be given — and would keep doing so until the entry expired. Copying at the
 * boundary is what lets a cached DAO behave like an uncached one, which hands
 * out a freshly materialized row every time.
 * </p>
 *
 * <h2>How a row is copied</h2>
 *
 * <p>
 * In descending order of preference:
 * </p>
 *
 * <dl>
 * <dt>already immutable</dt>
 * <dd>An interface-mapped row is materialized as a {@link Proxy} standing over
 * the values it was read with, and a record's components cannot be reassigned.
 * Neither can be modified, so both are shared rather than copied — and a list of
 * them is shared whole, since the list is unmodifiable too. Mapping an entity as
 * an interface is therefore the cheapest way to cache it.</dd>
 * <dt>{@link Cloneable} with a public {@code clone()}</dt>
 * <dd>The entity's own copy, which is the one the application declared it
 * wanted.</dd>
 * <dt>anything else</dt>
 * <dd>A new instance from the no-argument constructor, with every non-static
 * field assigned across. This reaches fields that no setter exposes, which
 * matters because a mapped column need not have one.</dd>
 * </dl>
 *
 * <p>
 * Every copy is shallow, as {@link Object#clone()} is: an entity holding a
 * mutable object in a field shares that object with its copy. Mapped columns
 * materialize as strings, numbers, and dates, so this is normally the whole
 * story, but an entity that hangs a mutable structure off a transient field
 * still shares it.
 * </p>
 */
final class DaoCopy {

	/**
	 * How to copy one type, worked out once per type.
	 *
	 * <p>
	 * A {@link ClassValue} rather than a map: it is keyed by the class itself, so
	 * a strategy cannot outlive the class loader that defined the entity.
	 * </p>
	 */
	private static final ClassValue<UnaryOperator<Object>> COPIERS = new ClassValue<>() {
		@Override
		protected UnaryOperator<Object> computeValue(Class<?> type) {
			return copier(type);
		}
	};

	private DaoCopy() {
	}

	/**
	 * Determines whether rows read as an entity type are already immutable, and so
	 * need no protecting.
	 *
	 * @param type entity type the rows were read as
	 * @return true when nothing about a row of this type can be modified
	 */
	static boolean immutable(Class<?> type) {
		// an interface-mapped row is materialized as a proxy standing over the values
		// it was read with, and a record's components cannot be reassigned
		return type.isInterface() //
				|| type.isRecord();
	}

	/**
	 * Copies a list of rows read as one entity type, and every row in it.
	 *
	 * <p>
	 * A list of immutable rows is answered with as it stands. Neither the list nor
	 * anything in it can be modified, so there is nothing for a copy to protect,
	 * and an interface-mapped entity is therefore the cheapest thing this cache can
	 * hold — one instance serves every reader. This relies on
	 * {@link edu.iu.dao.IuDao#searchBeans(Class, java.util.Map, boolean, int)}
	 * answering with an unmodifiable list, which it is specified to do.
	 * </p>
	 *
	 * <p>
	 * Otherwise the list is rebuilt from copies, and is unmodifiable for the same
	 * reason an uncached search's result is.
	 * </p>
	 *
	 * @param type entity type the rows were read as
	 * @param rows rows to copy
	 * @return unmodifiable list of rows, copied unless they cannot be modified
	 */
	static List<?> copyOf(Class<?> type, List<?> rows) {
		if (immutable(type))
			return rows;

		final var copied = new ArrayList<>(rows.size());
		for (final var row : rows)
			copied.add(copyOf(row));

		return Collections.unmodifiableList(copied);
	}

	/**
	 * Copies one row.
	 *
	 * @param entity row to copy; may be null
	 * @return copied row, or null
	 * @throws IllegalArgumentException if the entity's type offers no way to copy
	 *                                  it
	 */
	static Object copyOf(Object entity) {
		if (entity == null)
			return null;

		return COPIERS.get(entity.getClass()).apply(entity);
	}

	/**
	 * Works out how to copy one type.
	 *
	 * @param type type to copy
	 * @return copier
	 * @throws IllegalArgumentException if the type offers no way to copy it
	 */
	private static UnaryOperator<Object> copier(Class<?> type) {
		if (Proxy.isProxyClass(type) //
				|| type.isRecord())
			return UnaryOperator.identity();

		final var clone = publicClone(type);
		if (clone != null)
			return entity -> IuException.unchecked(() -> clone.invoke(entity));

		final Constructor<?> constructor;
		try {
			constructor = type.getDeclaredConstructor();
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("Cannot copy " + type.getName()
					+ "; an entity read through a cached DAO must be Cloneable, a record, or have a no-argument constructor",
					e);
		}

		if (!constructor.canAccess(null))
			constructor.setAccessible(true);

		final List<Field> fields = new ArrayList<>();
		for (final var field : DaoUtils.getAllDeclaredFields(type))
			fields.add(DaoUtils.accessible(field));

		return entity -> IuException.unchecked(() -> {
			final var copy = constructor.newInstance();
			for (final var field : fields)
				field.set(copy, field.get(entity));

			return copy;
		});
	}

	/**
	 * Gets a type's public {@code clone()}, when it declares that it has one.
	 *
	 * @param type type to inspect
	 * @return public {@code clone()} method, or null when the type is not
	 *         {@link Cloneable} or keeps {@code clone()} to itself
	 */
	private static Method publicClone(Class<?> type) {
		if (!Cloneable.class.isAssignableFrom(type))
			return null;

		try {
			return type.getMethod("clone");
		} catch (NoSuchMethodException e) {
			// Cloneable without a public clone() cannot be asked to copy itself; the
			// field-by-field copy below produces the same shallow copy anyway
			return null;
		}
	}
}
