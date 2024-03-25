package iu.crypt;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.client.IuJson;
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
	 * @param encryption   encrypted message
	 * @param header       header
	 * @param encryptedKey encrypted key
	 */
	JweRecipient(Jwe encryption, Jose header, byte[] encryptedKey) {
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
	JweRecipient(Jwe encryption, JsonObject protectedHeader, JsonObject sharedHeader, JsonObject recipient) {
		this(encryption,
				Jose.from(protectedHeader, sharedHeader, IuJson.get(recipient, "header", JsonValue::asJsonObject)),
				IuJson.text(recipient, "encrypted_key", IuText::base64Url));
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
		final var epk = Objects.requireNonNull(header.<Jwk>getExtendedParameter("epk"),
				"epk required for " + header.getAlgorithm());
		final var uinfo = header.<byte[]>getExtendedParameter("apu");
		final var vinfo = header.<byte[]>getExtendedParameter("apv");
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

		return JweRecipientBuilder.agreedUponKey((ECPrivateKey) recipientPrivateKey.getPrivateKey(),
				(ECPublicKey) epk.getPublicKey(), algorithm.algorithm, algId, uinfo, vinfo, keyDataLen);
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
		if (recipientPublicKey != null && !recipientPublicKey.represents(privateKey))
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
		} else if (algorithm.equals(Algorithm.ECDH_ES))
			// 5.2#10 verify that the JWE Encrypted Key value is an empty
			if (encryptedKey != null)
				throw new IllegalArgumentException("encrypted key must be empty for " + algorithm);
			else
				// 5.2#8 use agreed upon key as CEK for direct encryption
				return agreedUponKey(encryption, privateKey);

		// 5.2#9 encrypt CEK to the recipient
		Objects.requireNonNull(encryptedKey, "encrypted key required for " + algorithm);

		final byte[] cek;
		switch (algorithm) {
		case A128KW:
		case A192KW:
		case A256KW:
			// key wrapping
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(privateKey.getKey(), "AES");
				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.UNWRAP_MODE, key);
				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
			break;

		case A128GCMKW:
		case A192GCMKW:
		case A256GCMKW:
			// key wrapping w/ GCM
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(privateKey.getKey(), "AES");

				final var iv = Objects.requireNonNull(header.<byte[]>getExtendedParameter("iv"),
						"iv required for " + algorithm);
				if (iv.length != 12)
					throw new IllegalStateException("iv must be 96 bits");

				final var tag = Objects.requireNonNull(header.<byte[]>getExtendedParameter("tag"),
						"tag required for " + algorithm);
				if (tag.length != 16)
					throw new IllegalStateException("iv must be 128 bits");

				final var wrappedKey = Arrays.copyOf(encryptedKey, encryptedKey.length + 16);
				System.arraycopy(tag, 0, wrappedKey, encryptedKey.length, 16);

				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.UNWRAP_MODE, key, new GCMParameterSpec(128, iv));

				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
			break;

		case RSA1_5:
		case RSA_OAEP:
		case RSA_OAEP_256:
			// key encryption
			cek = IuException.unchecked(() -> {
				final var keyCipher = Cipher.getInstance(algorithm.keyAlgorithm);
				keyCipher.init(Cipher.DECRYPT_MODE, privateKey.getPrivateKey());
				return keyCipher.doFinal(encryptedKey);
			});
			break;

		case ECDH_ES_A128KW:
		case ECDH_ES_A192KW:
		case ECDH_ES_A256KW:
			// key agreement with key wrapping
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(agreedUponKey(encryption, privateKey), "AES");
				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.UNWRAP_MODE, key);
				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
			break;

		default:
			throw new UnsupportedOperationException();
		}

		return cek;
	}

}
