package iu.client;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Short}
 */
class ShortJsonAdapter implements IuJsonAdapter<Short> {

	/**
	 * Singleton instance.
	 */
	static ShortJsonAdapter INSTANCE = new ShortJsonAdapter();

	@Override
	public Short fromJson(JsonValue value) {
		if (value instanceof JsonNumber)
			return (short) ((JsonNumber) value).intValue();
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	@Override
	public JsonValue toJson(Short value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJson.PROVIDER.createValue(value);
	}

}
