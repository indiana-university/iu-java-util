package edu.iu.auth.oidc;

import java.util.Map;

import edu.iu.auth.IuPrincipalIdentity;

/**
 * Represents a principal identity authenticated by an OpenID provider.
 */
public interface IuOpenIdPrincipal extends IuPrincipalIdentity {

	/**
	 * Gets verified OpenID Claims.
	 * 
	 * @return verified OpenID Claims.
	 */
	Map<String, ?> getClaims();

}
