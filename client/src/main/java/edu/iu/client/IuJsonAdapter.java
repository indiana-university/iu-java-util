package edu.iu.client;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
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
	static <T> IuJsonAdapter<T> of(Function<JsonValue, T> toJava, Function<T, JsonValue> toJson) {
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
	 * recursive conversion of items via {@link #of(Object)}</li>
	 * <li>Converts between {@link Map Map&lt;String, ?&gt;} and {@link JsonObject},
	 * with recursive conversion of properties via {@link #of(Object)}</li>
	 * <li>Irreversible conversion to {@link JsonValue} for the types listed in
	 * {@link #of(Class)}; must use {@link #of(Class)} for two-way conversion.</li>
	 * </li>
	 * </ul>
	 * 
	 * <p>
	 * Equivalent to {@link #of(Class) of(Object.class)}
	 * </p>
	 * 
	 * @param <T>   target type, may be coerced to {@link Object} or one of the base
	 *              types outlined above, unchecked
	 * @param value Java value
	 * @return {@link JsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> basic() {
		return JsonAdapters.adapt(Object.class, null);
	}

	/**
	 * Provides a JSON type adapter for a Java value.
	 * 
	 * @param <T>   target type
	 * @param value value
	 * @return {@link JsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> of(T value) {
		if (value == null)
			return basic();
		else
			return JsonAdapters.adapt(value.getClass(), null);
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
	 * <li>byte[], via {@link IuText#base64Url(String)} and
	 * {@link IuText#base64Url(byte[])}</li>
	 * <li>{@link BigInteger}, via unsigned big-endian byte[]</li>
	 * <li>{@link ByteBuffer}, via byte[]</li>
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
	 * <li>{@link Properties}, enforces values as {@link String}</li>
	 * <li>{@link TreeMap}</li>
	 * </ul>
	 * </li>
	 * </ul>
	 * 
	 * @param <T>  Java type
	 * @param type Java type
	 * @return {@link JsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	static <T> IuJsonAdapter<T> of(Type type) {
		return JsonAdapters.adapt(type, null);
	}

	/**
	 * Provides a standard JSON type adapter for a Java value.
	 * 
	 * @param <T>  Java type
	 * @param type Java type
	 * @return {@link JsonAdapter}
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
	 * @param <T>          Java type
	 * @param <V>          Value type
	 * @param type         Java type
	 * @param valueAdapter Value type adapter for {@link JsonStructure} conversion,
	 *                     may be null if the Java type doesn't declare parameters,
	 *                     or to use a standard value adapter
	 * @return {@link JsonAdapter}
	 * @see #of(Type)
	 */
	@SuppressWarnings("unchecked")
	static <T, V> IuJsonAdapter<T> of(Class<T> type, IuJsonAdapter<V> valueAdapter) {
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