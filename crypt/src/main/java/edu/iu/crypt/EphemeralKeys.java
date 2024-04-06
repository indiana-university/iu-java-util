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

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.KeyGenerator;

import edu.iu.IuException;

/**
 * Generates ephemeral keys.
 * 
 * <p>
 * Ephemeral keys are generated using JDK 11 compliant <a href=
 * "https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html">
 * standard algorithms</a> with {@link Security#getProviders() registered JCE
 * providers}
 * </p>
 */
public class EphemeralKeys {

	/**
	 * Securely generates pseudorandom data.
	 * 
	 * @param bytes number of bytes to generate
	 * @return securely generated pseudorandom data.
	 */
	public static final byte[] rand(int bytes) {
		final var data = new byte[bytes];
		IuException.unchecked(SecureRandom::getInstanceStrong).nextBytes(data);
		return data;
	}

	/**
	 * Generates a random secret key.
	 * 
	 * @param algorithm {@link KeyGenerator} algorithm
	 * @param size      key size in bits
	 * @return AES key
	 */
	public static final byte[] secret(String algorithm, int size) {
		return IuException.unchecked(() -> {
			final var keygen = IuException.unchecked(() -> KeyGenerator.getInstance(algorithm));
			keygen.init(size);
			return keygen.generateKey().getEncoded();
		});
	}

	/**
	 * Generates a random Elliptic Curve (EC) key.
	 * 
	 * @param param EC paramter spec name
	 * @return {@link KeyPair}
	 */
	public static final KeyPair ec(String param) {
		return IuException.unchecked(() -> {
			final var gen = KeyPairGenerator.getInstance("EC");
			gen.initialize(new ECGenParameterSpec(param));
			return gen.generateKeyPair();
		});
	}

	/**
	 * Generates a random RSA key
	 * 
	 * @param algorithm RSA KeyPairGenerator algorithm: RSA or RSASSA-PSS
	 * @param size      RSA key size in bits
	 * @return {@link KeyPair}
	 */
	public static final KeyPair rsa(String algorithm, int size) {
		return IuException.unchecked(() -> {
			final var gen = KeyPairGenerator.getInstance(algorithm);
			gen.initialize(size);
			return gen.generateKeyPair();
		});
	}

	/**
	 * Generates a content encryption key for AES/GCM.
	 * 
	 * @param size key size
	 * @return content encryption key
	 */
	public static final byte[] contentEncryptionKey(int size) {
		return contentEncryptionKey(null, size);
	}

	/**
	 * Generates a content encryption key.
	 * 
	 * @param mac  MAC algorithm for AES/CBC/HMAC; null for AES/GCM
	 * @param size key size
	 * @return content encryption key
	 */
	public static final byte[] contentEncryptionKey(String mac, int size) {
		final var keylen = size / (mac == null ? 1 : 2);
		final byte[] key = secret("AES", keylen);
		if (mac == null)
			return key;

		final var buffer = ByteBuffer.wrap(new byte[keylen / 4]);
		buffer.put(secret(mac, keylen));
		buffer.put(key);
		return buffer.array();
	}

}
