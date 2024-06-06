package edu.iu.auth.config;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.crypt.WebKey;

/**
 * Describes an authorization configuration that describes a public key or
 * public/private key pair, for use with digital signature creation and
 * validation, encryption, and decryption.
 */
public interface IuPublicKeyPrincipalConfig extends IuAuthConfig {

	/**
	 * Gets the identity key, including private and/or secret key data if
	 * authoritative.
	 * 
	 * @return authoritative identity key; null if not authoritative: only a public
	 *         key or X.509 certificate is configured
	 */
	IuPrincipalIdentity getIdentity();

	/**
	 * Gets the digital signature verification and/or creation key.
	 * 
	 * @return signature key
	 */
	WebKey getSignatureKey();

	/**
	 * Gets the encryption key.
	 * 
	 * @return encryption key
	 */
	WebKey getEncryptKey();

}
