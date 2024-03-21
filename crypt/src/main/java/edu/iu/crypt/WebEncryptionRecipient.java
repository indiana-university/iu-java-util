package edu.iu.crypt;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import edu.iu.IuException;

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
	}

	/**
	 * Gets the JOSE header.
	 * 
	 * @return {@link WebEncryptionHeader}
	 */
	WebCryptoHeader getHeader();

	/**
	 * Gets the encrypted_key JWE attribute
	 * 
	 * @return encrypted_key JWE attribute
	 */
	byte[] getEncryptedKey();

	/**
	 * Gets the message encrypted for only this recipient in compact JWE format.
	 * 
	 * @return compact JWE
	 */
	String compact();

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

}
