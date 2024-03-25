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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

import edu.iu.IuException;

/**
 * Provides low-level cryptography and binary encoding functions.
 */
public class IuCrypt {

	private static final ThreadLocal<MessageDigest> SHA1 = new ThreadLocal<MessageDigest>() {
		@Override
		protected MessageDigest initialValue() {
			return IuException.unchecked(() -> MessageDigest.getInstance("SHA-1"));
		}
	};

	private static final ThreadLocal<MessageDigest> SHA256 = new ThreadLocal<MessageDigest>() {
		@Override
		protected MessageDigest initialValue() {
			return IuException.unchecked(() -> MessageDigest.getInstance("SHA-256"));
		}
	};

	private static final byte[] EMPTY_SHA1 = SHA1.get().digest(new byte[0]);
	private static final byte[] EMPTY_SHA256 = SHA256.get().digest(new byte[0]);

	/**
	 * Gets a SHA-1 digest.
	 * 
	 * @param data character data
	 * @return SHA-1 digest
	 */
	public static byte[] sha1(byte[] data) {
		if (data == null || data.length == 0)
			return EMPTY_SHA1;
		return SHA1.get().digest(data);
	}

	/**
	 * Gets a SHA-256 digest for character data.
	 * <p>
	 * The string passed into this method is first converted to UTF-8 binary format,
	 * then digested.
	 * </p>
	 * 
	 * @param data character data
	 * @return SHA-256 digest
	 */
	public static byte[] sha256(byte[] data) {
		if (data == null || data.length == 0)
			return EMPTY_SHA256;
		return SHA256.get().digest(data);
	}

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

	private IuCrypt() {
	}

}
