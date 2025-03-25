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
package edu.iu;

import java.security.MessageDigest;

/**
 * Provides simplified access to common {@link MessageDigest} operations.
 */
public final class IuDigest {

	/**
	 * Calculates a MD5 digest.
	 * 
	 * @param data data to digest
	 * @return MD5 digest
	 * @deprecated MD5 is not considered secure and <em>should</em> be replaced with
	 *             a stronger algorithm where used
	 */
	@Deprecated
	public static byte[] md5(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("MD5").digest(data));
	}

	/**
	 * Calculates a SHA-1 digest.
	 * 
	 * @param data data to digest
	 * @return SHA-1 digest
	 * @deprecated SHA-1 is not considered secure and <em>should</em> be replaced
	 *             with a stronger algorithm where used
	 */
	@Deprecated
	public static byte[] sha1(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("SHA-1").digest(data));
	}

	/**
	 * Calculates a SHA-256 digest.
	 * 
	 * @param data data to digest
	 * @return SHA-256 digest
	 */
	public static byte[] sha256(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("SHA-256").digest(data));
	}

	/**
	 * Calculates a SHA-384 digest.
	 * 
	 * @param data data to digest
	 * @return SHA-384 digest
	 */
	public static byte[] sha384(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("SHA-384").digest(data));
	}

	/**
	 * Calculates a SHA-512 digest.
	 * 
	 * @param data data to digest
	 * @return SHA-512 digest
	 */
	public static byte[] sha512(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("SHA-512").digest(data));
	}

	private IuDigest() {
	}
	
}
