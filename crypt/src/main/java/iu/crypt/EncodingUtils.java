package iu.crypt;

import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;

import edu.iu.IuException;

/**
 * Provides basic internal binary encoding behavior for JSON web crypto
 * algorithms.
 */
class EncodingUtils {

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
			public byte[] next() {
				if (!hasNext())
					throw new NoSuchElementException();

				final var next = data.substring(start, end);
				start = end + 1;
				return base64Url(next);
			}

			@Override
			public boolean hasNext() {
				if (end < start) {
					end = data.indexOf('.', start);
					if (end == -1)
						end = data.length();
				}
				return start < data.length();
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
	 * Encodes binary data as Base-64 with URL encoding scheme and padding chars
	 * stripped.
	 * 
	 * @param data binary data
	 * @return encoded {@link String}
	 */
	static String base64Url(byte[] data) {
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

}
