package iu.client;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Decorates another {@link IuJsonAdapter} to prevent null value conversion
 * 
 * @param <T> target type
 */
class PrimitiveJsonAdapter<T> implements IuJsonAdapter<T> {

	private static final Map<Class<?>, PrimitiveJsonAdapter<?>> INSTANCES = new WeakHashMap<>();

	/**
	 * Gets a singleton instance by target type.
	 * 
	 * @param <T>     target type
	 * @param type    target type
	 * @param autobox non-primitive type for autoboxing
	 * @return {@link PrimitiveJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	static <T> PrimitiveJsonAdapter<T> of(Class<T> type, IuJsonAdapter<?> autobox) {
		var instance = INSTANCES.get(type);
		if (instance == null) {
			instance = new PrimitiveJsonAdapter<T>(autobox);
			synchronized (INSTANCES) {
				INSTANCES.put(type, instance);
			}
		}
		return (PrimitiveJsonAdapter<T>) instance;
	}

	private final IuJsonAdapter<Object> autobox;

	@SuppressWarnings("unchecked")
	private PrimitiveJsonAdapter(IuJsonAdapter<?> autobox) {
		this.autobox = (IuJsonAdapter<Object>) autobox;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T fromJson(JsonValue value) {
		return (T) Objects.requireNonNull(autobox.fromJson(value));
	}

	@Override
	public JsonValue toJson(T value) {
		final var basic = autobox.toJson(value);
		if (JsonValue.NULL.equals(basic))
			throw new NullPointerException();
		else
			return basic;
	}

}
