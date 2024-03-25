package iu.client;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Float}
 */
class FloatJsonAdapter implements IuJsonAdapter<Float> {

	/**
	 * Singleton instance.
	 */
	static FloatJsonAdapter INSTANCE = new FloatJsonAdapter();

	@Override
	public Float fromJson(JsonValue value) {
		if (value instanceof JsonNumber)
			return (float) ((JsonNumber) value).doubleValue();
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	@Override
	public JsonValue toJson(Float value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJson.PROVIDER.createValue(value);
	}

}
