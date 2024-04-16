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
package edu.iu;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Low-level text processing utilities.
 */
public final class IuText {

	/**
	 * Encodes binary data as basic Base64.
	 * 
	 * @param data binary data
	 * @return encoded {@link String}
	 */
	public static String base64(byte[] data) {
		if (data == null)
			return null;
		else
			return Base64.getEncoder().encodeToString(data);
	}

	/**
	 * Decodes binary data from basic Base64.
	 * 
	 * @param data encoded {@link String}
	 * @return binary data
	 */
	public static byte[] base64(String data) {
		if (data == null)
			return null;
		else
			return Base64.getDecoder().decode(data);
	}

	/**
	 * Converts string data to UTF-8 binary.
	 * 
	 * @param data string data
	 * @return UTF-8 binary
	 */
	public static byte[] utf8(String data) {
		if (data == null)
			return null;
		else
			return data.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Gets a string from UTF-8 encoding data.
	 * 
	 * @param data UTF-8 encoded data
	 * @return string data
	 */
	public static String utf8(byte[] data) {
		if (data == null)
			return null;
		else
			return new String(data, StandardCharsets.UTF_8);
	}

	/**
	 * Converts string data to ASCII binary.
	 * 
	 * @param data string data
	 * @return ASCII binary
	 */
	public static byte[] ascii(String data) {
		if (data == null)
			return null;
		else
			return data.getBytes(StandardCharsets.US_ASCII);
	}

	/**
	 * Gets a string from ASCII encoding data.
	 * 
	 * @param data ASCII encoded data
	 * @return string data
	 */
	public static String ascii(byte[] data) {
		if (data == null)
			return null;
		else
			return new String(data, StandardCharsets.US_ASCII);
	}

	private IuText() {
	}

}
