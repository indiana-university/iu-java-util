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
package edu.iu.client;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.function.Function;

import iu.client.ConversionScope;

/**
 * A conversion for a type whose values convert to and from a JSON array:
 * arrays, including primitive arrays, and the iterable, collection, iterator,
 * enumeration, and stream types {@link IuJsonAdapter#of(Type)} supports.
 *
 * <p>
 * Beyond converting, it takes the items out of a value and puts items into a
 * new one, for code that reads or writes the items itself, such as accepting a
 * single value where an array is expected, as in a {@code JsonbDeserializer}
 * whose items convert as the JSON-B call does:
 * </p>
 *
 * <pre>
 * IuJsonArrayAdapter.of(type).collect(List.of(item))
 * </pre>
 *
 * @param <T> container type
 * @param <E> item type; boxed for a primitive array
 */
public interface IuJsonArrayAdapter<T, E> extends IuJsonAdapter<T> {

	/**
	 * Gets the built-in conversion for a type that converts to and from a JSON
	 * array.
	 *
	 * <p>
	 * Items convert as the JSON-B call in progress on this thread converts them,
	 * if any, so a {@code JsonbDeserializer} can collect items of any type the
	 * JSON-B instance converts; otherwise by the built-in conversions. An adapter
	 * got outside a call keeps the built-ins.
	 * </p>
	 *
	 * @param <T>  container type
	 * @param <E>  item type
	 * @param type array, iterable, collection, iterator, enumeration, or stream
	 *             type
	 * @return conversion
	 * @throws IllegalArgumentException      if {@code type} has a conversion, but
	 *                                       not to and from a JSON array
	 * @throws UnsupportedOperationException if {@code type}, or its item type, has
	 *                                       no conversion, as for
	 *                                       {@link IuJsonAdapter#of(Type)}
	 * @see IuJsonAdapter#of(Type)
	 */
	static <T, E> IuJsonArrayAdapter<T, E> of(Type type) {
		return of(type, ConversionScope.current());
	}

	/**
	 * Gets the built-in conversion for a type that converts to and from a JSON
	 * array, converting its items by a function of the caller's.
	 *
	 * @param <T>          container type
	 * @param <E>          item type
	 * @param type         array, iterable, collection, iterator, enumeration, or
	 *                     stream type
	 * @param valueAdapter item conversions; null for the built-ins
	 * @return conversion
	 * @throws IllegalArgumentException      if {@code type} has a conversion, but
	 *                                       not to and from a JSON array
	 * @throws UnsupportedOperationException if {@code type}, or with null
	 *                                       {@code valueAdapter} its item type, has
	 *                                       no conversion
	 * @see IuJsonAdapter#of(Type, Function)
	 */
	@SuppressWarnings("unchecked")
	static <T, E> IuJsonArrayAdapter<T, E> of(Type type, Function<Type, IuJsonAdapter<?>> valueAdapter) {
		final var adapter = IuJsonAdapter.of(type, valueAdapter);
		if (adapter instanceof IuJsonArrayAdapter)
			return (IuJsonArrayAdapter<T, E>) adapter;
		else
			throw new IllegalArgumentException("doesn't convert as a JSON array: " + type.getTypeName());
	}

	/**
	 * Gets the items of a non-null value, in order.
	 *
	 * <p>
	 * For a {@link java.util.stream.Stream Stream}, {@link Iterator}, or
	 * {@link java.util.Enumeration Enumeration}, this consumes the value.
	 * </p>
	 *
	 * @param value value
	 * @return iterator over the value's items
	 */
	Iterator<E> iterator(T value);

	/**
	 * Creates a value of the target type holding the given items, in order.
	 *
	 * <ul>
	 * <li>A collection, array, or primitive array is a new instance; a collection
	 * is mutable, of the class named or the default for the interface named:
	 * {@link java.util.ArrayList ArrayList} for a list,
	 * {@link java.util.LinkedHashSet LinkedHashSet} for a set,
	 * {@link java.util.TreeSet TreeSet} for a sorted set, and
	 * {@link java.util.ArrayDeque ArrayDeque} for a collection, queue, or
	 * deque.</li>
	 * <li>An {@link Iterable} target returns {@code items} itself, not a copy, so
	 * {@code List.of(item)} reads as an immutable one-item {@link Iterable}.</li>
	 * <li>A {@link java.util.stream.Stream Stream}, {@link Iterator}, or
	 * {@link java.util.Enumeration Enumeration} is a lazy, single-use view over
	 * {@code items}, which must stay valid until it is consumed.</li>
	 * </ul>
	 *
	 * @param items items
	 * @return value of the target type
	 */
	T collect(Iterable<E> items);

}
