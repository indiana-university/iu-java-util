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

import java.security.MessageDigest;

import edu.iu.IuException;

/**
 * Provides message digest and authentication utilities.
 */
public class DigestUtils {

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

	private DigestUtils() {
	}
}
