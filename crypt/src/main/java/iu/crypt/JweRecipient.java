package iu.crypt;

import edu.iu.IuObject;
import edu.iu.crypt.WebEncryptionHeader;
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
	public WebEncryptionHeader getHeader() {
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

	@Override
	public int hashCode() {
		return IuObject.hashCode(header, encryptedKey);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;

		final var other = (JweRecipient) obj;
		return IuObject.equals(header, other.header) //
				&& IuObject.equals(encryptedKey, other.encryptedKey);
	}

	private boolean isProtected(String name) {
		if (encryption.isProtected(name))
			return true;

		final var crit = header.getCriticalExtendedParameters();
		return crit != null && crit.contains(name);
	}

}
