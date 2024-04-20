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
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebEncryptionRecipient;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Represents a recipient of a {@link Jwe} encrypted message.
 */
class JweRecipient implements WebEncryptionRecipient {

	private final Jose header;
	private final byte[] encryptedKey;

	/**
	 * Constructor.
	 * 
	 * @param header       header
	 * @param encryptedKey encrypted key
	 */
	JweRecipient(Jose header, byte[] encryptedKey) {
		this.header = header;
		this.encryptedKey = encryptedKey;
	}

	/**
	 * Constructor.
	 * 
	 * @param encryption      encrypted message
	 * @param protectedHeader protected header parameters
	 * @param sharedHeader    shared header parameters
	 * @param recipient       recipient parameters
	 */
	JweRecipient(JsonObject protectedHeader, JsonObject sharedHeader, JsonObject recipient) {
		this(Jose.from(protectedHeader, sharedHeader,
				IuJson.get(recipient, "header", IuJsonAdapter.from(JsonValue::asJsonObject))),
				IuJson.get(recipient, "encrypted_key", UnpaddedBinary.JSON));
	}

	@Override
	public Jose getHeader() {
		return header;
	}

	@Override
	public byte[] getEncryptedKey() {
		return encryptedKey;
	}

	/**
	 * Computes the agreed-upon key for the Elliptic Curve Diffie-Hellman algorithm.
	 * 
	 * @param encryption          content encryption algorithm
	 * @param recipientPrivateKey recipient's private key
	 * 
	 * @return agreed-upon key
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
	 *      Section 4.6</a>
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7516#section-5.1">RFC-7516 JWE
	 *      Section 5.1 #3</a>
	 */
	byte[] agreedUponKey(Encryption encryption, WebKey recipientPrivateKey) {
		final Jwk epk = Objects.requireNonNull(header.getExtendedParameter("epk"),
				"epk required for " + header.getAlgorithm());
		if (!epk.getType().equals(recipientPrivateKey.getType()))
			throw new IllegalArgumentException("Private key type doesn't match epk");

		final byte[] uinfo = header.getExtendedParameter("apu");
		final byte[] vinfo = header.getExtendedParameter("apv");
		final var algorithm = header.getAlgorithm();

		final int keyDataLen;
		final byte[] algId;
		if (algorithm.equals(Algorithm.ECDH_ES)) {
			keyDataLen = encryption.size;
			algId = IuText.ascii(encryption.enc);
		} else {
			keyDataLen = algorithm.size;
			algId = IuText.ascii(algorithm.alg);
		}

		final String keyAlg;
		final var type = recipientPrivateKey.getType();
		if (type.kty.equals("EC"))
			keyAlg = "ECDH";
		else
			keyAlg = type.algorithmParams;

		return JweRecipientBuilder.agreedUponKey(recipientPrivateKey.getPrivateKey(), epk.getPublicKey(), keyAlg, algId,
				uinfo, vinfo, keyDataLen);
	}

	/**
	 * Gets the passphrase-derived key to use with PBKDF2 key derivation defined by
	 * <a href="https://datatracker.ietf.org/doc/html/rfc8018">PKCS#5</a>.
	 * 
	 * @param passphrase passphrase
	 * @return 128-bit derived key data suitable for use with AESWrap
	 */
	byte[] passphraseDerivedKey(String passphrase) {
		final var algorithm = header.getAlgorithm();

		final var alg = IuText.utf8(algorithm.alg);
		final byte[] p2s = Objects.requireNonNull(header.getExtendedParameter("p2s"), "p2s required for " + algorithm);
		final int p2c = Objects.requireNonNull(header.getExtendedParameter("p2c"), "p2c required for " + algorithm);

		final var saltValue = ByteBuffer.wrap(new byte[alg.length + 1 + p2s.length]);
		saltValue.put(alg);
		saltValue.put((byte) 0);
		saltValue.put(p2s);

		return IuException
				.unchecked(() -> SecretKeyFactory.getInstance(algorithm.algorithm)
						.generateSecret(new PBEKeySpec(passphrase.toCharArray(), saltValue.array(), p2c, 128)))
				.getEncoded();
	}

	/**
	 * Decrypts the content encryption key (CEK)
	 * 
	 * @param encryption content encryption algorithm
	 * @param recipient  in-progress recipient builder
	 * @param privateKey private key
	 * @return content encryption key
	 */
	@SuppressWarnings("deprecation")
	byte[] decryptCek(Encryption encryption, Jwk privateKey) {
		// 5.2#7 Verify that the JWE uses a key known to the recipient.
		final var recipientPublicKey = header.wellKnown();
		if (recipientPublicKey != null //
				&& recipientPublicKey.getPublicKey() != null //
				&& !recipientPublicKey.represents(privateKey))
			throw new IllegalArgumentException("Key is not valid for recipient");

		final var algorithm = header.getAlgorithm();
		if (algorithm.equals(Algorithm.DIRECT)) {
			if (encryptedKey != null)
				// 5.2#10 verify that the JWE Encrypted Key value is empty
				throw new IllegalArgumentException("encrypted key must be empty for " + algorithm);

			// 5.2#11 use shared key as CEK for direct encryption
			final var cek = Objects.requireNonNull(privateKey.getKey(), "DIRECT requires a secret key");
			if (cek.length != encryption.size / 8)
				throw new IllegalArgumentException("Invalid key size for " + encryption);
			return cek;
		}

		if (algorithm.equals(Algorithm.ECDH_ES))
			// 5.2#10 verify that the JWE Encrypted Key value is an empty
			if (encryptedKey != null)
				throw new IllegalArgumentException("encrypted key must be empty for " + algorithm);
			else
				// 5.2#8 use agreed upon key as CEK for direct encryption
				return agreedUponKey(encryption, privateKey);

		// 5.2#9 encrypt CEK to the recipient
		Objects.requireNonNull(encryptedKey, "encrypted key required for " + algorithm);

		final byte[] cek;
		if (EnumSet.of(Algorithm.A128KW, Algorithm.A192KW, Algorithm.A256KW).contains(algorithm))
			// key wrapping
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(privateKey.getKey(), "AES");
				final var cipher = Cipher.getInstance(algorithm.algorithm);
				cipher.init(Cipher.UNWRAP_MODE, key);
				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
		else if (EnumSet.of(Algorithm.A128GCMKW, Algorithm.A192GCMKW, Algorithm.A256GCMKW).contains(algorithm))
			// key wrapping
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(privateKey.getKey(), "AES");

				final byte[] iv = Objects.requireNonNull(header.getExtendedParameter("iv"),
						"iv required for " + algorithm);
				if (iv.length != 12)
					throw new IllegalArgumentException("iv must be 96 bits");

				final byte[] tag = Objects.requireNonNull(header.getExtendedParameter("tag"),
						"tag required for " + algorithm);
				if (tag.length != 16)
					throw new IllegalArgumentException("tag must be 128 bits");

				final var wrappedKey = Arrays.copyOf(encryptedKey, encryptedKey.length + 16);
				System.arraycopy(tag, 0, wrappedKey, encryptedKey.length, 16);

				final var cipher = Cipher.getInstance(algorithm.algorithm);
				cipher.init(Cipher.UNWRAP_MODE, key, new GCMParameterSpec(128, iv));

				return ((SecretKey) cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
		else if (EnumSet.of(Algorithm.RSA1_5, Algorithm.RSA_OAEP, Algorithm.RSA_OAEP_256).contains(algorithm))
			// key encryption
			cek = IuException.unchecked(() -> {
				final var rsa = (RSAPrivateKey) privateKey.getPrivateKey();
				final var keyCipher = Cipher.getInstance(algorithm.algorithm);
				keyCipher.init(Cipher.DECRYPT_MODE, rsa);
				return keyCipher.doFinal(encryptedKey);
			});
		else if (EnumSet.of(Algorithm.ECDH_ES_A128KW, Algorithm.ECDH_ES_A192KW, Algorithm.ECDH_ES_A256KW)
				.contains(algorithm))
			// key agreement with key wrapping
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(agreedUponKey(encryption, privateKey), "AES");
				final var cipher = Cipher.getInstance("AESWrap");
				cipher.init(Cipher.UNWRAP_MODE, key);
				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
		else // if (EnumSet.of(Algorithm.PBES2_HS256_A128KW, Algorithm.PBES2_HS384_A192KW, Algorithm.PBES2_HS512_A256KW)
			//	.contains(algorithm))
			// password-based key derivation with key wrapping
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(passphraseDerivedKey(IuText.utf8(privateKey.getKey())), "AES");
				final var cipher = Cipher.getInstance("AESWrap");
				cipher.init(Cipher.UNWRAP_MODE, key);
				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});

		return cek;

//		case :
//		case :
//		case :
//			break;
//
//		default:
//		}
	}

}
