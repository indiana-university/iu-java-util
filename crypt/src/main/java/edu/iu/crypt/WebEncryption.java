package edu.iu.crypt;

import java.util.Set;

import edu.iu.crypt.WebSignatureHeader.Param;
import iu.crypt.Jwe;

/**
 * Unifies algorithm support and maps from JCE encryption to JSON Web Encryption
 * (JWE).
 */
public interface WebEncryption {

	/**
	 * Encrypts data as JWE.
	 * 
	 * <p>
	 * Includes standard algorithm (alg, enc) parameters if provided, and all
	 * {@link WebSignatureHeader#getCriticalExtendedParameters() critical
	 * parameters}, as protected parameters.
	 * </p>
	 * 
	 * @param recipientHeaders header data for each recipient
	 * @param data             data to encrypt
	 * @param additionalData   optional additional data for AEAD authentication
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption encrypt(Iterable<WebEncryptionHeader> recipientHeaders, byte[] data) {
		return Jwe.encrypt(recipientHeaders, null, data, null);
	}

	/**
	 * Encrypts data as JWE.
	 * 
	 * @param recipientHeaders    header data for each recipient
	 * @param protectedParameters set of parameters to include in the protected
	 *                            header
	 * @param data                data to encrypt
	 * @param additionalData      optional additional data for AEAD authentication
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption encrypt(Iterable<WebEncryptionHeader> recipientHeaders, Set<Param> protectedParameters,
			byte[] data) {
		return Jwe.encrypt(recipientHeaders, protectedParameters, data, null);
	}

	/**
	 * Encrypts data as JWE.
	 * 
	 * <p>
	 * Includes standard algorithm (alg, enc) and key identification (jku, kid, x5u,
	 * x5t, x5t#S256) parameters if provided, and all
	 * {@link WebSignatureHeader#getCriticalExtendedParameters() critical
	 * parameters}, as protected parameters.
	 * </p>
	 * 
	 * @param recipientHeaders header data for each recipient
	 * @param data             data to encrypt
	 * @param additionalData   optional additional data for AEAD authentication
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption encrypt(Iterable<WebEncryptionHeader> recipientHeaders, byte[] data,
			byte[] additionalData) {
		return Jwe.encrypt(recipientHeaders, null, data, additionalData);
	}

	/**
	 * Encrypts data as JWE.
	 * 
	 * @param recipientHeaders    header data for each recipient
	 * @param protectedParameters set of parameters to include in the protected
	 *                            header
	 * @param data                data to encrypt
	 * @param additionalData      optional additional data for AEAD authentication
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption encrypt(Iterable<WebEncryptionHeader> recipientHeaders, Set<Param> protectedParameters,
			byte[] data, byte[] additionalData) {
		return Jwe.encrypt(recipientHeaders, protectedParameters, data, additionalData);
	}

	/**
	 * Parses a {@link #compact() compact} or {@link #serialize() serialized} JWE
	 * encrypted message.
	 * 
	 * @param jwe compact or serialized JWE message
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	static WebEncryption readJwe(String jwe) {
		return Jwe.readJwe(jwe);
	}

	/**
	 * Gets the recipients.
	 * 
	 * @return recipients
	 */
	Iterable<WebEncryptionRecipient> getRecipients();

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
	 * Decrypts the encrypted content.
	 * 
	 * @param key decryption (private or secret) key; <em>should</em> be verified by
	 *            the application as correct for the encryption scenario before
	 *            calling i.e., by inspecting {@link #getRecipients()}.
	 * @return decrypted content
	 */
	byte[] decrypt(WebKey key);

	/**
	 * Gets the encrypted message in serialized JWE format.
	 * 
	 * @return serialized JWE
	 */
	@Override
	String toString();

}
