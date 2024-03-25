package iu.client;

import edu.iu.IuText;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for byte[]
 */
class BinaryJsonAdapter implements IuJsonAdapter<byte[]> {

	/**
	 * Singleton instance.
	 */
	static final BinaryJsonAdapter INSTANCE = new BinaryJsonAdapter();

	private BinaryJsonAdapter() {
	}

	@Override
	public byte[] fromJson(JsonValue value) {
		final var text = TextJsonAdapter.INSTANCE.fromJson(value);
		if (text == null)
			return null;
		else
			return IuText.base64Url(text);
	}

	@Override
	public JsonValue toJson(byte[] data) {
		final var text = IuText.base64Url(data);
		if (text.isEmpty())
			return JsonValue.NULL;
		else
			return TextJsonAdapter.INSTANCE.toJson(text);
	}

}
