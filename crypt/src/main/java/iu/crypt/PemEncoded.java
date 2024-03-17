package iu.crypt;

import java.io.InputStream;
import java.util.Base64;
import java.util.Iterator;

import edu.iu.IuException;
import edu.iu.IuStream;

/**
 * Reads PEM-encoded key and/or certificate data.
 */
final class PemEncoded {

	/**
	 * Enumerates encoded key type.
	 */
	enum KeyType {
		/**
		 * Private key.
		 */
		PRIVATE_KEY,

		/**
		 * Public key.
		 */
		PUBLIC_KEY,

		/**
		 * X509 certificate.
		 */
		CERTIFICATE;
	}

	/**
	 * Reads PEM-encoded key and/or certificate data.
	 * 
	 * @param in input stream of PEM-encoded key and/or certificate data, multiple
	 *           entries may be concatenated
	 * @return Parsed PEM-encoded data
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc4945#section-6.1">RFC-4945
	 *      Section 6.1</a>
	 */
	static Iterator<PemEncoded> parse(InputStream in) {
		return IuException.unchecked(() -> parse(EncodingUtils.utf8(IuStream.read(in))));
	}

	/**
	 * Parses PEM-encoded key and/or certificate data.
	 * 
	 * @param pemEncoded PEM-encoded key and/or certificate data, may be
	 *                   concatenated
	 * @return Parsed PEM-encoded data
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc4945#section-6.1">RFC-4945
	 *      Section 6.1</a>
	 */
	static Iterator<PemEncoded> parse(String pemEncoded) {
		final var length = pemEncoded.length();
		return new Iterator<PemEncoded>() {
			private int start = 0;
			private int end = -1;

			@Override
			public boolean hasNext() {
				if (end < start) {
					// * 11 chars: "-----BEGIN "
					// * 10-11 chars: key type
					// * 5 chars: "-----"
					// => 27 chars
					if (start + 27 > length)
						return false;

					if (!"-----BEGIN ".equals(pemEncoded.substring(start, start + 11))) {
						if (start > 0)
							return false;

						end = pemEncoded.length();
						return true;
					}

					final var startOfKeyType = start += 11;
					final var endOfKeyType = pemEncoded.indexOf("-----", startOfKeyType);
					switch (pemEncoded.substring(start, endOfKeyType)) {
					case "PUBLIC KEY":
						start += 15;
						break;

					case "PRIVATE KEY":
					case "CERTIFICATE":
						start += 16;
						break;

					default:
						return false;
					}

					int endOfKey = pemEncoded
							.indexOf("-----END " + pemEncoded.substring(startOfKeyType, endOfKeyType) + "-----", start);
					if (endOfKey == -1)
						return false;

					end = endOfKey;
				}

				return end > start;
			}

			@Override
			public PemEncoded next() {
				final var sb = new StringBuilder(pemEncoded.substring(start, end));
				for (var i = 0; i < sb.length(); i++)
					if (Character.isWhitespace(sb.charAt(i)))
						sb.deleteCharAt(i--);

				final KeyType keyType;
				if (start < 26)
					keyType = KeyType.CERTIFICATE;
				else
					keyType = KeyType.valueOf(pemEncoded
							.substring(pemEncoded.lastIndexOf("-----BEGIN ", start) + 11, start - 5).replace(' ', '_'));

				final var nextStart = pemEncoded.indexOf("-----BEGIN ", end);
				if (nextStart == -1)
					start = length + 1;
				else
					start = nextStart;

				return new PemEncoded(keyType, Base64.getDecoder().decode(sb.toString()));
			}
		};
	}

	/**
	 * Key type.
	 */
	final KeyType keyType;

	/**
	 * Encoded key data.
	 */
	final byte[] encoded;

	private PemEncoded(KeyType keyType, byte[] encoded) {
		this.keyType = keyType;
		this.encoded = encoded;
	}

}
