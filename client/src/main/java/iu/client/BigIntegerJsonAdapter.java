package iu.client;

import java.math.BigInteger;

import edu.iu.IuCrypt;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link BigInteger}
 */
class BigIntegerJsonAdapter implements IuJsonAdapter<BigInteger> {

	/**
	 * Singleton instance.
	 */
	static final BigIntegerJsonAdapter INSTANCE = new BigIntegerJsonAdapter();

	private BigIntegerJsonAdapter() {
	}

	@Override
	public BigInteger fromJson(JsonValue value) {
		final var data = BinaryJsonAdapter.INSTANCE.fromJson(value);
		if (data == null)
			return null;
		else
			return IuCrypt.bigInt(data);
	}

	@Override
	public JsonValue toJson(BigInteger value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return BinaryJsonAdapter.INSTANCE.toJson(IuCrypt.bigInt(value));
	}

}
