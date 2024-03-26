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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import edu.iu.IuText;
import iu.client.JsonAdapters;
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
	 * @param toJava function that converts from JSON to the target type
	 * @param toJson function that converts from the target type to JSON
	 * @param <T>    target type
	 * @return functional adapter
	 */
	static <T> IuJsonAdapter<T> from(Function<JsonValue, T> toJava, Function<T, JsonValue> toJson) {
		return new IuJsonAdapter<T>() {
			@Override
			public T fromJson(JsonValue jsonValue) {
				return toJava.apply(jsonValue);
			}

			@Override
			public JsonValue toJson(T javaValue) {
				return toJson.apply(javaValue);
			}
		};
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
	 * <li>{@link Void} and {@link Void#TYPE void}, supporting only null values</li>
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
	 * <li>{@link Enum} subtypes</li>
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
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> of(Class<T> type, IuJsonAdapter<?> valueAdapter) {
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
	 * Converts a Java parameter value to its JSON equivalent.
	 * 
	 * @param javaValue JSON value
	 * @return Java equivalent
	 */
	JsonValue toJson(T javaValue);

}