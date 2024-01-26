package edu.iu.auth.oidc;

/**
 * Provides access to OpenID Provider Configuration.
 */
public interface OpenIDProviderConfiguration {

	/**
	 * Gets the issuer ID.
	 * 
	 * @return Issuer ID
	 */
	String getIssuer();

}
