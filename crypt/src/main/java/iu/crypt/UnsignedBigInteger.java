package iu.crypt;

import java.math.BigInteger;
import java.util.Arrays;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Encodes {@link BigInteger} values from JCE crypto objects in the formats
 * specified by RFC-7518 JWA.
 */
class UnsignedBigInteger implements IuJsonAdapter<BigInteger> {

	/**
	 * Singleton.
	 */
	static final UnsignedBigInteger JSON = new UnsignedBigInteger();

	/**
	 * Converts an unsigned big-endian {@link BigInteger} to binary, omitting the
	 * sign bit if necessary.
	 * 
	 * @param bigInteger unsigned big-endian {@link BigInteger}
	 * @return binary
	 */
	public static byte[] bigInt(BigInteger bigInteger) {
		final var bytes = bigInteger.toByteArray();

		final var bitlen = // ceil(bigInteger.bitLength()/8)
				(bigInteger.bitLength() + 7) / 8;
		final var bytelen = bytes.length;
		if (bytelen > bitlen)
			return Arrays.copyOfRange(bytes, bytelen - bitlen, bytelen);
		else
			return bytes;
	}

	/**
	 * Converts binary to unsigned big-endian {@link BigInteger}.
	 * 
	 * @param binary binary
	 * @return unsigned big-endian {@link BigInteger}
	 */
	public static BigInteger bigInt(byte[] binary) {
		return new BigInteger(1, binary);
	}

	@Override
	public BigInteger fromJson(JsonValue jsonValue) {
		return bigInt(EncodingUtils.base64Url(IuJsonAdapter.<String>basic().fromJson(jsonValue)));
	}

	@Override
	public JsonValue toJson(BigInteger javaValue) {
		return IuJsonAdapter.basic().toJson(EncodingUtils.base64Url(bigInt(javaValue)));
	}

	private UnsignedBigInteger() {
	}
}