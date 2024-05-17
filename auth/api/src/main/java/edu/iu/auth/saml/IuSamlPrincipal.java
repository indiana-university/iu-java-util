package edu.iu.auth.saml;

import java.util.Map;

import edu.iu.auth.IuPrincipalIdentity;

/**
 * Returned by
 * {@link IuSamlSession#authorize(java.net.InetAddress, java.net.URI, String, String)}
 * and {@link IuSamlSession#getPrincipalIdentity()}
 */
public interface IuSamlPrincipal extends IuPrincipalIdentity {
	/**
	 * Gets SAML Claims.
	 * 
	 * @return SAML Claims.
	 */
	Map<String, ?> getClaims();

	/**
	 * Gets principal display name
	 * 
	 * @return principal display name
	 */
	String getDisplayName();

	/**
	 * Gets the principal email address
	 * 
	 * @return principal email address
	 */
	String getEmailAddress();
}
