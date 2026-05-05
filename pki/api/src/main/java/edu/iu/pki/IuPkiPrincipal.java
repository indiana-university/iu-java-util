package edu.iu.pki;

import java.security.Principal;

import edu.iu.crypt.WebKey;

/**
 * Principal backed by a {@link WebKey} with a verifiable X.509 certificate
 * chain.
 */
public interface IuPkiPrincipal extends Principal {

	/**
	 * Gets the {@link WebKey} credentials backing this principal.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getJwk();

}
