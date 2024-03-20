package edu.iu.crypt;

import java.util.EnumSet;

import edu.iu.IuObject;

/**
 * Unifies algorithm support and maps cryptographic header data from JCE to JSON
 * Object Signing and Encryption (JOSE).
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">JSON Web Key
 *      (JWK) RFC-7517</a>
 */
public interface WebEncryptionHeader extends WebSignatureHeader {

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
		AES_128_CBC_HMAC_SHA_256("A128CBC-HS256", "AES", 128, "AES/CBC/PKCS5Padding", "HmacSHA256"),

		/**
		 * AES_192_CBC_HMAC_SHA_384 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_192_CBC_HMAC_SHA_384("A192CBC-HS384", "AES", 192, "AES/CBC/PKCS5Padding", "HmacSHA384"),

		/**
		 * AES_256_CBC_HMAC_SHA_512 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_256_CBC_HMAC_SHA_512("A192CBC-HS384", "AES", 256, "AES/CBC/PKCS5Padding", "HmacSHA512"),

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

}
