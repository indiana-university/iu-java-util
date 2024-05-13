package edu.iu.auth.saml;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

/**
 * Provides client configuration metadata for interacting with an SAML
 * authorization server.
 * 
 * <p>
 * The interface <em>should</em> be implemented by the application client module
 * requiring authorization on behalf of an SAML identity provider.
 * </p>
 */
public interface IuSamlClient {

	/**
	 * Gets whether to fail on address mismatch or not, true if required, false if
	 * not
	 * 
	 * @return failed on address mismatch
	 */
	default boolean failOnAddressMismatch() {
		return false;
	}
	
	
	/**
	 * Gets the maximum length of time to allow an authenticated session to be
	 * remain active before requesting the provide re-establish credentials for the
	 * principal.
	 * 
	 * @return {@link Duration}, will be truncated to second
	 */
	default Duration getAuthenticatedSessionTimeout() {
		return Duration.ofHours(12L);
	}
	
	/**
	 * Gets the maximum time interval to re-established metadata resolver typically
	 * measured in seconds. Once this interval is passed, metadata resolver will be
	 * re-established using metadata URIs.
	 * 
	 * @return metadaaTtl {@link Duration}
	 */
	default Duration getMetadataTtl() {
		return Duration.ofMillis(300000L);
	}
	
	/**
	 * Gets the SAML identity provider {@link URI} to configure Metadata resolver
	 * 
	 * @return SAML identity provider URL
	 */
	default URI getIdentityProviderUri() {
		return null;
	}
	
	/**
	 * Gets the root resource URI covered by this client's protection domain.
	 * 
	 * <p>
	 * All client-side application URIs used with this client <em>must</em> begin
	 * with this URI. The root resource URI <em>should</em> end with a '/' character
	 * unless the client is only intended to protect a single URI.
	 * </p>
	 * 
	 * @return {@link URI}
	 */
	URI getApplicationUri();

	/**
	 * Gets allowed list of IP addresses to validate against SAML response
	 * 
	 * @return allowed ranged of IP addresses
	 */
	List<String> getAllowedRange();

	
	/**
	 * Gets the SAML metadata {@link URI} to retrieve configure metadata file that
	 * is configured directly into the SAML provider by administrator
	 * 
	 * @return metadata URL
	 */
	List<URI> getMetaDataUris();

	/**
	 * Gets the list of assertion Consumer {@link URI}
	 * 
	 * @return allowed list of assertion consumer {@link URI}
	 */
	List<URI> getAcsUris();

	/**
	 * Gets the unique service provider id that register with identity provider
	 * 
	 * @return unique service provide id
	 */
	String getServiceProviderEntityId();

	/**
	 * Gets the X.509 certificate to validate SAML response that came from identity
	 * provider by decrypt the signature, using the public key on the X.509
	 * certificate, and checking if the values match.
	 * 
	 * @return certificate
	 */
	X509Certificate getCertificate();

	/**
	 * Gets the private key to validate SAML response that came from identity
	 * provider by decrypt the X.509 certificate using private key and match the
	 * values
	 * 
	 * @return private key
	 */

	String getPrivateKey();

}
