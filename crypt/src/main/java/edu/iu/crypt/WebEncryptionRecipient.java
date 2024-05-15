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

import java.io.InputStream;

/**
 * Represents the recipient of a {@link WebEncryption} JWE encrpted message.
 */
public interface WebEncryptionRecipient {

	/**
	 * Builder interface for defining {@link WebEncryptionRecipient} instances.
	 * 
	 * @param <B> builder type
	 */
	interface Builder<B extends Builder<B>> extends WebCryptoHeader.Builder<B> {
		/**
		 * Returns the {@link WebEncryption.Builder} that spawned this builder instance.
		 * 
		 * @return {@link WebEncryption.Builder}
		 */
		WebEncryption.Builder then();

		/**
		 * Shorthand for {@link #then()}{@link WebEncryption.Builder#encrypt(String)
		 * .encrypt(text)}
		 * 
		 * @param text data to encrypt
		 * @return encrypted message
		 */
		default WebEncryption encrypt(String text) {
 			return then().encrypt(text);
		}

		/**
		 * Shorthand for {@link #then()}{@link WebEncryption.Builder#encrypt(String)
		 * .encrypt(data)}
		 * 
		 * @param data data to encrypt
		 * @return encrypted message
		 */
		default WebEncryption encrypt(byte[] data) {
			return then().encrypt(data);
		}

		/**
		 * Shorthand for {@link #then()}{@link WebEncryption.Builder#encrypt(String)
		 * .encrypt(in)}
		 * 
		 * @param in stream of data to encrypt
		 * @return encrypted message
		 */
		default WebEncryption encrypt(InputStream in) {
			return then().encrypt(in);
		}
	}

	/**
	 * Gets the JOSE header.
	 * 
	 * @return {@link WebCryptoHeader}
	 */
	WebCryptoHeader getHeader();

	/**
	 * Gets the encrypted_key JWE attribute
	 * 
	 * @return encrypted_key JWE attribute
	 */
	byte[] getEncryptedKey();

}
