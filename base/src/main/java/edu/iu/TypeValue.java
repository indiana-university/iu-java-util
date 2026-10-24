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
package edu.iu;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes and caches values associated with reflective types.
 * 
 * <p>
 * This class is intended as a generic equivalent of {@link ClassValue}.
 * </p>
 *
 * <p>
 * Cached values are grouped by the most specific class loader represented by a
 * type, allowing a group to be released when its associated class is unloaded.
 * </p>
 *
 * @param <T> value type
 */
public abstract class TypeValue<T> {

	private final ClassValue<ConcurrentHashMap<Type, T>> classValue = new ClassValue<ConcurrentHashMap<Type, T>>() {
		@Override
		protected ConcurrentHashMap<Type, T> computeValue(Class<?> type) {
			return new ConcurrentHashMap<>();
		}
	};

	/**
	 * Creates a type-value cache.
	 */
	protected TypeValue() {
	}

	/**
	 * Computes the value to cache for a type.
	 *
	 * @param type reflective type
	 * @return value to cache
	 */
	protected abstract T computeValue(Type type);

	/**
	 * Gets the value associated with a reflective type, computing it when it is not
	 * already cached.
	 *
	 * @param type reflective type
	 * @return cached value
	 */
	public T get(Type type) {
		return classValue.get(mostSpecific(type)).computeIfAbsent(type, this::computeValue);
	}

	/**
	 * Determine which of two classes is loaded by the more specific loader.
	 * 
	 * @param a a class
	 * @param b another class
	 * @return a or b, whichever has the most specific loader
	 */
	static Class<?> moreSpecific(Class<?> a, Class<?> b) {
		if (a == null)
			return b;
		if (b == null)
			return a;

		var aLoader = a.getClassLoader();
		if (aLoader == null)
			return b;

		var bLoader = b.getClassLoader();
		if (bLoader == null)
			return a;

		do {
			if (aLoader == bLoader)
				return a;
			aLoader = IuObject.convert(aLoader, ClassLoader::getParent);
		} while (aLoader != null);

		return b;
	}

	/**
	 * Determines the most specific class referred to by a generic type.
	 * 
	 * @param type generic type
	 * @return The class referred to by the generic type that is
	 *         {@link #moreSpecific(Class, Class) more specific} than all other
	 *         types, recursive
	 */
	static Class<?> mostSpecific(Type type) {
		if (type instanceof Class c)
			return c;

		if (type instanceof ParameterizedType pt) {
			var c = mostSpecific(pt.getRawType());
			c = moreSpecific(c, mostSpecific(pt.getOwnerType()));
			for (final var arg : pt.getActualTypeArguments())
				c = moreSpecific(c, mostSpecific(arg));
			return c;
		}

		if (type instanceof TypeVariable v) {
			Class<?> c = Object.class;
			for (final var bound : v.getBounds())
				c = moreSpecific(c, mostSpecific(bound));
			return c;
		}

		if (type instanceof WildcardType w) {
			Class<?> c = Object.class;
			for (final var bound : w.getUpperBounds())
				c = moreSpecific(c, mostSpecific(bound));
			for (final var bound : w.getLowerBounds())
				c = moreSpecific(c, mostSpecific(bound));
			return c;
		}

		if (type instanceof GenericArrayType ga)
			return mostSpecific(ga.getGenericComponentType());

		if (type == null)
			return null;

		throw new IllegalArgumentException(type.toString());
	}

}
