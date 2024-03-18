package edu.iu.crypt;

/**
 * Represents the recipient of a {@link WebEncryption} JWE encrpted message.
 */
public interface WebEncryptionRecipient {

	/**
	 * Gets the JOSE header.
	 * 
	 * @return {@link WebEncryptionHeader}
	 */
	WebEncryptionHeader getHeader();

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
	 * Decrypts the encrypted content.
	 * 
	 * @param key private or secret key; <em>should</em> be verified by the
	 *            application as correct for the recipient before calling.
	 * @return decrypted content
	 */
	byte[] decrypt(WebKey key);

}
