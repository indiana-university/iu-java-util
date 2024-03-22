package edu.iu.crypt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.crypt.WebCryptoHeader.Param;
import iu.crypt.Jwe;
import iu.crypt.JweBuilder;

/**
 * Unifies algorithm support and maps from JCE encryption to JSON Web Encryption
 * (JWE).
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
		AES_128_CBC_HMAC_SHA_256("A128CBC-HS256", "AES", 256, "AES/CBC/PKCS5Padding", "HmacSHA256"),

		/**
		 * AES_192_CBC_HMAC_SHA_384 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_192_CBC_HMAC_SHA_384("A192CBC-HS384", "AES", 384, "AES/CBC/PKCS5Padding", "HmacSHA384"),

		/**
		 * AES_256_CBC_HMAC_SHA_512 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_256_CBC_HMAC_SHA_512("A256CBC-HS512", "AES", 512, "AES/CBC/PKCS5Padding", "HmacSHA512"),

		/**
		 * AES-128 GCM.
		 */
		A128GCM("A128GCM", "AES", 128, "AES/GCM/NoPadding", null),

		/**
		 * AES-192 GCM.
		 */
		A192GCM("A192GCM", "AES", 192, "AES/GCM/NoPadding", null),

		/**
		 * AES-256 GCM.
		 */
		A256GCM("A256GCM", "AES", 256, "AES/GCM/NoPadding", null);

		/**
		 * Selects encryption by JOSE enc parameter value.
		 * 
		 * @param enc JOSE parameter value
		 * @return encryption
		 */
		public static Encryption from(String enc) {
			return EnumSet.allOf(Encryption.class).stream().filter(a -> IuObject.equals(enc, a.enc)).findFirst().get();
		}

		/**
		 * JOSE enc attribute value.
		 */
		public final String enc;

		/**
		 * JCE secret key algorithm;
		 */
		public final String keyAlgorithm;

		/**
		 * CEK size, in bits.
		 */
		public final int size;

		/**
		 * JCE Cipher algorithm.
		 */
		public final String cipherAlgorithm;

		/**
		 * JCE MAC algorithm.
		 */
		public final String mac;

		private Encryption(String enc, String cipherAlgorithm, int size, String cipherMode, String mac) {
			this.enc = enc;
			this.keyAlgorithm = cipherAlgorithm;
			this.size = size;
			this.cipherAlgorithm = cipherMode;
			this.mac = mac;
		}
	}

	/**
	 * Prepares a new encrypted message.
	 */
	interface Builder {
		/**
		 * Sets encryption algorithm.
		 * 
		 * @param encryption encryption algorithm
		 * @return this
		 */
		Builder enc(Encryption encryption);

		/**
		 * Determines whether or not to compress content before encryption.
		 * 
		 * @param deflate false to encrypt as-is; true (default) to compress content
		 *                before encrypting
		 * @return this
		 */
		Builder deflate(boolean deflate);

		/**
		 * Defines standard protected header parameters.
		 * 
		 * @param params protected header parameters
		 * @return this
		 */
		Builder protect(Param... params);

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
		 * @return {@link WebEncryptionRecipient.Builder}
		 */
		WebEncryptionRecipient.Builder<?> addRecipient();

		/**
		 * Encrypts data for sending to all recipients.
		 * 
		 * @param text data to encrypt
		 * @return encrypted message
		 */
		default WebEncryption encrypt(String text) {
			return encrypt(IuException.unchecked(() -> text.getBytes("UTF-8")));
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
	 * Starts a new encrypted message.
	 * 
	 * @return {@link Builder}
	 */
	static Builder builder() {
		return new JweBuilder();
	}

	/**
	 * Parses a compact or serialized JWE.
	 * 
	 * @param jwe compact or serialized JWE
	 * @return {@link WebEncryption}
	 */
	static WebEncryption parse(String jwe) {
		return new Jwe(jwe);
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
	Stream<? extends WebEncryptionRecipient> getRecipients();

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
		return IuException.unchecked(() -> new String(decrypt(key), "UTF-8"));
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
	 * Gets the encrypted message in serialized JWE format.
	 * 
	 * @return serialized JWE
	 */
	@Override
	String toString();

}
