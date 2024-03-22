package edu.iu.crypt;

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

}
