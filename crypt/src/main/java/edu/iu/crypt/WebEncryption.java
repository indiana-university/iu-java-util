package edu.iu.crypt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignatureHeader.Param;
import iu.crypt.JweBuilder;

/**
 * Unifies algorithm support and maps from JCE encryption to JSON Web Encryption
 * (JWE).
 */
public interface WebEncryption {

	/**
	 * Prepares a new encrypted message.
	 */
	interface Builder {

		/**
		 * Determines whether or not to compress content before encryption.
		 * 
		 * <p>
		 * By default, content will be encrypted.
		 * </p>
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
		 * Adds a new recipient.
		 * 
		 * @return {@link WebEncryptionRecipient.Builder}
		 */
		WebEncryptionRecipient.Builder<?> add();

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
	 * Starts a new encrypted message with direct key agreement.
	 * 
	 * @param algorithm Any algorithm with {@link Algorithm#use} equal to
	 *                  {@link Use#ENCRYPT} and null {@link Algorithm#keyAlgorithm}
	 * @return {@link Builder}
	 */
	static Builder with(Algorithm algorithm) {
		return new JweBuilder(algorithm, null);
	}

	/**
	 * Starts a new encrypted message with direct key agreement.
	 * 
	 * @param algorithm  Any algorithm with {@link Algorithm#use} equal to
	 *                   {@link Use#ENCRYPT} and non-null
	 *                   {@link Algorithm#keyAlgorithm}
	 * @param encryption
	 * @return {@link Builder}
	 */
	static Builder of(Algorithm algorithm, Encryption encryption) {
		return new JweBuilder(algorithm, encryption);
	}

	/**
	 * Parses a compact or serialized JWE.
	 * 
	 * @param jwe compact or serialized JWE
	 * @return {@link WebEncryption}
	 */
	static WebEncryption parse(String jwe) {
		return JweBuilder.parse(jwe);
	}

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
	 * Gets the encrypted message in serialized JWE format.
	 * 
	 * @return serialized JWE
	 */
	@Override
	String toString();

}
