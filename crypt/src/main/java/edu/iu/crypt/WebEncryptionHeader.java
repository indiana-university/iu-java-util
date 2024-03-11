package edu.iu.crypt;

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
		AES_128_CBC_HMAC_SHA_256("A128CBC-HS256", "AES", 128, "CBC", "HmacSHA256"),

		/**
		 * AES_192_CBC_HMAC_SHA_384 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_192_CBC_HMAC_SHA_384("A192CBC-HS384", "AES", 192, "CBC", "HmacSHA384"),

		/**
		 * AES_256_CBC_HMAC_SHA_512 authenticated encryption algorithm.
		 * 
		 * @see <a href=
		 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-5.2.3">RFC-7518
		 *      Section 5.2.3</a>
		 */
		AES_256_CBC_HMAC_SHA_512("A192CBC-HS384", "AES", 256, "CBC", "HmacSHA512"),

		/**
		 * AES-128 GCM.
		 */
		A128GCM("A128GCM", "AES", 128, "GCM", null),

		/**
		 * AES-192 GCM.
		 */
		A192GCM("A192GCM", "AES", 192, "GCM", null),

		/**
		 * AES-256 GCM.
		 */
		A256GCM("A256GCM", "AES", 256, "GCM", null);

		/**
		 * JOSE enc attribute value.
		 */
		public final String enc;

		/**
		 * JCE Cipher algorithm name.
		 */
		public final String cipherAlgorithm;

		/**
		 * CEK size, in bits.
		 */
		public final int size;

		/**
		 * JCE Cipher mode.
		 */
		public final String cipherMode;

		/**
		 * JCE MAC algorithm.
		 */
		public final String mac;

		private Encryption(String enc, String cipherAlgorithm, int size, String cipherMode, String mac) {
			this.enc = enc;
			this.cipherAlgorithm = cipherAlgorithm;
			this.size = size;
			this.cipherMode = cipherMode;
			this.mac = mac;
		}
	}

	/**
	 * Gets the encryption algorithm.
	 * 
	 * @return encryption algorithm
	 */
	default Encryption getEncryption() {
		return null;
	}

	/**
	 * Determines whether or not to compress content before encryption.
	 * 
	 * @return true to compress content before encrypting; false to encrypt as-is
	 */
	default boolean isDeflate() {
		return true;
	}

	/**
	 * Gets additional authenticated data, for AEAD encryption.
	 * 
	 * @return additional authenticated data
	 */
	default byte[] getAdditionalAuthenticatedData() {
		return null;
	}

}
