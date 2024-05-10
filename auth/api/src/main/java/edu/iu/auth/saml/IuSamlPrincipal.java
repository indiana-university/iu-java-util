package edu.iu.auth.saml;

import edu.iu.auth.IuPrincipalIdentity;

/**
 * Returned by
 * {@link IuSamlSession#authorize(java.net.InetAddress, java.net.URI, String, String)}
 * and {@link IuSamlSession#getPrincipalIdentity()}
 */
public interface IuSamlPrincipal extends IuPrincipalIdentity {

}
