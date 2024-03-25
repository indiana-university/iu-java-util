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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

import edu.iu.IuException;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Provides standard {@link IuJsonAdapter} instances.
 */
public final class JsonAdapters {

	private static final Set<Class<?>> BASIC_TYPES = Set.of(Object.class, Boolean.class, BigDecimal.class,
			Number.class);
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

		if (BASIC_TYPES.contains(erased))
			return BasicJsonAdapter.INSTANCE;

		if (erased == boolean.class)
			return PrimitiveJsonAdapter.of(erased, BasicJsonAdapter.INSTANCE);

		if (erased == Byte.class)
			return ByteJsonAdapter.INSTANCE;
		if (erased == byte.class)
			return PrimitiveJsonAdapter.of(erased, ByteJsonAdapter.INSTANCE);
		if (erased == Double.class)
			return DoubleJsonAdapter.INSTANCE;
		if (erased == double.class)
			return PrimitiveJsonAdapter.of(erased, DoubleJsonAdapter.INSTANCE);
		if (erased == Float.class)
			return FloatJsonAdapter.INSTANCE;
		if (erased == float.class)
			return PrimitiveJsonAdapter.of(erased, FloatJsonAdapter.INSTANCE);
		if (erased == Long.class)
			return LongJsonAdapter.INSTANCE;
		if (erased == long.class)
			return PrimitiveJsonAdapter.of(erased, LongJsonAdapter.INSTANCE);
		if (erased == Integer.class)
			return IntJsonAdapter.INSTANCE;
		if (erased == int.class)
			return PrimitiveJsonAdapter.of(erased, IntJsonAdapter.INSTANCE);
		if (erased == Short.class)
			return ShortJsonAdapter.INSTANCE;
		if (erased == short.class)
			return PrimitiveJsonAdapter.of(erased, ShortJsonAdapter.INSTANCE);

		if (erased == CharSequence.class //
				|| erased == String.class)
			return TextJsonAdapter.INSTANCE;

		if (erased == byte[].class)
			return BinaryJsonAdapter.INSTANCE;
		if (erased == BigInteger.class)
			return BigIntegerJsonAdapter.INSTANCE;
		if (erased == ByteBuffer.class)
			return ByteBufferJsonAdapter.INSTANCE;

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
			return new ArrayAdapter(IuJsonAdapter.of(item), factory);
		}

		if (erased == List.class //
				|| erased == ArrayList.class)
			if (valueAdapter != null)
				return new ListAdapter(valueAdapter);
			else if (type instanceof ParameterizedType)
				return new ListAdapter(IuJsonAdapter.of(item(type)));
			else
				return ListAdapter.INSTANCE;

		if (erased == Iterable.class //
				|| erased == Collection.class //
				|| erased == Queue.class //
				|| erased == Deque.class //
				|| erased == ArrayDeque.class)
			if (valueAdapter != null)
				return new DequeAdapter(valueAdapter);
			else if (type instanceof ParameterizedType)
				return new DequeAdapter(IuJsonAdapter.of(item(type)));
			else
				return DequeAdapter.INSTANCE;

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
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
		else if (type instanceof WildcardType)
			return erase(((WildcardType) type).getUpperBounds()[0]);
		else
			throw new UnsupportedOperationException();
	}

	private static Class<?> item(Type type) {
		if (type == JsonObject.class || type == JsonArray.class)
			return JsonValue.class;
		else if (type instanceof Class) {
			final var c = (Class<?>) type;
			if (c.isArray())
				return c.getComponentType();
			else
				return Object.class;
		} else if (type instanceof GenericArrayType)
			return erase(((GenericArrayType) type).getGenericComponentType());
		else if (type instanceof ParameterizedType) {
			// assumes erase() was invoked and returned a supported type first
			final var p = (ParameterizedType) type;
			final var raw = erase(p);
			if (Map.class.isAssignableFrom(raw))
				return erase(p.getActualTypeArguments()[1]);
			else
				return erase(p.getActualTypeArguments()[0]);
		} else
			return Object.class;
	}

	private JsonAdapters() {
	}
}
