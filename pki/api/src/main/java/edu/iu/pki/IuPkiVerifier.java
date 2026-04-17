package edu.iu.pki;

import edu.iu.crypt.WebKey;

/**
 * Public interface for resources capable of verifying a {@link WebKey} as
 * containing a trusted certificate chain.
 */
public interface IuPkiVerifier {

	/**
	 * Verifies a {@link WebKey} as containing a trusted certificate chain.
	 * 
	 * @param jwk {@link WebKey}
	 * @throws IllegalArgumentException if the certificate is invalid; the cause
	 *                                  should include further detail on the failure
	 */
	void verify(WebKey jwk) throws IllegalArgumentException;
}
