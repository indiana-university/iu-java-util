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
	static Iterator<String> compact(final String data) {
		return new Iterator<String>() {
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
			public String next() {
				if (!hasNext())
					throw new NoSuchElementException();

				final var next = data.substring(start, end);
				start = end + 1;
				return next;
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

	/**
	 * Copies an integer into a byte array as a 32-bit big-endian.
	 * 
	 * @param value integer to encode
	 * @param buf   buffer
	 * @param pos   start position
	 */
	static void bigEndian(int value, byte[] buf, int pos) {
		buf[pos] = (byte) ((value >>> 24) & 0xff);
		buf[pos + 1] = (byte) ((value >>> 16) & 0xff);
		buf[pos + 2] = (byte) ((value >>> 8) & 0xff);
		buf[pos + 3] = (byte) value;
	}

	/**
	 * Copies an integer into a byte array as a 32-bit big-endian.
	 * 
	 * @param value integer to encode
	 * @param buf   buffer
	 * @param pos   start position
	 */
	static void bigEndian(long value, byte[] buf, int pos) {
		buf[pos] = (byte) ((value >>> 56) & 0xff);
		buf[pos + 1] = (byte) ((value >>> 48) & 0xff);
		buf[pos + 2] = (byte) ((value >>> 40) & 0xff);
		buf[pos + 3] = (byte) ((value >>> 32) & 0xff);
		buf[pos + 4] = (byte) ((value >>> 24) & 0xff);
		buf[pos + 5] = (byte) ((value >>> 16) & 0xff);
		buf[pos + 6] = (byte) ((value >>> 8) & 0xff);
		buf[pos + 7] = (byte) value;
	}

	/**
	 * Encodes ASCII text in NIST.800-56A Concatenated Key Derivation Format (KDF).
	 * 
	 * @param text ASCII text
	 * @param buf  buffer
	 * @param pos  start position
	 */
	static void concatKdfFragment(String text, byte[] buf, int pos) {
		final var data = IuException.unchecked(() -> text.getBytes("US-ASCII"));
		final var datalen = data.length;
		bigEndian(datalen, buf, pos);
		System.arraycopy(data, 0, buf, pos + 4, datalen);
	}

	/**
	 * Performs one round of the <a href=
	 * "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-56Ar3.pdf">NIST.800-56A
	 * Section 5.8.1 Concatenated Key Derivation Format (KDF)</a> using SHA-256 as
	 * the hash function.
	 * 
	 * @param round   round number
	 * @param z       key derivation output
	 * @param algid   algorithm ID
	 * @param uinfo   party UInfo value
	 * @param vinfo   party VInfo value
	 * @param datalen data length
	 * @return Concat KDF data
	 */
	static byte[] concatKdf(int round, byte[] z, String algid, String uinfo, String vinfo, int datalen) {
		var buf = new byte[20 + z.length + algid.length() + uinfo.length() + vinfo.length()];
		var pos = 0;

		EncodingUtils.bigEndian(round, buf, pos);
		pos += 4;

		System.arraycopy(z, 0, buf, pos, z.length);
		pos += z.length;

		EncodingUtils.concatKdfFragment(algid, buf, pos);
		pos += 4 + algid.length();

		EncodingUtils.concatKdfFragment(uinfo, buf, pos);
		pos += 4 + uinfo.length();

		EncodingUtils.concatKdfFragment(vinfo, buf, pos);
		pos += 4 + vinfo.length();

		EncodingUtils.bigEndian(datalen, buf, pos);
		return buf;
	}

	private EncodingUtils() {
	}
}
