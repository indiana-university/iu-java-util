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

import java.nio.ByteBuffer;

/**
 * Provides basic internal binary encoding behavior for JSON web crypto
 * algorithms.
 */
class EncodingUtils {

	private static final byte[] b0 = new byte[0];

	/**
	 * Copies an 32-bit integer into a {@link ByteBuffer}
	 * 
	 * @param value  integer to encode
	 * @param buffer {@link ByteBuffer}
	 */
	public static void bigEndian(int value, ByteBuffer buffer) {
		buffer.put((byte) ((value >>> 24) & 0xff));
		buffer.put((byte) ((value >>> 16) & 0xff));
		buffer.put((byte) ((value >>> 8) & 0xff));
		buffer.put((byte) value);
	}

	/**
	 * Copies an 64-bit long into a {@link ByteBuffer}
	 * 
	 * @param value  integer to encode
	 * @param buffer {@link ByteBuffer}
	 */
	public static void bigEndian(long value, ByteBuffer buffer) {
		buffer.put((byte) ((value >>> 56) & 0xff));
		buffer.put((byte) ((value >>> 48) & 0xff));
		buffer.put((byte) ((value >>> 40) & 0xff));
		buffer.put((byte) ((value >>> 32) & 0xff));
		buffer.put((byte) ((value >>> 24) & 0xff));
		buffer.put((byte) ((value >>> 16) & 0xff));
		buffer.put((byte) ((value >>> 8) & 0xff));
		buffer.put((byte) value);
	}

	/**
	 * Encodes data in NIST.800-56A Concatenated Key Derivation Format (KDF).
	 * 
	 * @param data   data to encode
	 * @param buffer {@link ByteBuffer}
	 */
	static void concatKdfFragment(byte[] data, ByteBuffer buffer) {
		bigEndian(data.length, buffer);
		buffer.put(data);
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
		if (vinfo == null)
			vinfo = b0;

		final var buffer = ByteBuffer.wrap(new byte[20 + z.length + algid.length + uinfo.length + vinfo.length]);
		bigEndian(round, buffer);
		buffer.put(z);
		concatKdfFragment(algid, buffer);
		concatKdfFragment(uinfo, buffer);
		concatKdfFragment(vinfo, buffer);
		bigEndian(datalen, buffer);
		return buffer.array();
	}

	/**
	 * Reverses byte order
	 * 
	 * @param bytes big-endian data (or vice-versa)
	 * @return little-endian data (or vice-versa)
	 */
	static byte[] reverse(byte[] bytes) {
		final int l;
		if (bytes == null || (l = bytes.length) <= 1)
			return bytes;

		final var b = new byte[l];
		for (var i = 0; i < l; i++)
			b[i] = bytes[l - i - 1];
		return b;
	}

	private EncodingUtils() {
	}
}
