package iu.client;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter#basic()}
 */
class BasicJsonAdapter implements IuJsonAdapter<Object> {

	/**
	 * Singleton instance.
	 */
	static BasicJsonAdapter INSTANCE = new BasicJsonAdapter();

	@Override
	public Object fromJson(JsonValue value) {
		if (value instanceof JsonObject) {
			final Map<String, Object> m = new LinkedHashMap<>();
			((JsonObject) value).forEach((k, v) -> m.put(k, fromJson(v)));
			return Collections.unmodifiableMap(m);
		} else if (value instanceof JsonArray)
			return ((JsonArray) value).stream().map(this::fromJson).collect(Collectors.toUnmodifiableList());
		else if (value instanceof JsonString)
			return ((JsonString) value).getString();
		else if (value instanceof JsonNumber)
			return ((JsonNumber) value).bigDecimalValue();
		else if (JsonValue.TRUE.equals(value))
			return Boolean.TRUE;
		else if (JsonValue.FALSE.equals(value))
			return Boolean.FALSE;
		else if (JsonValue.NULL.equals(value) || value == null)
			return null;
		else
			throw new IllegalArgumentException();
	}

	@Override
	public JsonValue toJson(Object value) {
		if (value == null)
			return JsonValue.NULL;
		else if (value instanceof JsonValue)
			return (JsonValue) value;
		else if (value instanceof JsonObjectBuilder)
			return ((JsonObjectBuilder) value).build();
		else if (value instanceof JsonArrayBuilder)
			return ((JsonArrayBuilder) value).build();
		else if (value instanceof Map)
			return convertMap((Map<?, ?>) value);
		else if (value.getClass().isArray())
			return convertArray(value);
		else if (value instanceof Stream)
			return convertIterator(((Stream<?>) value).iterator());
		else if (value instanceof Iterable)
			return convertIterator(((Iterable<?>) value).iterator());
		else if (value instanceof Iterator)
			return convertIterator((Iterator<?>) value);
		else if (value instanceof Enumeration)
			return convertIterator(((Enumeration<?>) value).asIterator());
		else if (value instanceof String)
			return IuJson.PROVIDER.createValue((String) value);
		else if (value instanceof Number)
			return IuJson.PROVIDER.createValue((Number) value);
		else if (Boolean.TRUE.equals(value))
			return JsonValue.TRUE;
		else if (Boolean.FALSE.equals(value))
			return JsonValue.FALSE;
		else
			throw new IllegalArgumentException();
	}

	private static JsonValue convertMap(Map<?, ?> map) {
		final var builder = IuJson.object();
		for (final var entry : map.entrySet()) {
			final var name = (String) entry.getKey();
			final var value = entry.getValue();
			final var valueAdapter = IuJsonAdapter.of(value);
			builder.add(name, valueAdapter.toJson(value));
		}
		return builder.build();
	}

	private static JsonValue convertArray(Object array) {
		final var builder = IuJson.array();
		for (int i = 0; i < Array.getLength(array); i++) {
			final var item = Array.get(array, i);
			final var itemAdapter = IuJsonAdapter.of(item);
			builder.add(itemAdapter.toJson(item));
		}
		return builder.build();
	}

	private static JsonValue convertIterator(Iterator<?> iterator) {
		final var builder = IuJson.array();
		while (iterator.hasNext()) {
			final var item = iterator.next();
			final var itemAdapter = IuJsonAdapter.of(item);
			builder.add(itemAdapter.toJson(item));
		}
		return builder.build();
	}

	private BasicJsonAdapter() {
	}
}
