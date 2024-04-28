package iu.auth.principal;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;

/**
 * Verifies a principal as valid for a realm.
 * 
 * @param <I> principal identity type
 */
public interface PrincipalVerifier<I extends IuPrincipalIdentity> {

	/**
	 * Gets the identity type.
	 * 
	 * @return identity type; must be a final implementation class
	 */
	Class<I> getType();

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	String getRealm();

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
	 * @throws IuAuthenticationException If the principal could not be verified
	 */
	void verify(I id, String realm) throws IuAuthenticationException;

}
