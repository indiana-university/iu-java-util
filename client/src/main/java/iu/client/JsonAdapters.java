/*
 * Copyright © 2024 Indiana University
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
package iu.client;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.client.IuJsonAdapter;

/**
 * Provides standard {@link IuJsonAdapter} instances.
 */
public final class JsonAdapters {

	private static final Map<Class<?>, Class<?>> ARRAY_TYPES = new WeakHashMap<>();

	/**
	 * {@link IuJsonAdapter} factory method.
	 * 
	 * @param type         Java type
	 * @param valueAdapter value type adapter
	 * @return {@link JsonAdapter}
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static IuJsonAdapter adapt(Type type, IuJsonAdapter valueAdapter) {
		Class erased = erase(type);

		if (erased == Object.class)
			return BasicJsonAdapter.INSTANCE;

		if (erased == Boolean.class)
			return BooleanJsonAdapter.INSTANCE;
		if (erased == boolean.class)
			return BooleanJsonAdapter.PRIMITIVE;

		if (erased == BigDecimal.class //
				|| erased == Number.class)
			return NumberAdapter.BIG_DECIMAL;
		if (erased == BigInteger.class)
			return NumberAdapter.BIG_INTEGER;
		if (erased == Byte.class)
			return NumberAdapter.BYTE;
		if (erased == byte.class)
			return NumberAdapter.BYTE_PRIMITIVE;
		if (erased == Double.class)
			return NumberAdapter.DOUBLE;
		if (erased == double.class)
			return NumberAdapter.DOUBLE_PRIMITIVE;
		if (erased == Float.class)
			return NumberAdapter.FLOAT;
		if (erased == float.class)
			return NumberAdapter.FLOAT_PRIMITIVE;
		if (erased == Long.class)
			return NumberAdapter.LONG;
		if (erased == long.class)
			return NumberAdapter.LONG_PRIMITIVE;
		if (erased == Integer.class)
			return NumberAdapter.INT;
		if (erased == int.class)
			return NumberAdapter.INT_PRIMITIVE;
		if (erased == Short.class)
			return NumberAdapter.SHORT;
		if (erased == short.class)
			return NumberAdapter.SHORT_PRIMITIVE;

		if (erased == CharSequence.class //
				|| erased == String.class)
			return TextJsonAdapter.INSTANCE;

		if (erased == byte[].class)
			return BinaryJsonAdapter.INSTANCE;

		if (erased == Calendar.class)
			return CalendarJsonAdapter.INSTANCE;
		if (erased == Date.class)
			return DateJsonAdapter.INSTANCE;
		if (erased == Duration.class)
			return ParsingJsonAdapter.of(Duration.class, Duration::parse);
		if (erased == Instant.class)
			return ParsingJsonAdapter.of(Instant.class, Instant::parse);
		if (erased == LocalDate.class)
			return ParsingJsonAdapter.of(LocalDate.class, LocalDate::parse);
		if (erased == LocalTime.class)
			return ParsingJsonAdapter.of(LocalTime.class, LocalTime::parse);
		if (erased == LocalDateTime.class)
			return ParsingJsonAdapter.of(LocalDateTime.class, LocalDateTime::parse);
		if (erased == OffsetDateTime.class)
			return ParsingJsonAdapter.of(OffsetDateTime.class, OffsetDateTime::parse);
		if (erased == OffsetTime.class)
			return ParsingJsonAdapter.of(OffsetTime.class, OffsetTime::parse);
		if (erased == Pattern.class)
			return ParsingJsonAdapter.of(Pattern.class, Pattern::compile);
		if (erased == Period.class)
			return ParsingJsonAdapter.of(Period.class, Period::parse);
		if (erased == SimpleTimeZone.class)
			return TimeZoneJsonAdapter.INSTANCE;
		if (erased == TimeZone.class)
			return TimeZoneJsonAdapter.INSTANCE;
		if (erased == ZonedDateTime.class)
			return ParsingJsonAdapter.of(ZonedDateTime.class, ZonedDateTime::parse);
		if (erased == ZoneId.class)
			return ParsingJsonAdapter.of(ZoneId.class, ZoneId::of);
		if (erased == ZoneOffset.class)
			return ParsingJsonAdapter.of(ZoneOffset.class, ZoneOffset::of);
		if (erased == URI.class)
			return ParsingJsonAdapter.of(URI.class, URI::create);
		if (erased == URL.class)
			return ParsingJsonAdapter.of(URL.class, a -> IuException.unchecked(() -> new URL(a)));

		if (erased.isEnum())
			return ParsingJsonAdapter.of(erased, v -> Enum.valueOf(erased, v));

		if (erased == Optional.class)
			if (valueAdapter != null)
				return new OptionalJsonAdapter(valueAdapter);
			else if (type instanceof ParameterizedType)
				return new OptionalJsonAdapter(IuJsonAdapter.of(item(type)));
			else
				return OptionalJsonAdapter.INSTANCE;

		if (erased.isArray()) {
			final var item = item(type);
			final IntFunction factory = n -> Array.newInstance(item, n);
			final IuJsonAdapter itemAdapter;
			if (valueAdapter != null)
				itemAdapter = valueAdapter;
			else
				itemAdapter = IuJsonAdapter.of(item);

			return new ArrayAdapter(itemAdapter, factory);
		}

		if (Iterable.class.isAssignableFrom(erased) //
				|| erased == Enumeration.class //
				|| erased == Iterator.class //
				|| erased == Stream.class) {
			final IuJsonAdapter itemAdapter;
			if (valueAdapter != null)
				itemAdapter = valueAdapter;
			else if (type instanceof ParameterizedType)
				itemAdapter = IuJsonAdapter.of(item(type));
			else
				itemAdapter = BasicJsonAdapter.INSTANCE;

			if (erased == Iterable.class)
				return new IterableAdapter(itemAdapter);

			if (erased == Collection.class //
					|| erased == Queue.class //
					|| erased == Deque.class //
					|| erased == ArrayDeque.class)
				return new CollectionAdapter(itemAdapter, ArrayDeque::new);

			if (erased == List.class //
					|| erased == ArrayList.class)
				return new CollectionAdapter(itemAdapter, ArrayList::new);

			if (erased == Set.class //
					|| erased == LinkedHashSet.class)
				return new CollectionAdapter(itemAdapter, LinkedHashSet::new);

			if (erased == SortedSet.class //
					|| erased == NavigableSet.class //
					|| erased == TreeSet.class)
				return new CollectionAdapter(itemAdapter, TreeSet::new);

			if (erased == HashSet.class)
				return new CollectionAdapter(itemAdapter, HashSet::new);

			if (erased == Enumeration.class)
				return new EnumerationAdapter(itemAdapter);
			if (erased == Iterator.class)
				return new IteratorAdapter(itemAdapter);
			if (erased == Stream.class)
				return new StreamAdapter(itemAdapter);
		}

		if (Map.class.isAssignableFrom(erased)) {
			if (valueAdapter == null)
				if (type instanceof ParameterizedType)
					valueAdapter = IuJsonAdapter.of(item(type));
				else
					valueAdapter = BasicJsonAdapter.INSTANCE;

			if (erased == Map.class //
					|| erased == LinkedHashMap.class)
				return new JsonObjectAdapter(valueAdapter, LinkedHashMap::new);

			if (erased == HashMap.class)
				return new JsonObjectAdapter(valueAdapter, HashMap::new);

			if (erased == SortedMap.class //
					|| erased == NavigableMap.class //
					|| erased == TreeMap.class)
				return new JsonObjectAdapter(valueAdapter, TreeMap::new);

			if (erased == Properties.class)
				return new JsonObjectAdapter(valueAdapter, Properties::new);
		}

		throw new UnsupportedOperationException("Unsupported for JSON conversion: " + type);
	}

	private static Class<?> erase(Type type) {
		if (type instanceof Class)
			return (Class<?>) type;
		else if (type instanceof GenericArrayType) {
			final var component = erase(((GenericArrayType) type).getGenericComponentType());
			var array = ARRAY_TYPES.get(component);
			if (array == null) {
				array = Array.newInstance(component, 0).getClass();
				synchronized (ARRAY_TYPES) {
					ARRAY_TYPES.put(component, array);
				}
			}
			return array;
		} else if (type instanceof ParameterizedType)
			return erase(((ParameterizedType) type).getRawType());
		else if (type instanceof TypeVariable)
			return erase(((TypeVariable<?>) type).getBounds()[0]);
		else // if (type instanceof WildcardType)
			return erase(((WildcardType) type).getUpperBounds()[0]);
	}

	private static Class<?> item(Type type) {
		if (type instanceof Class) {
			return ((Class<?>) type).getComponentType();
		} else if (type instanceof GenericArrayType)
			return erase(((GenericArrayType) type).getGenericComponentType());
		else {
			// assumes erase() was invoked and returned a supported type first
			final var p = (ParameterizedType) type;
			final var raw = erase(p);
			if (Map.class.isAssignableFrom(raw))
				return erase(p.getActualTypeArguments()[1]);
			else
				return erase(p.getActualTypeArguments()[0]);
		}
	}

	private JsonAdapters() {
	}
}
