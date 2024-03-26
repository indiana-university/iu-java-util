/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.crypt;

import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;

import edu.iu.IuText;
import edu.iu.client.IuJson;
import jakarta.json.JsonObject;

/**
 * Provides basic internal binary encoding behavior for JSON web crypto
 * algorithms.
 */
class EncodingUtils {

	private static final byte[] b0 = new byte[0];

	/**
	 * Parses a JSON object from compact encoded form
	 * 
	 * @param data compact encoded JSON
	 * @return {@link JsonObject}
	 */
	static JsonObject compactJson(String data) {
		return IuJson.parse(IuText.utf8(base64Url(data))).asJsonObject();
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
	public static String unpad(String b64) {
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
	public static String pad(String b64) {
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
	 * Encodes binary data as Base64 with URL encoding and padding chars stripped.
	 * 
	 * <p>
	 * This method implements specific padding and null-handling semantics to
	 * support JWS and JWE serialization methods in the iu.util.crypt module.
	 * </p>
	 * 
	 * @param data binary data
	 * @return encoded {@link String}; empty string if data is null
	 */
	public static String base64Url(byte[] data) {
		if (data == null || data.length == 0)
			return null;
		else
			return unpad(Base64.getUrlEncoder().encodeToString(data));
	}

	/**
	 * Decodes binary data from Base64 with URL encoding scheme and padding chars
	 * stripped.
	 * 
	 * <p>
	 * This method implements specific padding and null-handling semantics to
	 * support JWS and JWE serialization methods in the iu.util.crypt module.
	 * </p>
	 * 
	 * @param data encoded {@link String}
	 * @return binary data; null if data is empty or null
	 */
	public static byte[] base64Url(String data) {
		if (data == null || data.isBlank())
			return null;
		else
			return Base64.getUrlDecoder().decode(pad(data));
	}

	/**
	 * Copies an integer into a byte array as 32-bit big-endian.
	 * 
	 * @param value integer to encode
	 * @param buf   buffer
	 * @param pos   start position
	 */
	public static void bigEndian(int value, byte[] buf, int pos) {
		buf[pos] = (byte) ((value >>> 24) & 0xff);
		buf[pos + 1] = (byte) ((value >>> 16) & 0xff);
		buf[pos + 2] = (byte) ((value >>> 8) & 0xff);
		buf[pos + 3] = (byte) value;
	}

	/**
	 * Copies an integer into a byte array as 64-bit big-endian.
	 * 
	 * @param value integer to encode
	 * @param buf   buffer
	 * @param pos   start position
	 */
	public static void bigEndian(long value, byte[] buf, int pos) {
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
	 * Encodes data in NIST.800-56A Concatenated Key Derivation Format (KDF).
	 * 
	 * @param data data to encode
	 * @param buf  buffer
	 * @param pos  start position
	 */
	static void concatKdfFragment(byte[] data, byte[] buf, int pos) {
		final var datalen = data.length;
		bigEndian(datalen, buf, pos);
		System.arraycopy(data, 0, buf, pos + 4, datalen);
	}

	/**
	 * Gets the hash input data for one round of the <a href=
	 * "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-56Ar3.pdf">NIST.800-56A
	 * Section 5.8.1 Concatenated Key Derivation Format (KDF)</a>
	 * 
	 * @param round   round number
	 * @param z       key derivation output
	 * @param algid   algorithm ID
	 * @param uinfo   party UInfo value
	 * @param vinfo   party VInfo value
	 * @param datalen data length
	 * @return Concat KDF hash input
	 */
	static byte[] concatKdf(int round, byte[] z, byte[] algid, byte[] uinfo, byte[] vinfo, int datalen) {
		if (uinfo == null)
			uinfo = b0;
		if (uinfo == null)
			vinfo = b0;

		var buf = new byte[20 + z.length + algid.length + uinfo.length + vinfo.length];
		var pos = 0;

		bigEndian(round, buf, pos);
		pos += 4;

		System.arraycopy(z, 0, buf, pos, z.length);
		pos += z.length;

		concatKdfFragment(algid, buf, pos);
		pos += 4 + algid.length;

		concatKdfFragment(uinfo, buf, pos);
		pos += 4 + uinfo.length;

		concatKdfFragment(vinfo, buf, pos);
		pos += 4 + vinfo.length;

		EncodingUtils.bigEndian(datalen, buf, pos);
		return buf;
	}

	private EncodingUtils() {
	}
}
