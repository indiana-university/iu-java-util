/*
 * Copyright © 2026 Indiana University
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Stream;

import edu.iu.IuText;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Unifies algorithm support and maps from JCE encryption to JSON Web Encryption
 * (JWE).
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC 7516</a>
 */
public interface WebEncryption {

	/**
	 * Enumerates content encryption algorithms.
	 */
	enum Encryption {

		/**
		 * AES_128_CBC_HMAC_SHA_256 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_128_CBC_HMAC_SHA_256("A128CBC-HS256", 256, "AES/CBC/PKCS5Padding", "HmacSHA256"),

		/**
		 * AES_192_CBC_HMAC_SHA_384 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_192_CBC_HMAC_SHA_384("A192CBC-HS384", 384, "AES/CBC/PKCS5Padding", "HmacSHA384"),

		/**
		 * AES_256_CBC_HMAC_SHA_512 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_256_CBC_HMAC_SHA_512("A256CBC-HS512", 512, "AES/CBC/PKCS5Padding", "HmacSHA512"),

		/**
		 * AES-128 GCM.
		 */
		A128GCM("A128GCM", 128, "AES/GCM/NoPadding", null),

		/**
		 * AES-192 GCM.
		 */
		A192GCM("A192GCM", 192, "AES/GCM/NoPadding", null),

		/**
		 * AES-256 GCM.
		 */
		A256GCM("A256GCM", 256, "AES/GCM/NoPadding", null);

		/**
		 * Selects encryption by JOSE enc parameter value.
		 * 
		 * @param enc JOSE parameter value
		 * @return encryption
		 */
		public static Encryption from(String enc) {
			return Stream.of(Encryption.values()).filter(a -> a.enc.equals(enc)).findFirst().get();
		}

		/**
		 * JOSE enc attribute value.
		 */
		public final String enc;

		/**
		 * CEK size, in bits.
		 */
		public final int size;

		/**
		 * JCE Cipher algorithm.
		 */
		public final String algorithm;

		/**
		 * JCE MAC algorithm.
		 */
		public final String mac;

		private Encryption(String enc, int size, String algorithm, String mac) {
			this.enc = enc;
			this.size = size;
			this.algorithm = algorithm;
			this.mac = mac;
		}
	}

	/**
	 * Prepares a new encrypted message.
	 */
	interface Builder {
		/**
		 * Protects all header parameters except jwk and verifies inputs are valid for
		 * JWE compact serialization.
		 * 
		 * @return this
		 */
		Builder compact();

		/**
		 * Defines standard protected header parameters.
		 * 
		 * @param params protected header parameters
		 * @return this
		 */
		Builder protect(Param... params);

		/**
		 * Defines extended protected header parameters.
		 * 
		 * @param params protected header parameter names
		 * @return this
		 */
		Builder protect(String... params);

		/**
		 * Provides additional authentication data for protecting the encrypted content.
		 * 
		 * @param additionalData additional authentication data
		 * @return this
		 */
		Builder aad(byte[] additionalData);

		/**
		 * Adds a new recipient.
		 * 
		 * @param algorithm key encryption algorithm
		 * @return {@link WebEncryptionRecipient.Builder}
		 */
		WebEncryptionRecipient.Builder<?> addRecipient(Algorithm algorithm);

		/**
		 * Encrypts data for sending to all recipients.
		 * 
		 * @param text data to encrypt
		 * @return encrypted message
		 */
		default WebEncryption encrypt(String text) {
			return encrypt(IuText.utf8(text));
		}

		/**
		 * Encrypts data for sending to all recipients.
		 * 
		 * @param data data to encrypt
		 * @return encrypted message
		 */
		default WebEncryption encrypt(byte[] data) {
			return encrypt(new ByteArrayInputStream(data));
		}

		/**
		 * Encrypts data for sending to all recipients.
		 * 
		 * @param in stream of data to encrypt
		 * @return encrypted message
		 */
		WebEncryption encrypt(InputStream in);
	}

	/**
	 * Starts a new encrypted message for a single recipient with
	 * {@link Builder#compact() compact semantics} and compression enabled.
	 * 
	 * @param encryption {@link Encryption content encryption algorithm}
	 * @param algorithm  {@link Algorithm key encryption algorithm}
	 * 
	 * @return {@link WebEncryptionRecipient.Builder}
	 */
	static WebEncryptionRecipient.Builder<?> to(Encryption encryption, Algorithm algorithm) {
		return builder(encryption, true).compact().addRecipient(algorithm);
	}

	/**
	 * Starts a new encrypted message with compression enabled.
	 * 
	 * @param encryption {@link Encryption encryption algorithm}
	 * 
	 * @return {@link Builder}
	 */
	static Builder builder(Encryption encryption) {
		return builder(encryption, true);
	}

	/**
	 * Starts a new encrypted message.
	 * 
	 * @param encryption {@link Encryption encryption algorithm}
	 * @param deflate    true to compress content; false to encrypt without
	 *                   compression
	 * 
	 * @return {@link Builder}
	 */
	static Builder builder(Encryption encryption, boolean deflate) {
		return Init.SPI.getJweBuilder(encryption, deflate);
	}

	/**
	 * Parses a compact or serialized JWE.
	 * 
	 * @param jwe compact or serialized JWE
	 * @return {@link WebEncryption}
	 */
	static WebEncryption parse(String jwe) {
		return Init.SPI.parseJwe(jwe);
	}

	/**
	 * Gets the encryption algorithm.
	 * 
	 * @return encryption algorithm
	 */
	Encryption getEncryption();

	/**
	 * Determines whether or not to compress content before encryption.
	 * 
	 * @return true to compress content before encrypting; false to encrypt as-is
	 */
	boolean isDeflate();

	/**
	 * Gets the recipients.
	 * 
	 * @return recipients
	 */
	Iterable<? extends WebEncryptionRecipient> getRecipients();

	/**
	 * Gets the iv JWE attribute
	 * 
	 * @return iv JWE attribute
	 */
	byte[] getInitializationVector();

	/**
	 * Gets the ciphertext JWE attribute
	 * 
	 * @return ciphertext JWE attribute
	 */
	byte[] getCipherText();

	/**
	 * Gets the tag JWE attribute
	 * 
	 * @return tag JWE attribute
	 */
	byte[] getAuthenticationTag();

	/**
	 * Gets the aad JWE attribute
	 * 
	 * @return aad JWE attribute
	 */
	byte[] getAdditionalData();

	/**
	 * Decrypts UTF-8 encoded encrypted content.
	 * 
	 * @param key private or secret key; <em>should</em> be verified by the
	 *            application as correct for the recipient before calling.
	 * @return decrypted content
	 */
	default String decryptText(WebKey key) {
		return IuText.utf8(decrypt(key));
	}

	/**
	 * Decrypts the encrypted content.
	 * 
	 * @param key private or secret key; <em>should</em> be verified by the
	 *            application as correct for the recipient before calling.
	 * @return decrypted content
	 */
	default byte[] decrypt(WebKey key) {
		final var out = new ByteArrayOutputStream();
		decrypt(key, out);
		return out.toByteArray();
	}

	/**
	 * Decrypts the encrypted content.
	 * 
	 * @param key private or secret key; <em>should</em> be verified by the
	 *            application as correct for the recipient before calling.
	 * @param out {@link OutputStream} to write the decrypted content to
	 */
	void decrypt(WebKey key, OutputStream out);

	/**
	 * Gets the message encrypted for only this recipient in compact JWE format.
	 * 
	 * @return compact JWE
	 */
	String compact();

	/**
	 * Gets the encrypted message in serialized JWE format.
	 * 
	 * @return serialized JWE
	 */
	@Override
	String toString();

}
