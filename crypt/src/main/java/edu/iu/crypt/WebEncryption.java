package edu.iu.crypt;

import iu.crypt.Jwe;

/**
 * Unifies algorithm support and maps from JCE encryption to JSON Web Encryption
 * (JWE).
 */
public interface WebEncryption {

	/**
	 * Encrypts data as JWE.
	 * 
	 * @param header header data
	 * @param data   data to encrypt
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	static WebEncryption encrypt(WebEncryptionHeader header, byte[] data) {
		return Jwe.encrypt(header, data, null);
	}

	/**
	 * Encrypts data as JWE.
	 * 
	 * @param header         header data
	 * @param data           data to encrypt
	 * @param additionalData additional data for AEAD authentication
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	static WebEncryption encrypt(WebEncryptionHeader header, byte[] data, byte[] additionalData) {
		return Jwe.encrypt(header, data, null);
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
	 * @param key decryption (private or secret) key
	 * @return decrypted content
	 */
	byte[] decrypt(WebKey key);

	/**
	 * Gets the encrypted message in compact JWE format.
	 * 
	 * @return compact JWE
	 */
	String compact();

	/**
	 * Gets the encrypted message in serialized JWE format.
	 * 
	 * @return serialized JWE
	 */
	@Override
	String toString();

}
