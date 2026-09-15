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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
import java.time.temporal.Temporal;
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import edu.iu.IuObject;
import edu.iu.IuText;
import iu.client.EnumJsonAdapter;
import iu.client.JsonAdapters;
import iu.client.JsonDeserializer;
import iu.client.JsonSerializer;
import iu.client.ParsingJsonAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * Adapts JSON values to equivalent Java types.
 * 
 * @param <T> target type, <em>may</em> be unchecked
 */
public interface IuJsonAdapter<T> {

	/**
	 * Creates a functional JSON type adapter.
	 * 
	 * @param fromJson function that converts from JSON to the target type
	 * @param <T>      target type
	 * @return functional adapter
	 */
	static <T> IuJsonAdapter<T> from(Function<JsonValue, T> fromJson) {
		return new IuJsonAdapter<T>() {
			@Override
			public T fromJson(JsonValue jsonValue) {
				return fromJson.apply(jsonValue);
			}

			@Override
			public JsonValue toJson(T javaValue) {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Creates a functional JSON type adapter for text conversion.
	 * 
	 * @param parser parsing function
	 * @param <T>    target type
	 * @return functional adapter
	 */
	static <T> IuJsonAdapter<T> text(Function<String, T> parser) {
		return text(parser, T::toString);
	}

	/**
	 * Creates a functional JSON type adapter.
	 * 
	 * @param parser parsing function
	 * @param print  printing function
	 * @param <T>    target type
	 * @return functional adapter
	 */
	static <T> IuJsonAdapter<T> text(Function<String, T> parser, Function<T, String> print) {
		return new ParsingJsonAdapter<>(parser, print);
	}

	/**
	 * Creates a functional JSON type adapter.
	 * 
	 * @param toJson function that converts from the target type to JSON
	 * @param <T>    target type
	 * @return functional adapter
	 */
	static <T> IuJsonAdapter<T> to(Function<T, JsonValue> toJson) {
		return new IuJsonAdapter<T>() {
			@Override
			public T fromJson(JsonValue jsonValue) {
				throw new UnsupportedOperationException();
			}

			@Override
			public JsonValue toJson(T javaValue) {
				return toJson.apply(javaValue);
			}
		};
	}

	/**
	 * Creates a functional JSON type adapter.
	 * 
	 * @param fromJson function that converts from JSON to the target type
	 * @param toJson   function that converts from the target type to JSON
	 * @param <T>      target type
	 * @return functional adapter
	 */
	static <T> IuJsonAdapter<T> from(Function<JsonValue, T> fromJson, Function<T, JsonValue> toJson) {
		return new IuJsonAdapter<T>() {
			@Override
			public T fromJson(JsonValue jsonValue) {
				return fromJson.apply(jsonValue);
			}

			@Override
			public JsonValue toJson(T javaValue) {
				return toJson.apply(javaValue);
			}
		};
	}

	/**
	 * Creates a JSON type adapter that converts from a JavaBeans business object
	 * type to and from JSON.
	 * 
	 * <p>
	 * {@link #toJson(Object)} returns a {@link JsonObject} with an entry for each
	 * readable JavaBeans property of {@code type}, including properties inherited
	 * from superclasses and declared as interface default methods. Properties
	 * declared by {@link Object}, in particular {@link Object#getClass() class}, are
	 * skipped, as are properties with a null value.
	 * </p>
	 * 
	 * <p>
	 * {@link #fromJson(JsonValue)} behavior depends on {@code type}:
	 * </p>
	 * <ul>
	 * <li>An interface is wrapped by a {@link java.lang.reflect.Proxy} that reads
	 * property values directly from the {@link JsonObject}; see
	 * {@link IuJson#wrap(JsonObject, Class, Function)}.</li>
	 * <li>Any other type is instantiated using its no-arg constructor, then each
	 * JavaBeans property with a setter that maps to a defined JSON value is
	 * converted and applied. Setters without a corresponding JSON value are
	 * skipped, retaining the value assigned by the constructor.</li>
	 * </ul>
	 * 
	 * @param <T>                business object type
	 * @param type               business object class; <em>must</em> declare an
	 *                           accessible no-arg constructor to convert from JSON
	 *                           unless it is an interface
	 * @param propertyNameFormat property name format to use for converting to JSON
	 * @param valueAdapter       value adapter function
	 * @return {@link IuJsonAdapter}; {@link #toJson(Object) toJson} converts a null
	 *         business object to {@link JsonValue#NULL}, and
	 *         {@link #fromJson(JsonValue) fromJson} converts {@link JsonValue#NULL}
	 *         and an undefined value to null
	 */
	static <T> IuJsonAdapter<T> from(Class<T> type, IuJsonPropertyNameFormat propertyNameFormat,
			Function<Type, IuJsonAdapter<?>> valueAdapter) {
		final var options = IuJsonSerializationOptions.of(propertyNameFormat);
		return from(type, () -> options, valueAdapter);
	}

	/**
	 * Creates a JSON type adapter that converts from a JavaBeans business object
	 * type to and from JSON, with dynamically supplied options.
	 * 
	 * <p>
	 * Behaves as {@link #from(Class, IuJsonPropertyNameFormat, Function)}, except
	 * that {@code options} is read once for each conversion, so this adapter
	 * observes an options change without being recreated. Whether a property with
	 * a null value is included is controlled by
	 * {@link IuJsonSerializationOptions#isIncludeNullProperties()}.
	 * </p>
	 * 
	 * @param <T>          business object type
	 * @param type         business object class; <em>must</em> declare an
	 *                     accessible no-arg constructor to convert from JSON
	 *                     unless it is an interface
	 * @param options      supplies the options in effect for converting to JSON;
	 *                     <em>should</em> return quickly, as it is called on every
	 *                     conversion. A null value reads as
	 *                     {@link IuJsonSerializationOptions#DEFAULT}
	 * @param valueAdapter value adapter function
	 * @return {@link IuJsonAdapter}; {@link #toJson(Object) toJson} converts a
	 *         null business object to {@link JsonValue#NULL}, and
	 *         {@link #fromJson(JsonValue) fromJson} converts
	 *         {@link JsonValue#NULL} and an undefined value to null
	 */
	static <T> IuJsonAdapter<T> from(Class<T> type, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> valueAdapter) {
		return from(v -> v == null || JsonValue.NULL.equals(v) //
				? null //
				: JsonDeserializer.deserialize(type, v.asJsonObject(), valueAdapter), //
				v -> v == null //
						? JsonValue.NULL //
						: JsonSerializer.serialize(type, v, options, valueAdapter));
	}

	/**
	 * Gets a JSON type adapter for common-case conversion to/from a simple type or
	 * {@link IuObject#isPlatformName(String) non-platform} JavaBeans type.
	 * 
	 * <p>
	 * Returns {@link #from(Class, IuJsonPropertyNameFormat, Function)} for a
	 * {@link IuObject#isPlatformName(String) non-platform} interface or class.
	 * {@link Class#isPrimitive() Primitive} and {@link Class#isArray() array} types
	 * are handled by {@link #of(Type, Function)} even when non-platform. An
	 * {@link Class#isEnum() enum} type converts to text, or to a
	 * {@link JsonObject} describing the constant when
	 * {@link IuJsonSerializationOptions#isEnumAsObject()}.
	 * </p>
	 * 
	 * @param type               business object class
	 * @param propertyNameFormat property name format to use for converting to JSON
	 * @return {@link IuJsonAdapter}
	 */
	static IuJsonAdapter<?> adapt(Type type, IuJsonPropertyNameFormat propertyNameFormat) {
		final var options = IuJsonSerializationOptions.of(propertyNameFormat);
		return adapt(type, () -> options);
	}

	/**
	 * Gets a JSON type adapter for common-case conversion to/from a simple type or
	 * {@link IuObject#isPlatformName(String) non-platform} JavaBeans type, with
	 * dynamically supplied options.
	 * 
	 * <p>
	 * Behaves as {@link #adapt(Type, IuJsonPropertyNameFormat)}, except that
	 * {@code options} is read once for each conversion, so an adapter observes an
	 * options change without being recreated.
	 * </p>
	 * 
	 * <p>
	 * The same supplier is used for every nested value type, so options apply to
	 * business object and enum properties, and to business objects and enum values
	 * nested in an array, {@link java.util.Collection Collection},
	 * {@link java.util.Map Map}, {@link java.util.Optional Optional}, or
	 * {@link java.util.stream.Stream Stream}, at any depth.
	 * </p>
	 * 
	 * @param type    business object class
	 * @param options supplies the options in effect for converting to JSON;
	 *                <em>should</em> return quickly, as it is called on every
	 *                conversion. A null value reads as
	 *                {@link IuJsonSerializationOptions#DEFAULT}
	 * @return {@link IuJsonAdapter}
	 */
	static IuJsonAdapter<?> adapt(Type type, Supplier<IuJsonSerializationOptions> options) {
		return adapt(type, options, a -> adapt(a, options));
	}

	/**
	 * Gets a JSON type adapter for common-case conversion to/from a simple type or
	 * {@link IuObject#isPlatformName(String) non-platform} JavaBeans type, using a
	 * custom value adapter function.
	 * 
	 * <p>
	 * Behaves as {@link #adapt(Type, IuJsonPropertyNameFormat)}, except that
	 * {@code valueAdapter} decides how every nested value type converts. Unlike
	 * {@link #adapt(Type, IuJsonPropertyNameFormat)}, this method applies the
	 * JavaBeans decision to {@code type} only; a nested type is whatever
	 * {@code valueAdapter} answers for it, so a function that should treat a
	 * nested {@link IuObject#isPlatformName(String) non-platform} class as a
	 * JavaBeans type <em>must</em> call back into this method itself.
	 * </p>
	 * 
	 * @param type               business object class or type
	 * @param propertyNameFormat property name format to use for converting to JSON
	 * @param valueAdapter       factory function for supplying child value type
	 *                           adapters; called for every nested type, and
	 *                           responsible for its own recursion
	 * @return {@link IuJsonAdapter}
	 */
	static IuJsonAdapter<?> adapt(Type type, IuJsonPropertyNameFormat propertyNameFormat,
			Function<Type, IuJsonAdapter<?>> valueAdapter) {
		final var options = IuJsonSerializationOptions.of(propertyNameFormat);
		return adapt(type, () -> options, valueAdapter);
	}

	/**
	 * Gets a JSON type adapter for common-case conversion to/from a simple type or
	 * {@link IuObject#isPlatformName(String) non-platform} JavaBeans type, using a
	 * custom value adapter function and dynamically supplied options.
	 * 
	 * <p>
	 * This is the common implementation behind every {@code adapt} method: an
	 * {@link Class#isEnum() enum} type converts as described by
	 * {@link IuJsonSerializationOptions#isEnumAsObject()}, {@code type} converts as
	 * a JavaBeans type when it is a
	 * {@link IuObject#isPlatformName(String) non-platform} interface or class, and
	 * everything else converts through {@link #of(Type, Function)}, including when
	 * {@link Class#isPrimitive() primitive} or an {@link Class#isArray() array}.
	 * Nested value types are resolved by {@code valueAdapter}, which is
	 * responsible for its own recursion; {@link #adapt(Type, Supplier)} supplies a
	 * function that recurses here.
	 * </p>
	 * 
	 * @param type         business object class or type
	 * @param options      supplies the options in effect for converting to JSON;
	 *                     <em>should</em> return quickly, as it is called on every
	 *                     conversion. A null value reads as
	 *                     {@link IuJsonSerializationOptions#DEFAULT}
	 * @param valueAdapter factory function for supplying child value type adapters;
	 *                     called for every nested type, and responsible for its own
	 *                     recursion
	 * @return {@link IuJsonAdapter}
	 */
	static IuJsonAdapter<?> adapt(Type type, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> valueAdapter) {
		final var c = JsonAdapters.erase(type);
		if (c.isEnum())
			return EnumJsonAdapter.of(c, options, valueAdapter);

		if (!IuObject.isPlatformName(c.getName()) //
				&& !c.isPrimitive() //
				&& !c.isArray())
			return from(c, options, valueAdapter);

		return IuJsonAdapter.of(type, valueAdapter);
	}

	/**
	 * Gets a JSON type adapter for common-case conversion to/from a
	 * {@link IuObject#isPlatformName(String) non-platform} JavaBeans type.
	 * 
	 * @param <T>                business object type
	 * @param type               business object class
	 * @param propertyNameFormat property name format to use for converting to JSON
	 * @return {@link IuJsonAdapter}
	 * @see #adapt(Type, IuJsonPropertyNameFormat)
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> adapt(Class<T> type, IuJsonPropertyNameFormat propertyNameFormat) {
		return (IuJsonAdapter<T>) adapt((Type) type, propertyNameFormat);
	}

	/**
	 * Gets a JSON type adapter for common-case conversion to/from a
	 * {@link IuObject#isPlatformName(String) non-platform} JavaBeans type, with
	 * dynamically supplied options.
	 * 
	 * @param <T>     business object type
	 * @param type    business object class
	 * @param options supplies the options in effect for converting to JSON;
	 *                <em>should</em> return quickly, as it is called on every
	 *                conversion. A null value reads as
	 *                {@link IuJsonSerializationOptions#DEFAULT}
	 * @return {@link IuJsonAdapter}
	 * @see #adapt(Type, Supplier)
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> adapt(Class<T> type, Supplier<IuJsonSerializationOptions> options) {
		return (IuJsonAdapter<T>) adapt((Type) type, options);
	}

	/**
	 * Provides a basic JSON type adapter.
	 * 
	 * <p>
	 * Returns an adapter that:
	 * </p>
	 * <ul>
	 * <li>Handles undefined (null) JSON values as null</li>
	 * <li>Returns {@link JsonValue} as-is</li>
	 * <li>Builds {@link JsonObjectBuilder} and {@link JsonArrayBuilder} if provided
	 * to {@link #toJson(Object)} as Java values</li>
	 * <li>Converts between null and {@link JsonValue#NULL}</li>
	 * <li>Converts between {@link Boolean} and
	 * {@link JsonValue#TRUE}/{@link JsonValue#FALSE}</li>
	 * <li>Converts between {@link String} and {@link JsonString}</li>
	 * <li>Converts between {@link Number} and {@link JsonNumber},
	 * {@link #fromJson(JsonValue)} returns {@link BigDecimal}</li>
	 * <li>Converts between {@link List List&lt;?&gt;} and {@link JsonArray}, with
	 * recursive item conversion</li>
	 * <li>Converts between {@link Map Map&lt;String, ?&gt;} and {@link JsonObject},
	 * with recursive conversion</li>
	 * <li>Converts irreversibly to {@link JsonValue} for other types listed in
	 * {@link #of(Class)}.</li>
	 * </ul>
	 * 
	 * <p>
	 * Equivalent to {@link #of(Class) of(Object.class)}
	 * </p>
	 * 
	 * @param <T> target type, <em>may</em> be used for unchecked cast to a one of
	 *            the types listed above. For full generic type support and two-way
	 *            conversion use {@link #of(Class)}
	 * @return {@link IuJsonAdapter}
	 * @throws ClassCastException (potentially upstream) If the target type doesn't
	 *                            match the return type
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> basic() {
		return JsonAdapters.adapt(Object.class, null);
	}

	/**
	 * Provides a JSON type adapter for the erasure of a generic type.
	 * 
	 * <p>
	 * The adapter returned is aware of {@link ParameterizedType} and
	 * {@link GenericArrayType} arguments indicating item, component, and value
	 * types. All types referenced by generic type arguments must be included in the
	 * list below.
	 * </p>
	 * 
	 * <p>
	 * Supports:
	 * </p>
	 * <ul>
	 * <li>{@link Boolean} and {@link Boolean#TYPE boolean}</li>
	 * <li>{@link Object}, see {@link #basic()} for Object conversion rules</li>
	 * <li>{@link #toJson(Object)} as {@link JsonNumber}
	 * <ul>
	 * <li>{@link BigDecimal}</li>
	 * <li>{@link Byte} and {@link Byte#TYPE byte}</li>
	 * <li>{@link Double} and {@link Double#TYPE byte}</li>
	 * <li>{@link Float} and {@link Float#TYPE byte}</li>
	 * <li>{@link Integer} and {@link Integer#TYPE int}</li>
	 * <li>{@link Long} and {@link Long#TYPE long}</li>
	 * <li>{@link Number}, as {@link BigDecimal}</li>
	 * <li>{@link Short} and {@link Short#TYPE short}</li>
	 * </ul>
	 * </li>
	 * <li>{@link #toJson(Object)} returns {@link JsonString}
	 * <ul>
	 * <li>byte[], via {@link IuText#base64(String)} and
	 * {@link IuText#base64(byte[])}</li>
	 * <li>{@link BigInteger}</li>
	 * <li>{@link CharSequence}</li>
	 * <li>{@link Calendar}, as {@link Date}</li>
	 * <li>{@link CharSequence}, as {@link String}</li>
	 * <li>{@link Date}, as {@link Temporal}</li>
	 * <li>{@link Duration}</li>
	 * <li>{@link Enum} subtypes; {@link #fromJson(JsonValue)} also accepts a
	 * {@link JsonObject} naming the constant in its {@code name} property, as
	 * written by {@link IuJsonSerializationOptions#isEnumAsObject()}</li>
	 * <li>{@link Instant}</li>
	 * <li>{@link LocalDate}</li>
	 * <li>{@link LocalTime}</li>
	 * <li>{@link LocalDateTime}</li>
	 * <li>{@link OffsetDateTime}</li>
	 * <li>{@link OffsetTime}</li>
	 * <li>{@link Optional}</li>
	 * <li>{@link Period}</li>
	 * <li>{@link SimpleTimeZone}</li>
	 * <li>{@link String}</li>
	 * <li>{@link Pattern}</li>
	 * <li>{@link TimeZone}</li>
	 * <li>{@link URI}</li>
	 * <li>{@link URL}</li>
	 * <li>{@link ZonedDateTime}</li>
	 * <li>{@link ZoneId}</li>
	 * <li>{@link ZoneOffset}</li>
	 * </ul>
	 * </li>
	 * <li>{@link #toJson(Object)} as {@link JsonArray}
	 * <ul>
	 * <li>{@link Class#isArray() Array} type</li>
	 * <li>{@link ArrayList}</li>
	 * <li>{@link Collection}, as {@link Queue}</li>
	 * <li>{@link Deque}, as {@link ArrayDeque}</li>
	 * <li>{@link Enumeration}</li>
	 * <li>{@link HashSet}</li>
	 * <li>{@link Iterable}, as {@link Collection}</li>
	 * <li>{@link Iterator}</li>
	 * <li>{@link LinkedHashSet}</li>
	 * <li>{@link List}, as {@link ArrayList}</li>
	 * <li>{@link NavigableSet}, as {@link TreeSet}</li>
	 * <li>{@link Queue}, as {@link Deque}</li>
	 * <li>{@link Set}, as {@link LinkedHashSet}</li>
	 * <li>{@link SortedSet}, as {@link NavigableSet}</li>
	 * <li>{@link Properties}, enforces values as {@link String}</li>
	 * <li>{@link TreeSet}</li>
	 * <li>{@link Stream}</li>
	 * </ul>
	 * </li>
	 * <li>{@link #toJson(Object)} as {@link JsonObject}:
	 * <ul>
	 * <li>{@link LinkedHashMap}</li>
	 * <li>{@link HashMap}</li>
	 * <li>{@link Map}, as {@link LinkedHashMap}</li>
	 * <li>{@link SortedMap}, as {@link TreeMap}</li>
	 * <li>{@link Properties}</li>
	 * <li>{@link TreeMap}</li>
	 * </ul>
	 * </li>
	 * </ul>
	 * 
	 * @param <T>  target type
	 * @param type target type
	 * @return {@link IuJsonAdapter}
	 * @throws UnsupportedOperationException if {@code type}, or a type named by one
	 *                                       of its generic type arguments, is not
	 *                                       in the list above. In particular there
	 *                                       is no adapter for {@link Void},
	 *                                       {@link Void#TYPE void}, or
	 *                                       {@link Character}; a method returning
	 *                                       void is expected to be excluded by the
	 *                                       caller rather than adapted
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> of(Type type) {
		return JsonAdapters.adapt(type, null);
	}

	/**
	 * Provides a standard JSON type adapter for a Java value.
	 * 
	 * @param <T>  target type
	 * @param type target type
	 * @return {@link IuJsonAdapter}
	 * @see #of(Type)
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> of(Class<T> type) {
		return JsonAdapters.adapt((Type) type, null);
	}

	/**
	 * Provides a JSON type adapter that delegates to another adapter for
	 * parameterized values.
	 * 
	 * @param <T>          target type
	 * @param type         target type
	 * @param valueAdapter Value type adapter for {@link JsonStructure} conversion,
	 *                     may be null if the Java type doesn't declare parameters,
	 *                     or to use a standard value adapter
	 * @return {@link IuJsonAdapter}
	 * @see #of(Type)
	 */
	static <T> IuJsonAdapter<T> of(Class<? super T> type, IuJsonAdapter<?> valueAdapter) {
		return of(type, a -> valueAdapter);
	}

	/**
	 * Provides a JSON type adapter that delegates to another adapter for
	 * parameterized values.
	 * 
	 * @param <T>          target type
	 * @param type         target type
	 * @param valueAdapter Factory function for supplying a value type adapter for
	 *                     {@link JsonStructure} conversion based on the item type.
	 * @return {@link IuJsonAdapter}
	 * @see #of(Type)
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> of(Type type, Function<Type, IuJsonAdapter<?>> valueAdapter) {
		return JsonAdapters.adapt(type, valueAdapter);
	}

	/**
	 * Converts a JSON parameter value to its Java equivalent.
	 * 
	 * @param jsonValue JSON value
	 * @return Java equivalent
	 */
	T fromJson(JsonValue jsonValue);

	/**
	 * Converts a value to its JSON equivalent.
	 * 
	 * @param value value
	 * @return JSON equivalent
	 */
	JsonValue toJson(T value);

}