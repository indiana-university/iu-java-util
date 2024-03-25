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
package edu.iu.crypt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import edu.iu.IuException;
import edu.iu.crypt.WebCryptoHeader.Param;

/**
 * Unifies algorithm support and maps from JCE encryption to JSON Web Encryption
 * (JWE).
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC 7515</a>
 */
public interface WebSignature {

	/**
	 * Provides parameters for creating new {@link WebSignature} instances.
	 * 
	 * @param <B> builder type
	 */
	interface Builder<B extends Builder<B>> extends WebCryptoHeader.Builder<B> {
		/**
		 * Defines standard protected header parameters.
		 * 
		 * @param params protected header parameters
		 * @return this
		 */
		B protect(Param... params);

		/**
		 * Signs text.
		 * 
		 * @param text message to sign
		 * @return signed message
		 */
		default WebSignature sign(String text) {
			return sign(IuException.unchecked(() -> text.getBytes("UTF-8")));
		}

		/**
		 * Signs data.
		 * 
		 * @param data data to sign
		 * @return signed data
		 */
		default WebSignature sign(byte[] data) {
			return sign(new ByteArrayInputStream(data));
		}

		/**
		 * Signs data.
		 * 
		 * @param in stream of data to sign
		 * @return signed data
		 */
		WebSignature sign(InputStream in);
	}

	/**
	 * Gets the signature header.
	 * 
	 * @return {@link WebCryptoHeader}
	 */
	WebCryptoHeader getHeader();

	/**
	 * Gets encrypted data.
	 * 
	 * @return encrypted data
	 */
	byte[] getPayload();

	/**
	 * Gets the signature data.
	 * 
	 * @return signature data
	 */
	byte[] getSignature();

	/**
	 * Gets the signature in compact serialized form.
	 * 
	 * @return compact serialized form
	 */
	String compact();

	/**
	 * Gets the signature in JSON serialized form.
	 * 
	 * @return JSON serialized form
	 */
	@Override
	String toString();

}
