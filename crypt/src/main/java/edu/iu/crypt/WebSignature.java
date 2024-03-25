package edu.iu.crypt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import edu.iu.IuException;
import edu.iu.crypt.WebCryptoHeader.Param;

/**
 * Unifies algorithm support and maps from JCE encryption to JSON Web Encryption
 * (JWE).
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC 7515</a>
 */
public interface WebSignature {

	/**
	 * Provides parameters for creating new {@link WebSignature} instances.
	 * 
	 * @param <B> builder type
	 */
	interface Builder<B extends Builder<B>> extends WebCryptoHeader.Builder<B> {
		/**
		 * Defines standard protected header parameters.
		 * 
		 * @param params protected header parameters
		 * @return this
		 */
		B protect(Param... params);

		/**
		 * Signs text.
		 * 
		 * @param text message to sign
		 * @return signed message
		 */
		default WebSignature sign(String text) {
			return sign(IuException.unchecked(() -> text.getBytes("UTF-8")));
		}

		/**
		 * Signs data.
		 * 
		 * @param data data to sign
		 * @return signed data
		 */
		default WebSignature sign(byte[] data) {
			return sign(new ByteArrayInputStream(data));
		}

		/**
		 * Signs data.
		 * 
		 * @param in stream of data to sign
		 * @return signed data
		 */
		WebSignature sign(InputStream in);
	}

	/**
	 * Gets the signature header.
	 * 
	 * @return {@link WebCryptoHeader}
	 */
	WebCryptoHeader getHeader();

	/**
	 * Gets encrypted data.
	 * 
	 * @return encrypted data
	 */
	byte[] getPayload();

	/**
	 * Gets the signature data.
	 * 
	 * @return signature data
	 */
	byte[] getSignature();

	/**
	 * Gets the signature in compact serialized form.
	 * 
	 * @return compact serialized form
	 */
	String compact();

	/**
	 * Gets the signature in JSON serialized form.
	 * 
	 * @return JSON serialized form
	 */
	@Override
	String toString();

}
