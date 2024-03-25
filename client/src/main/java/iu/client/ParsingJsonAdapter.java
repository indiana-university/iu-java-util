package iu.client;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for types that provide a mechanism for
 * parsing the value returned by {@link Object#toString()}
 * 
 * @param <T> Java type
 */
class ParsingJsonAdapter<T> implements IuJsonAdapter<T> {

	private static final Map<Class<?>, ParsingJsonAdapter<?>> INSTANCES = new WeakHashMap<>();

	/**
	 * Gets a singleton instance by target type.
	 * 
	 * @param <T>    target type
	 * @param type   target type
	 * @param parser parsing function
	 * @return {@link ParsingJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	static <T> ParsingJsonAdapter<T> of(Class<T> type, Function<String, T> parser) {
		var instance = INSTANCES.get(type);
		if (instance == null) {
			instance = new ParsingJsonAdapter<T>(parser);
			synchronized (INSTANCES) {
				INSTANCES.put(type, instance);
			}
		}
		return (ParsingJsonAdapter<T>) instance;
	}

	private final Function<String, T> parser;

	private ParsingJsonAdapter(Function<String, T> parser) {
		this.parser = parser;
	}

	@Override
	public T fromJson(JsonValue value) {
		final var text = TextJsonAdapter.INSTANCE.fromJson(value);
		if (text == null)
			return null;
		else
			return parser.apply(text);
	}

	@Override
	public JsonValue toJson(T value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return TextJsonAdapter.INSTANCE.toJson(value.toString());
	}

}
