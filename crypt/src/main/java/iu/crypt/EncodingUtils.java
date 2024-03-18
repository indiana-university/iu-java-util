package iu.crypt;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;

import edu.iu.IuException;
import edu.iu.client.IuJson;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Provides basic internal binary encoding behavior for JSON web crypto
 * algorithms.
 */
class EncodingUtils {

	/**
	 * Parses a JSON object from compact encoded form
	 * 
	 * @param data compact encoded JSON
	 * @return {@link JsonObject}
	 */
	static JsonObject compactJson(String data) {
		return IuJson.parse(utf8(base64Url(data))).asJsonObject();
	}

	/**
	 * Iterates over segments in a JSON compact serialized structure.
	 * 
	 * @param data compact serialize data
	 * @return {@link Iterator} over data segments
	 */
	static Iterator<byte[]> compact(final String data) {
		return new Iterator<byte[]>() {
			private int start;
			private int end = -1;

			@Override
			public boolean hasNext() {
				if (end < start) {
					end = data.indexOf('.', start);
					if (end == -1)
						end = data.length();
				}
				return start < data.length();
			}

			@Override
			public byte[] next() {
				if (!hasNext())
					throw new NoSuchElementException();

				final var next = data.substring(start, end);
				start = end + 1;
				return base64Url(next);
			}
		};
	}

	/**
	 * Removes training padding characters from Base-64 encoded data.
	 * 
	 * @param b64 Base-64 encoded
	 * @return encoded data with padding chars removed
	 */
	static String unpad(String b64) {
		if (b64 == null || b64.isEmpty())
			return b64;
		var i = b64.length() - 1;
		while (i > 0 && b64.charAt(i) == '=')
			i--;
		return b64.substring(0, i + 1);
	}

	/**
	 * Restores padding characters to Base-64 encoded data.
	 * 
	 * @param b64 Base-64 encoded
	 * @return encoded data with padding chars restored
	 */
	static String pad(String b64) {
		if (b64 == null || b64.isEmpty())
			return b64;
		switch (b64.length() % 4) {
		case 1:
			return b64 + "===";
		case 2:
			return b64 + "==";
		case 3:
			return b64 + "=";
		default:
			return b64;
		}
	}

	/**
	 * Encodes binary data as Base-64, for handling certificates.
	 * 
	 * @param data binary data
	 * @return encoded {@link String}
	 */
	static String base64(byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}

	/**
	 * Decodes binary data from Base-64, for handling certificates.
	 * 
	 * @param data encoded {@link String}
	 * @return binary data
	 */
	static byte[] base64(String data) {
		return Base64.getDecoder().decode(data);
	}

	/**
	 * Encodes binary data as Base-64 with URL encoding scheme and padding chars
	 * stripped.
	 * 
	 * @param data binary data
	 * @return encoded {@link String}
	 */
	static String base64Url(byte[] data) {
		if (data == null || data.length == 0)
			return "";
		else
			return unpad(Base64.getUrlEncoder().encodeToString(data));
	}

	/**
	 * Decodes binary data from Base-64 with URL encoding scheme and padding chars
	 * stripped.
	 * 
	 * @param data encoded {@link String}
	 * @return binary data
	 */
	static byte[] base64Url(String data) {
		if (data == null || data.isBlank())
			return null;
		else
			return Base64.getUrlDecoder().decode(pad(data));
	}

	/**
	 * Decodes UTF-8 binary data to a character stream.
	 * 
	 * @param data UTF-8 binary
	 * @return decoded {@link String}
	 */
	static String utf8(byte[] data) {
		return IuException.unchecked(() -> new String(data, "UTF-8"));
	}

	/**
	 * Encodes a character stream as UTF-8 binary.
	 * 
	 * @param data {@link String}
	 * @return UTF-8 encoded binary
	 */
	static byte[] utf8(String data) {
		return IuException.unchecked(() -> data.getBytes("UTF-8"));
	}

	/**
	 * Gets binary data from a JSON object.
	 * 
	 * @param object {@link JsonObject}
	 * @param name   name of a string property containing unpadded Base-64
	 *               URL-encoded data.
	 * @return binary data
	 */
	static byte[] getBytes(JsonObject object, String name) {
		return IuJson.text(object, name, EncodingUtils::base64Url);
	}

	/**
	 * Adds binary data to a JSON object builder.
	 * 
	 * @param objectBuilder {@link JsonObjectBuilder}
	 * @param name          property name
	 * @param binaryData    data to add as an unpadded Base-64 URL-encoded string.
	 */
	static void setBytes(JsonObjectBuilder objectBuilder, String name, byte[] binaryData) {
		IuJson.add(objectBuilder, name, () -> binaryData, a -> IuJson.toJson(EncodingUtils.base64Url(a)));
	}

	/**
	 * Gets a {@link BigInteger} value from a JSON object.
	 * 
	 * @param object
	 * @param name   name of string property containing the unpadded positive
	 *               big-endian Base-64 URL-encoded representation of the
	 *               {@link BigInteger}
	 * @return {@link BigInteger}
	 */
	static BigInteger getBigInt(JsonObject object, String name) {
		return toBigInteger(getBytes(object, name));
	}

	/**
	 * Gets a {@link BigInteger} value from a JSON object.
	 * 
	 * @param objectBuilder {@link JsonObjectBuilder}
	 * @param name          property name
	 * @param bigInteger    {@link BigInteger} to add as an unpadded positive
	 *                      big-endian Base-64 URL-encoded string.
	 */
	static void setBigInt(JsonObjectBuilder objectBuilder, String name, BigInteger bigInteger) {
		setBytes(objectBuilder, name, toByteArray(bigInteger));
	}

	/**
	 * Converts a positive big-endian {@link BigInteger} to binary, omitting the
	 * sign bit if necessary.
	 * 
	 * @param bigInteger positive big-endian {@link BigInteger}
	 * @return binary
	 */
	static byte[] toByteArray(BigInteger bigInteger) {
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
	 * Converts binary w/o sign bit to positive big-endian {@link BigInteger}.
	 * 
	 * @param binary binary
	 * @return positive big-endian {@link BigInteger}
	 */
	static BigInteger toBigInteger(byte[] binary) {
		return new BigInteger(1, binary);
	}

	private EncodingUtils() {
	}
}
