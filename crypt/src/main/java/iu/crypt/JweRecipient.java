package iu.crypt;

import edu.iu.crypt.WebEncryptionRecipient;

/**
 * Represents a recipient of a {@link Jwe} encrypted message.
 */
class JweRecipient implements WebEncryptionRecipient {

	private final Jwe encryption;
	private final Jose header;
	private final byte[] encryptedKey;

	/**
	 * Constructor.
	 * 
	 * @param encryption   encrypted message
	 * @param header       recipient header
	 * @param encryptedKey encrypted key
	 */
	JweRecipient(Jwe encryption, Jose header, byte[] encryptedKey) {
		this.encryption = encryption;
		this.header = header;
		this.encryptedKey = encryptedKey;
	}

	@Override
	public Jose getHeader() {
		return header;
	}

	@Override
	public byte[] getEncryptedKey() {
		return encryptedKey;
	}

	@Override
	public String compact() {
		return EncodingUtils.base64Url(EncodingUtils.utf8(header.toJson(this::isProtected).toString())) //
				+ '.' + EncodingUtils.base64Url(encryptedKey) //
				+ '.' + EncodingUtils.base64Url(encryption.getInitializationVector()) //
				+ '.' + EncodingUtils.base64Url(encryption.getCipherText()) //
				+ '.' + EncodingUtils.base64Url(encryption.getAuthenticationTag());
	}

	/**
	 * Determines which if a JOSE parameter is protected for this recipient.
	 * 
	 * @param name parameter name
	 * @return true if protected; else false
	 */
	boolean isProtected(String name) {
		if (encryption.isProtected(name))
			return true;

		final var crit = header.getCriticalExtendedParameters();
		return crit != null && crit.contains(name);
	}

}
