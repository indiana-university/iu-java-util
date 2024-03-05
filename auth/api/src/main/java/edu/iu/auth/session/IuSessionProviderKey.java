package edu.iu.auth.session;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Describes a trusted session provider key.
 */
public interface IuSessionProviderKey {

	/**
	 * Designates key type.
	 */
	enum Type {

		/**
		 * RSA key.
		 */
		RSA,

		/**
		 * NIST P-256 Elliptical Curve.
		 */
		EC_P256,

		/**
		 * NIST P-384 Elliptical Curve.
		 */
		EC_P384,

		/**
		 * NIST P-521 Elliptical Curve.
		 */
		EC_P521;
	}

	/**
	 * Designates key usage.
	 */
	enum Usage {

		/**
		 * Used for signing.
		 */
		SIGN,

		/**
		 * Use for encryption.
		 */
		ENCRYPT;
	}

	/**
	 * Gets the key ID.
	 * 
	 * @return key ID
	 */
	String getId();

	/**
	 * Gets the key type.
	 * 
	 * @return key type
	 */
	Type getType();

	/**
	 * Gets the key usage.
	 * 
	 * @return key usage
	 */
	Usage getUsage();

	/**
	 * Gets the public key.
	 * 
	 * @return public key
	 */
	PublicKey getPublic();

	/**
	 * Gets the private key.
	 * 
	 * @return private key
	 */
	PrivateKey getPrivate();

}
