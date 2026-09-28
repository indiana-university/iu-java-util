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
package iu.client;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonProperties;
import jakarta.json.JsonValue;

/**
 * Provides standard {@link IuJsonAdapter} instances.
 */
public final class JsonAdapters {

	private static final ClassValue<Class<?>> ARRAY_TYPES = new ClassValue<>() {
		@Override
		protected Class<?> computeValue(Class<?> component) {
			return Array.newInstance(component, 0).getClass();
		}
	};

	/**
	 * {@link IuJsonAdapter} factory method.
	 *
	 * <p>
	 * A {@link WildcardType} or {@link TypeVariable} resolves to its first upper
	 * bound and is adapted as that instead, so a property declared
	 * {@code Iterable<? extends Foo>} converts the same way {@code Iterable<Foo>}
	 * does. Resolution goes back out through {@code valueAdapter} rather than
	 * recursing here, because whether the bound converts as a JavaBean is the
	 * caller's to decide &mdash; this factory only knows the types it handles by
	 * name, and would refuse a bound it has no case for.
	 * </p>
	 *
	 * @param type         Java type
	 * @param valueAdapter value type adapter
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("rawtypes")
	public static IuJsonAdapter adapt(Type type, Function<Type, IuJsonAdapter<?>> valueAdapter) {
		return adapt(type, valueAdapter, ItemScope.NONE);
	}

	/**
	 * {@link IuJsonAdapter} factory method, tracking the items of an array,
	 * collection, or map as {@link #adapt(Type, Function)} converts them.
	 *
	 * @param type         Java type
	 * @param valueAdapter value type adapter
	 * @param scope        tracks the item converting, for an array, collection,
	 *                     or map
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("rawtypes")
	public static IuJsonAdapter adapt(Type type, Function<Type, IuJsonAdapter<?>> valueAdapter, ItemScope scope) {
		return adapt(type, valueAdapter, t -> adapt(t, null), scope);
	}

	/**
	 * {@link IuJsonAdapter} factory method, tracking the items of an array,
	 * collection, or map, and converting a map's keys by a function of their
	 * own.
	 *
	 * @param type         Java type
	 * @param valueAdapter value type adapter
	 * @param keyAdapter   map key type adapter, whose values convert to and from
	 *                     text
	 * @param scope        tracks the item converting, for an array, collection,
	 *                     or map
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static IuJsonAdapter adapt(Type type, Function<Type, IuJsonAdapter<?>> valueAdapter,
			Function<Type, IuJsonAdapter<?>> keyAdapter, ItemScope scope) {
		final var bound = bound(type);
		if (bound != null)
			if (valueAdapter != null)
				return valueAdapter.apply(bound);
			else
				return adapt(bound, null, keyAdapter, scope);

		Class erased = erase(type);

		if (erased == Object.class)
			return BasicJsonAdapter.INSTANCE;

		// before Map and Iterable, which JsonObject and JsonArray implement
		if (JsonValue.class.isAssignableFrom(erased))
			return new JsonValueAdapter(erased);

		// an index read converts by the value adapter, or else as the JSON-B call
		// in progress does
		if (erased == IuJsonProperties.class)
			return new PropertiesJsonAdapter(valueAdapter);

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
		if (erased == Character.class)
			return CharacterJsonAdapter.INSTANCE;
		if (erased == char.class)
			return CharacterJsonAdapter.PRIMITIVE;

		if (erased == byte[].class)
			return BinaryJsonAdapter.INSTANCE;

		if (erased == Calendar.class //
				|| erased == GregorianCalendar.class)
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
			return ParsingJsonAdapter.of(LocalTime.class, LocalTime::parse, DateTimeFormatter.ISO_LOCAL_TIME::format);
		if (erased == LocalDateTime.class)
			return ParsingJsonAdapter.of(LocalDateTime.class, LocalDateTime::parse,
					DateTimeFormatter.ISO_LOCAL_DATE_TIME::format);
		if (erased == OffsetDateTime.class)
			return ParsingJsonAdapter.of(OffsetDateTime.class, OffsetDateTime::parse,
					DateTimeFormatter.ISO_OFFSET_DATE_TIME::format);
		if (erased == OffsetTime.class)
			return ParsingJsonAdapter.of(OffsetTime.class, OffsetTime::parse, DateTimeFormatter.ISO_OFFSET_TIME::format);
		if (erased == Pattern.class)
			return ParsingJsonAdapter.of(Pattern.class, Pattern::compile);
		if (erased == Period.class)
			return ParsingJsonAdapter.of(Period.class, Period::parse);
		if (erased == SimpleTimeZone.class)
			return TimeZoneJsonAdapter.INSTANCE;
		if (erased == TimeZone.class)
			return TimeZoneJsonAdapter.INSTANCE;
		if (erased == ZonedDateTime.class)
			return ParsingJsonAdapter.of(ZonedDateTime.class, ZonedDateTime::parse,
					DateTimeFormatter.ISO_ZONED_DATE_TIME::format);
		if (erased == ZoneId.class)
			return ParsingJsonAdapter.of(ZoneId.class, ZoneId::of);
		if (erased == ZoneOffset.class)
			return ParsingJsonAdapter.of(ZoneOffset.class, ZoneOffset::of);
		if (erased == URI.class)
			return ParsingJsonAdapter.of(URI.class, URI::create);
		if (erased == URL.class)
			return ParsingJsonAdapter.of(URL.class, a -> IuException.unchecked(() -> URI.create(a).toURL()));
		if (erased == UUID.class)
			return ParsingJsonAdapter.of(UUID.class, UUID::fromString);

		if (erased.isEnum())
			return EnumJsonAdapter.of(erased);

		if (erased == OptionalInt.class)
			return OptionalNumberAdapter.INT;
		if (erased == OptionalLong.class)
			return OptionalNumberAdapter.LONG;
		if (erased == OptionalDouble.class)
			return OptionalNumberAdapter.DOUBLE;

		if (erased == Optional.class)
			if (valueAdapter != null)
				return new OptionalJsonAdapter(valueAdapter.apply(item(type)));
			else if (type instanceof ParameterizedType)
				return new OptionalJsonAdapter(IuJsonAdapter.of(item(type)));
			else
				return OptionalJsonAdapter.INSTANCE;

		if (erased.isArray()) {
			final var item = item(type);
			final IuJsonAdapter itemAdapter;
			if (valueAdapter != null)
				itemAdapter = valueAdapter.apply(item);
			else
				itemAdapter = IuJsonAdapter.of(item);

			final var component = erased.getComponentType();
			if (component.isPrimitive())
				return scoped(new PrimitiveArrayAdapter(itemAdapter, component), scope);

			final IntFunction factory = n -> Array.newInstance(component, n);
			return scoped(new ArrayAdapter(itemAdapter, factory), scope);
		}

		if (Iterable.class.isAssignableFrom(erased) //
				|| erased == Enumeration.class //
				|| erased == Iterator.class //
				|| erased == Stream.class) {
			final IuJsonAdapter itemAdapter;
			if (valueAdapter != null)
				itemAdapter = valueAdapter.apply(item(type));
			else if (type instanceof ParameterizedType)
				itemAdapter = IuJsonAdapter.of(item(type));
			else
				itemAdapter = BasicJsonAdapter.INSTANCE;

			if (erased == Iterable.class)
				return scoped(new IterableAdapter(itemAdapter), scope);

			if (erased == Collection.class //
					|| erased == Queue.class //
					|| erased == Deque.class //
					|| erased == ArrayDeque.class)
				return scoped(new CollectionAdapter(itemAdapter, ArrayDeque::new), scope);

			if (erased == List.class //
					|| erased == ArrayList.class)
				return scoped(new CollectionAdapter(itemAdapter, ArrayList::new), scope);

			if (erased == LinkedList.class)
				return scoped(new CollectionAdapter(itemAdapter, LinkedList::new), scope);

			if (erased == PriorityQueue.class)
				return scoped(new CollectionAdapter(itemAdapter, PriorityQueue::new), scope);

			if (erased == EnumSet.class) {
				final Class element = enumType(item(type), type);
				return scoped(new CollectionAdapter(itemAdapter, () -> EnumSet.noneOf(element)), scope);
			}

			if (erased == Set.class //
					|| erased == LinkedHashSet.class)
				return scoped(new CollectionAdapter(itemAdapter, LinkedHashSet::new), scope);

			if (erased == SortedSet.class //
					|| erased == NavigableSet.class //
					|| erased == TreeSet.class)
				return scoped(new CollectionAdapter(itemAdapter, TreeSet::new), scope);

			if (erased == HashSet.class)
				return scoped(new CollectionAdapter(itemAdapter, HashSet::new), scope);

			if (erased == Enumeration.class)
				return scoped(new EnumerationAdapter(itemAdapter), scope);
			if (erased == Iterator.class)
				return scoped(new IteratorAdapter(itemAdapter), scope);
			if (erased == Stream.class)
				return scoped(new StreamAdapter(itemAdapter), scope);
		}

		if (Map.class.isAssignableFrom(erased)) {
			if (valueAdapter == null)
				if (type instanceof ParameterizedType)
					valueAdapter = IuJsonAdapter::of;
				else
					valueAdapter = a -> BasicJsonAdapter.INSTANCE;

			// a key is a JSON name, not a value: it converts through the key type's
			// built-in text conversion, never through valueAdapter
			final Type keyType;
			final IuJsonAdapter keys;
			if (type instanceof ParameterizedType) {
				keyType = ((ParameterizedType) type).getActualTypeArguments()[0];
				keys = keyAdapter.apply(keyType);
			} else {
				keyType = Object.class;
				keys = BasicJsonAdapter.INSTANCE;
			}

			final Supplier<Map> factory;
			if (erased == EnumMap.class) {
				final Class keyClass = enumType(keyType, type);
				factory = () -> new EnumMap(keyClass);
			} else if (erased == Map.class //
					|| erased == LinkedHashMap.class)
				factory = LinkedHashMap::new;
			else if (erased == HashMap.class)
				factory = HashMap::new;
			else if (erased == SortedMap.class //
					|| erased == NavigableMap.class //
					|| erased == TreeMap.class)
				factory = TreeMap::new;
			else if (erased == Properties.class)
				factory = Properties::new;
			else
				factory = null;

			if (factory != null) {
				final var adapter = new JsonObjectAdapter(keys, valueAdapter.apply(item(type)), factory);
				adapter.scope = scope;
				return adapter;
			}
		}

		throw new UnsupportedOperationException("Unsupported for JSON conversion: " + type);
	}

	/**
	 * Gets the upper bound a type is a stand-in for.
	 *
	 * <p>
	 * Answers only for the two forms that name a bound rather than a type: a
	 * wildcard, as in {@code Iterable<? extends Foo>}, and a type variable, as in
	 * a generic bean's {@code Iterable<T>}. Both erase to their bound, so both
	 * convert as it.
	 * </p>
	 *
	 * @param type Java type
	 * @return first upper bound; null if {@code type} names a type of its own
	 */
	private static Type bound(Type type) {
		if (type instanceof WildcardType)
			return ((WildcardType) type).getUpperBounds()[0];
		else if (type instanceof TypeVariable)
			return ((TypeVariable<?>) type).getBounds()[0];
		else
			return null;
	}

	/**
	 * Erases a {@link Type} to its equivalent raw {@link Class}.
	 *
	 * @param type type
	 * @return raw class
	 */
	public static Class<?> erase(Type type) {
		if (type instanceof Class)
			return (Class<?>) type;
		else if (type instanceof GenericArrayType)
			return ARRAY_TYPES.get(erase(((GenericArrayType) type).getGenericComponentType()));
		else if (type instanceof ParameterizedType)
			return erase(((ParameterizedType) type).getRawType());
		else if (type instanceof TypeVariable)
			return erase(((TypeVariable<?>) type).getBounds()[0]);
		else // if (type instanceof WildcardType)
			return erase(((WildcardType) type).getUpperBounds()[0]);
	}

	private static Type item(Type type) {
		if (type instanceof Class) {
			final var c = (Class<?>) type;
			if (c.isArray())
				return ((Class<?>) type).getComponentType();
			else
				return Object.class;
		} else if (type instanceof GenericArrayType)
			return ((GenericArrayType) type).getGenericComponentType();
		else {
			// assumes erase() was invoked and returned a supported type first
			final var p = (ParameterizedType) type;
			final var raw = erase(p);
			if (Map.class.isAssignableFrom(raw))
				return p.getActualTypeArguments()[1];
			else
				return p.getActualTypeArguments()[0];
		}
	}

	private static <A extends JsonArrayAdapter<?, ?>> A scoped(A adapter, ItemScope scope) {
		adapter.scope = scope;
		return adapter;
	}

	/**
	 * Determines if values of a type are scalar: text, a number, or a boolean.
	 *
	 * @param type type
	 * @return true for {@link CharSequence}, {@link Number}, {@link Boolean}, their
	 *         subtypes, and the primitive number types and {@code boolean}
	 */
	public static boolean isScalar(Type type) {
		final var c = (Class<?>) GenericTypes.box(erase(type));
		return CharSequence.class.isAssignableFrom(c) //
				|| Number.class.isAssignableFrom(c) //
				|| c == Boolean.class;
	}

	/**
	 * Determines if a type is broad: a value declared as it may be of nearly any
	 * type, so converts as its runtime type.
	 *
	 * @param type type
	 * @return true for {@link Object}, {@link java.io.Serializable}, and an
	 *         interface in {@code java.lang} or one of its subpackages, such as
	 *         {@link Comparable} or {@code java.lang.constant.Constable}, that
	 *         isn't {@link #isScalar(Type) scalar}
	 */
	public static boolean isBroad(Type type) {
		final var c = erase(type);
		if (c == Object.class || c == Serializable.class)
			return true;
		if (!c.isInterface() || isScalar(c))
			return false;
		final var packageName = c.getPackageName();
		return packageName.equals("java.lang") || packageName.startsWith("java.lang.");
	}

	/**
	 * Gets the type to convert a value as when no declared type applies.
	 *
	 * @param value value
	 * @return {@link Enum#getDeclaringClass()} for an enum constant, including one
	 *         with a class body; the wrapped interface for a {@link JsonProxy};
	 *         otherwise the value's class, or {@link Object} for null
	 */
	public static Class<?> runtimeType(Object value) {
		if (value == null)
			return Object.class;
		else if (value instanceof Enum)
			return ((Enum<?>) value).getDeclaringClass();
		else if (Proxy.isProxyClass(value.getClass()) //
				&& Proxy.getInvocationHandler(value) instanceof JsonProxy)
			return value.getClass().getInterfaces()[0];
		else
			return value.getClass();
	}

	/**
	 * Gets a conversion for a value of a {@link #isBroad(Type) broad} declared
	 * type, by its runtime type.
	 *
	 * @param valueAdapter value adapter function, given the runtime type of each
	 *                     value written
	 * @return {@link IuJsonAdapter}
	 */
	public static IuJsonAdapter<Object> runtime(Function<Type, IuJsonAdapter<?>> valueAdapter) {
		return new RuntimeTypeAdapter(valueAdapter);
	}

	private static final Map<Class<?>, Type> CONVERSION_TYPES = new ConcurrentHashMap<>();

	private static final Class<?>[] CONVERSION_INTERFACES = { CharSequence.class, Map.class, List.class, Set.class,
			Collection.class, Iterable.class, Iterator.class, Enumeration.class, Stream.class };

	/**
	 * Gets the type a platform class converts as, for a class with no built-in
	 * conversion of its own, such as a JDK-internal collection or a subclass of a
	 * supported type.
	 *
	 * <p>
	 * The nearest superclass with a built-in conversion is used, short of
	 * {@link Object}; failing that, the first of {@link CharSequence}, {@link Map},
	 * {@link List}, {@link Set}, {@link Collection}, {@link Iterable},
	 * {@link Iterator}, {@link Enumeration}, and {@link Stream} the class
	 * implements.
	 * </p>
	 *
	 * @param type platform class
	 * @return {@code type} itself if it has a conversion of its own; the type it
	 *         converts as; {@link Object} if none applies
	 */
	public static Type conversionType(Class<?> type) {
		return CONVERSION_TYPES.computeIfAbsent(type, JsonAdapters::resolveConversionType);
	}

	private static Type resolveConversionType(Class<?> type) {
		for (Class<?> next = type; next != null && next != Object.class; next = next.getSuperclass())
			if (hasConversion(next))
				return next;

		for (final var conversionInterface : CONVERSION_INTERFACES)
			if (conversionInterface.isAssignableFrom(type))
				return conversionInterface;

		return Object.class;
	}

	private static boolean hasConversion(Class<?> type) {
		try {
			adapt(type, null);
			return true;
		} catch (UnsupportedOperationException e) {
			return false;
		}
	}

	/**
	 * Gets the enum type an {@link EnumSet} or {@link EnumMap} is keyed by.
	 *
	 * @param argument type argument naming the enum type
	 * @param type     {@link EnumSet} or {@link EnumMap} type
	 * @return enum type
	 * @throws UnsupportedOperationException if the argument isn't an enum type, as
	 *                                       for a raw type
	 */
	private static Class<?> enumType(Type argument, Type type) {
		final var c = erase(argument);
		if (c.isEnum())
			return c;
		else
			throw new UnsupportedOperationException("Unsupported for JSON conversion: " + type
					+ "; declare the enum type, as in EnumSet<E> or EnumMap<E, V>");
	}

	/**
	 * Describes a JSON value of the wrong shape for the type being read.
	 *
	 * @param expected describes what the type reads, such as "a string"
	 * @param found    JSON value type or parser event found instead
	 * @return {@link IllegalArgumentException} to throw
	 */
	public static IllegalArgumentException expected(String expected, Object found) {
		return new IllegalArgumentException("expected " + expected + ", found " + found);
	}

	private JsonAdapters() {
	}
}
