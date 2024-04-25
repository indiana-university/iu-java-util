package iu.auth.principal;

import edu.iu.UnsafeBiConsumer;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;

/**
 * Verifies a principal as valid for a realm.
 */
public interface PrincipalVerifier extends UnsafeBiConsumer<IuPrincipalIdentity, String> {

	/**
	 * Determines if this verifier is authoritative for the realm.
	 * 
	 * @return true if the identity principal is managed by the authorization
	 *         module, or verifiable through an established trust relationship with
	 *         a remote authentication provider; false verification is based solely
	 *         on an implicit trust relationship based on well-known information
	 *         about the authentication provider.
	 */
	boolean isAuthoritative();

	/**
	 * Verifies a principal identity.
	 * 
	 * @param id    principal identity
	 * @param realm authentication realm
	 * @throws IuAuthenticationException If the principal is well formed, but
	 *                                   invalid for the authentication realm.
	 */
	@Override
	void accept(IuPrincipalIdentity id, String realm) throws IuAuthenticationException;

}
