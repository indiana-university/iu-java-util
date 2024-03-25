package iu.client;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Integer}
 */
class IntJsonAdapter implements IuJsonAdapter<Integer> {

	/**
	 * Singleton instance.
	 */
	static IntJsonAdapter INSTANCE = new IntJsonAdapter();

	@Override
	public Integer fromJson(JsonValue value) {
		if (value instanceof JsonNumber)
			return ((JsonNumber) value).intValue();
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	@Override
	public JsonValue toJson(Integer value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJson.PROVIDER.createValue(value);
	}

}
