package iu.client;

import java.util.Date;
import java.util.Optional;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Date}
 * 
 * @param <T> value type
 */
class OptionalJsonAdapter<T> implements IuJsonAdapter<Optional<T>> {

	/**
	 * Singleton instance.
	 */
	static final OptionalJsonAdapter<?> INSTANCE = new OptionalJsonAdapter<>(null);

	private final IuJsonAdapter<T> adapter;

	/**
	 * Constructor.
	 * 
	 * @param adapter value adapter
	 */
	OptionalJsonAdapter(IuJsonAdapter<T> adapter) {
		this.adapter = adapter;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Optional<T> fromJson(JsonValue value) {
		final T converted;
		if (adapter == null)
			converted = (T) BasicJsonAdapter.INSTANCE.fromJson(value);
		else
			converted = adapter.fromJson(value);
		return Optional.ofNullable(converted);
	}

	@Override
	public JsonValue toJson(Optional<T> value) {
		if (value == null)
			return JsonValue.NULL;

		final var unwrapped = value.orElse(null);
		if (adapter == null)
			return IuJsonAdapter.of(unwrapped).toJson(unwrapped);
		else
			return adapter.toJson(unwrapped);
	}

}
