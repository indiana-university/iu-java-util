/*
 * Copyright © 2025 Indiana University
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
import java.util.Arrays;

/**
 * Encodes {@link BigInteger} values from JCE crypto objects in the formats
 * specified by RFC-7518 JWA.
 */
class UnsignedBigInteger {

	/**
	 * Converts an unsigned big-endian {@link BigInteger} to binary, omitting the
	 * sign bit if necessary.
	 * 
	 * @param bigInteger unsigned big-endian {@link BigInteger}
	 * @return binary
	 */
	static byte[] bigInt(BigInteger bigInteger) {
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
	static BigInteger bigInt(byte[] binary) {
		return new BigInteger(1, binary);
	}

	private UnsignedBigInteger() {
	}
}