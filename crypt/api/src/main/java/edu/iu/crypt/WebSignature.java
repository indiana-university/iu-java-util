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

import edu.iu.IuText;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Unifies algorithm support and maps from JCE encryption to JSON Web Encryption
 * (JWE).
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC 7516</a>
 */
public interface WebSignature {

	/**
	 * Provides parameters for creating new {@link WebSignature} instances.
	 * 
	 * @param <B> builder type
	 */
	interface Builder<B extends Builder<B>> extends WebCryptoHeader.Builder<B> {
		/**
		 * Enqueues the current signature and resets the builder for the next entry.
		 * 
		 * @param algorithm {@link Algorithm}
		 * @return {@link Builder}
		 */
		B next(Algorithm algorithm);

		/**
		 * Protects all header parameters except jwk and verifies inputs are valid for
		 * JWE compact serialization.
		 * 
		 * @return this
		 */
		B compact();

		/**
		 * Defines registered protected header parameters.
		 * 
		 * @param params protected header parameters
		 * @return this
		 */
		B protect(Param... params);

		/**
		 * Defines extended protected header parameters.
		 * 
		 * @param params protected header parameters
		 * @return this
		 */
		B protect(String... params);

		/**
		 * Signs text content encoded as UTF-8.
		 * 
		 * @param text message to sign
		 * @return this
		 */
		default WebSignedPayload sign(String text) {
			return sign(IuText.utf8(text));
		}

		/**
		 * Signs raw binary data.
		 * 
		 * @param data data to sign
		 * @return this
		 */
		default WebSignedPayload sign(byte[] data) {
			return sign(new ByteArrayInputStream(data));
		}

		/**
		 * Signs a raw data read from from an {@link InputStream}
		 * 
		 * @param in stream of data to sign
		 * @return signed data
		 */
		WebSignedPayload sign(InputStream in);
	}

	/**
	 * Creates a new {@link Builder}.
	 * 
	 * @param algorithm {@link Algorithm}
	 * @return {@link Builder}
	 */
	static Builder<?> builder(Algorithm algorithm) {
		return Init.SPI.getJwsBuilder(algorithm);
	}

	/**
	 * Gets the signature header.
	 * 
	 * @return {@link WebCryptoHeader}
	 */
	WebCryptoHeader getHeader();

	/**
	 * Gets the signature data.
	 * 
	 * @return signature data
	 */
	byte[] getSignature();

	/**
	 * Verifies the signature as valid.
	 * 
	 * @param payload payload to verify the signature against
	 * @param key     (public or shared) key to use for verifying the signature
	 */
	void verify(byte[] payload, WebKey key);

}
