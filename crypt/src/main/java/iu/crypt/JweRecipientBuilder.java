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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebEncryptionRecipient.Builder;
import edu.iu.crypt.DigestUtils;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;

/**
 * Builds JWE recipients for {@link JweBuilder}
 */
class JweRecipientBuilder extends JoseBuilder<JweRecipientBuilder> implements Builder<JweRecipientBuilder> {

	/**
	 * Computes the agreed-upon key for ECDH key agreement with NIST.SP.800.56C
	 * Concat KDF using SHA-256 as the key derivation formula.
	 * 
	 * @param privateKey JCE private key
	 * @param publicKey  JCE public key
	 * @param algorithm  JCE key agreement algorithm name
	 * @param algId      JWA algorithm ID
	 * @param uinfo      PartyUInfo value for Concat KDF
	 * @param vinfo      PartyVInfo value for Concat KDF
	 * @param keyDataLen bit length of desired output key, SuppPubInfo for Concat
	 *                   KDF
	 * @return derived key data
	 * @see <a href=
	 *      "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-56Ar3.pdf">NIST.SP.800-56Ar3</a>
	 * @see <a href=
	 *      "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-56Cr2.pdf">NIST.SP.800-56Cr2</a>
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6.2">RFC-7518
	 *      JSON Web Algorithms (JWA) 4.6.2</a>
	 */
	static byte[] agreedUponKey(PrivateKey privateKey, PublicKey publicKey, String algorithm, byte[] algId,
			byte[] uinfo, byte[] vinfo, int keyDataLen) {

		final var z = IuException.unchecked(() -> {
			final var ka = KeyAgreement.getInstance(algorithm);
			ka.init(privateKey);
			ka.doPhase(publicKey, true);
			return ka.generateSecret();
		});

		// JWA: H = SHA-256
		// L in [256,384,512] for AES-CBC-HMAC, [128,192,512] for AES-GCM and ECDH+KW
		// => 1 or 2 rounds
		final var reps = keyDataLen <= 256 ? 1 : 2;
		final var keyData = new byte[32 * reps];

		// NIST.SP.800-56Cr2 5.8.2.1.1:
		// R(0) = []
		// K = for n in [1..r]: R(n-1) || R(n)
		final var keyBuffer = ByteBuffer.wrap(keyData);
		for (var i = 0; i < reps; i++) {
			final var n = i + 1;
			// R(n) = H(n || Z || FixedInfo)
			keyBuffer.put(DigestUtils
					.sha256(EncodingUtils.concatKdf(n, z, /* FixedInfo = */ algId, uinfo, vinfo, keyDataLen)));
		}

		final var keylen = keyDataLen / 8;
		if (keyData.length == keylen)
			return keyData;
		else
			return Arrays.copyOf(keyData, keylen);
	}

	/**
	 * Handles ephemeral key-protection parameters.
	 */
	class EncryptedKeyBuilder extends JoseBuilder<EncryptedKeyBuilder> {

		private EncryptedKeyBuilder() {
			super(Algorithm.JSON.fromJson(JweRecipientBuilder.this.param("alg")));
			copy(JweRecipientBuilder.this);
		}

		/**
		 * Gets the algorithm
		 * 
		 * @return algorithm
		 */
		Algorithm algorithm() {
			return Algorithm.JSON.fromJson(param("alg"));
		}

		/**
		 * Computes the agreed-upon key for the Elliptic Curve Diffie-Hellman algorithm.
		 * 
		 * @param encryption encryption algorithm
		 * @param epk        key to use as the ephemeral public key; <em>must</em>
		 *                   contain an EC public/private key, when serialized with only
		 *                   kid, or jwk (if kid is null); <em>may</em> be null to
		 *                   generate an epk
		 * 
		 * @return agreed-upon key
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
		 *      Section 4.6</a>
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7516#section-5.1">RFC-7516 JWE
		 *      Section 5.1 #3</a>
		 */
		byte[] agreedUponKey(Encryption encryption) {
			final var algorithm = this.algorithm();

			final var key = key();
			final var type = key.getType();
			final String keyAlg;
			if (type.kty.equals("EC"))
				keyAlg = "ECDH";
			else
				keyAlg = type.algorithmParams;

			final var epk = WebKey.builder(type).algorithm(algorithm).ephemeral().build();
			param(Param.EPHEMERAL_PUBLIC_KEY, epk.wellKnown());

			final var uinfo = UnpaddedBinary.JSON.fromJson(param("apu"));
			final var vinfo = UnpaddedBinary.JSON.fromJson(param("apv"));

			final int keyDataLen;
			final byte[] algId;
			if (algorithm.equals(Algorithm.ECDH_ES)) {
				keyDataLen = encryption.size;
				algId = IuText.ascii(encryption.enc);
			} else {
				keyDataLen = algorithm.size;
				algId = IuText.ascii(algorithm.alg);
			}

			return JweRecipientBuilder.agreedUponKey(epk.getPrivateKey(), key().getPublicKey(), keyAlg, algId, uinfo,
					vinfo, keyDataLen);
		}

		/**
		 * Gets the passphrase-derived key to use with PBKDF2 key derivation defined by
		 * <a href="https://datatracker.ietf.org/doc/html/rfc8018">PKCS#5</a>.
		 * 
		 * @return 128-bit derived key data suitable for use with AESWrap
		 */
		byte[] passphraseDerivedKey() {
			final var algorithm = this.algorithm();

			final var alg = IuText.utf8(algorithm.alg);
			final byte[] p2s = new byte[algorithm.size / 8];
			new SecureRandom().nextBytes(p2s);
			param(Param.PASSWORD_SALT, p2s);

			// 128 -> 2048, 192 -> 3072, 256 -> 4096
			final var p2c = algorithm.size * 16;
			param(Param.PASSWORD_COUNT, p2c);

			final var saltValue = ByteBuffer.wrap(new byte[alg.length + 1 + p2s.length]);
			saltValue.put(alg);
			saltValue.put((byte) 0);
			saltValue.put(p2s);

			return IuException
					.unchecked(() -> SecretKeyFactory.getInstance(algorithm.algorithm).generateSecret(
							new PBEKeySpec(IuText.utf8(key().getKey()).toCharArray(), saltValue.array(), p2c, 128)))
					.getEncoded();
		}

		/**
		 * Generates the encrypted key and creates the recipient.
		 * 
		 * @param encryption           content encryption algorithm
		 * @param contentEncryptionKey supplies an ephemeral content encryption key if
		 *                             needed
		 * @param from                 message originator key, if known
		 * 
		 * @return recipient
		 */
		@SuppressWarnings({ "deprecation" })
		JweRecipient encrypt(Encryption encryption, byte[] contentEncryptionKey) {
			final var algorithm = this.algorithm();

			byte[] encryptedKey = null;
			// 5.1#4 encrypt CEK to the recipient
			switch (algorithm) {
			case A128KW:
			case A192KW:
			case A256KW:
				// key wrapping
				encryptedKey = IuException.unchecked(() -> {
					final var key = new SecretKeySpec(key().getKey(), "AES");
					final var cipher = Cipher.getInstance(algorithm.algorithm);
					cipher.init(Cipher.WRAP_MODE, key);
					return cipher.wrap(new SecretKeySpec(contentEncryptionKey, "AES"));
				});
				break;

			case A128GCMKW:
			case A192GCMKW:
			case A256GCMKW:
				// key wrapping w/ GCM
				encryptedKey = IuException.unchecked(() -> {
					final var key = new SecretKeySpec(key().getKey(), "AES");
					final var iv = new byte[12];
					new SecureRandom().nextBytes(iv);
					param(Param.INITIALIZATION_VECTOR, iv);

					final var cipher = Cipher.getInstance(algorithm.algorithm);
					cipher.init(Cipher.WRAP_MODE, key, new GCMParameterSpec(128, iv));
					final var wrappedKey = cipher.wrap(new SecretKeySpec(contentEncryptionKey, "AES"));

					param(Param.TAG, Arrays.copyOfRange(wrappedKey, wrappedKey.length - 16, wrappedKey.length));

					return Arrays.copyOf(wrappedKey, wrappedKey.length - 16);
				});
				break;

			case RSA1_5:
			case RSA_OAEP:
			case RSA_OAEP_256:
				// key encryption
				encryptedKey = IuException.unchecked(() -> {
					final var keyCipher = Cipher.getInstance(algorithm.algorithm);
					keyCipher.init(Cipher.ENCRYPT_MODE, key().getPublicKey());
					return keyCipher.doFinal(contentEncryptionKey);
				});
				break;

			case ECDH_ES_A128KW:
			case ECDH_ES_A192KW:
			case ECDH_ES_A256KW:
				// key agreement with key wrapping
				encryptedKey = IuException.unchecked(() -> {
					final var key = new SecretKeySpec(agreedUponKey(encryption), "AES");
					final var cipher = Cipher.getInstance("AESWrap");
					cipher.init(Cipher.WRAP_MODE, key);
					return cipher.wrap(new SecretKeySpec(contentEncryptionKey, "AES"));
				});
				break;

			case PBES2_HS256_A128KW:
			case PBES2_HS384_A192KW:
			case PBES2_HS512_A256KW:
				// passphrase-derived key with key wrapping
				encryptedKey = IuException.unchecked(() -> {
					final var key = new SecretKeySpec(passphraseDerivedKey(), "AES");
					final var cipher = Cipher.getInstance("AESWrap");
					cipher.init(Cipher.WRAP_MODE, key);
					return cipher.wrap(new SecretKeySpec(contentEncryptionKey, "AES"));
				});
				break;

			case ECDH_ES:
			case EDDSA:
			case DIRECT:
			default:
				IuObject.once(algorithm.use, Use.ENCRYPT, "encryption algorithm required");
				// 5.1#5 don't populate encrypted key for direct key agreement or encryption
				encryptedKey = null;
				break;
			}

			final var header = new Jose(toJson());
			return new JweRecipient(header, encryptedKey);
		}
	}

	private final JweBuilder jweBuilder;

	/**
	 * Constructor
	 * 
	 * @param jweBuilder JWE builder
	 * @param algorithm  key encryption algorithm
	 */
	JweRecipientBuilder(JweBuilder jweBuilder, Algorithm algorithm) {
		super(algorithm);
		this.jweBuilder = jweBuilder;
	}

	@Override
	public JweBuilder then() {
		return jweBuilder;
	}

	/**
	 * Gets a builder for completing key protection.
	 * 
	 * @return encrypted key builder
	 */
	EncryptedKeyBuilder encryptedKeyBuilder() {
		return new EncryptedKeyBuilder();
	}

}
