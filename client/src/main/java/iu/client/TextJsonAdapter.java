package iu.client;

import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link CharSequence}
 */
class TextJsonAdapter implements IuJsonAdapter<CharSequence> {

	/**
	 * Singleton instance.
	 */
	static TextJsonAdapter INSTANCE = new TextJsonAdapter();

	@Override
	public String fromJson(JsonValue value) {
		if (value instanceof JsonString)
			return ((JsonString) value).getString();
		else if ((value instanceof JsonNumber) //
				|| JsonValue.TRUE.equals(value) //
				|| JsonValue.FALSE.equals(value))
			return value.toString();
		else if (value instanceof JsonArray)
			return String.join(",", IuIterable.map(((JsonArray) value), this::fromJson));
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	@Override
	public JsonValue toJson(CharSequence value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJson.PROVIDER.createValue(value.toString());
	}

}
