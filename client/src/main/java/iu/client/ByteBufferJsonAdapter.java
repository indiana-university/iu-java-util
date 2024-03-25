package iu.client;

import java.nio.ByteBuffer;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link ByteBuffer}
 */
class ByteBufferJsonAdapter implements IuJsonAdapter<ByteBuffer> {

	/**
	 * Singleton instance.
	 */
	static final ByteBufferJsonAdapter INSTANCE = new ByteBufferJsonAdapter();

	private ByteBufferJsonAdapter() {
	}

	@Override
	public ByteBuffer fromJson(JsonValue value) {
		final var data = BinaryJsonAdapter.INSTANCE.fromJson(value);
		if (data == null)
			return null;
		else
			return ByteBuffer.wrap(data);
	}

	@Override
	public JsonValue toJson(ByteBuffer value) {
		if (value == null || !value.hasRemaining())
			return JsonValue.NULL;

		final byte[] data;
		if (value.hasArray() //
				&& value.arrayOffset() == 0 //
				&& value.limit() == value.capacity()) {
			value.position(value.limit());
			data = value.array();
		} else {
			data = new byte[value.remaining()];
			value.get(data);
		}

		return BinaryJsonAdapter.INSTANCE.toJson(data);
	}

}
