package iu.client;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Byte}
 */
class ByteJsonAdapter implements IuJsonAdapter<Byte> {

	/**
	 * Singleton instance.
	 */
	static ByteJsonAdapter INSTANCE = new ByteJsonAdapter();

	@Override
	public Byte fromJson(JsonValue value) {
		if (value instanceof JsonNumber)
			return (byte) ((JsonNumber) value).intValue();
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	@Override
	public JsonValue toJson(Byte value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJson.PROVIDER.createValue(value);
	}

}
