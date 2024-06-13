package edu.iu.auth.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import edu.iu.crypt.WebKey;

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
	default boolean isFailOnAddressMismatch() {
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
		return Duration.ofMinutes(5L);
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
	Iterable<String> getAllowedRange();

	/**
	 * Gets the SAML metadata {@link URI} to retrieve configure metadata file that
	 * is configured directly into the SAML provider by administrator
	 * 
	 * @return metadata URL
	 */
	Iterable<URI> getMetaDataUris();

	/**
	 * Gets the list of assertion Consumer {@link URI}
	 * 
	 * @return allowed list of assertion consumer {@link URI}
	 */
	Iterable<URI> getAcsUris();

	/**
	 * Gets the unique service provider id that register with identity provider
	 * 
	 * @return unique service provider id
	 */
	String getServiceProviderEntityId();

	/**
	 * Gets the private key and X.509 certificate used to decrypt SAML responses and
	 * assertions from the identity provider.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getEncryptionKey();

}
