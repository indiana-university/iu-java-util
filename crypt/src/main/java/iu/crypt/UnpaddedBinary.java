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

import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Encodes {@link byte[]} values for inclusion in JWS and JWE serialized forms
 * as unpadded Base64 URL encoded strings.
 */
public class UnpaddedBinary implements IuJsonAdapter<byte[]> {
	static {
		IuObject.assertNotOpen(UnpaddedBinary.class);
	}

	/**
	 * Singleton.
	 */
	public static final UnpaddedBinary JSON = new UnpaddedBinary();

	@Override
	public byte[] fromJson(JsonValue jsonValue) {
		return jsonValue == null ? null : base64Url(IuJsonAdapter.of(String.class).fromJson(jsonValue));
	}

	@Override
	public JsonValue toJson(byte[] javaValue) {
		return IuJsonAdapter.of(String.class).toJson(base64Url(javaValue));
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
		if (data == null)
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
		if (data == null)
			return null;
		else
			return Base64.getUrlDecoder().decode(pad(data));
	}

	/**
	 * Parses a JSON object from compact encoded form
	 * 
	 * @param data compact encoded JSON
	 * @return {@link JsonObject}
	 */
	static JsonValue compactJson(String data) {
		if (data == null || data.isBlank())
			return null;
		else
			return IuJson.parse(IuText.utf8(base64Url(data)));
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

				final var next = start == end ? null : data.substring(start, end);
				start = end + 1;
				return next;
			}
		};
	}

	/**
	 * Concatenates binary data segments as '.' separated Base64 URL encoded
	 * unpadded strings.
	 * 
	 * @param segments JWS or JWE compact serialization segments
	 * @return encoded sequence
	 */
	static String compact(byte[]... segments) {
		final var sb = new StringBuilder();
		for (final var segment : segments) {
			if (sb.length() > 0)
				sb.append('.');
			if (segment != null)
				sb.append(UnpaddedBinary.base64Url(segment));
		}

		return sb.toString();
	}

	private UnpaddedBinary() {
	}

}