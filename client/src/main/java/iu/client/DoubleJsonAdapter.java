package iu.client;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Double}
 */
class DoubleJsonAdapter implements IuJsonAdapter<Double> {

	/**
	 * Singleton instance.
	 */
	static DoubleJsonAdapter INSTANCE = new DoubleJsonAdapter();

	@Override
	public Double fromJson(JsonValue value) {
		if (value instanceof JsonNumber)
			return (double) ((JsonNumber) value).doubleValue();
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	@Override
	public JsonValue toJson(Double value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJson.PROVIDER.createValue(value);
	}

}
