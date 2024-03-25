package iu.client;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Long}
 */
class LongJsonAdapter implements IuJsonAdapter<Long> {

	/**
	 * Singleton instance.
	 */
	static LongJsonAdapter INSTANCE = new LongJsonAdapter();

	@Override
	public Long fromJson(JsonValue value) {
		if (value instanceof JsonNumber)
			return (long) ((JsonNumber) value).longValue();
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	@Override
	public JsonValue toJson(Long value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJson.PROVIDER.createValue(value);
	}

}
