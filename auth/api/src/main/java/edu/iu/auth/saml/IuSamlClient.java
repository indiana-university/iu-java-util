package edu.iu.auth.saml;

import java.net.InetAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

/**
 * Provides client configuration metadata for interacting with an SAML authorization
 * server.
 * 
 * <p>
 * The interface <em>should</em> be implemented by the application client module
 * requiring authorization on behalf of an SAML identity provider.
 * </p>
 */
public interface IuSamlClient {
	//	@Resource
	// idp url env basis
	//	private String[] metadataUrls = new String[0];
	//	@Resource
	//	private long metadataTtl = 300000L;
	//	@Resource
	//	private String entityId;
	//	@Resource
	//	private String[] acsUrls;
	//	@Resource
	//	private String privateKey;
	//	@Resource
	//	private String certificate;
	//	@Resource
	//	private String oldCertificate = "";
	//	@Resource
	//	private boolean failOnAddressMismatch;

	// id

	/**
	 * 
	 */
	default boolean failOnAddressMismatch() {
		return false;
	}
	/**
	 * Gets allowed list of IP addresses to validate against SAML response
	 * @return allowed ranged of IP addresses
	 */
	List<InetAddress> getAllowedRange();

	/**
	 * Gets the maximum time interval to re-established metadata resolver 
	 * typically measured in seconds. Once this interval is passed,
	 * metadata resolver will be re-established using metadata URLs.
	 * 
	 * @return metadaaTtl {@link Duration}
	 */
	default Duration getMetadataTtl() {
		return Duration.ofMillis(300000L);
	}

	/**
	 * Gets the SAML metadata {@link URI} to retrieve configure metadata file that is configured directly 
	 * into the SAML provider by administrator 
	 * @return metadata URL
	 */
	List<URI> getMetaDataUris();
	
	/**
	 * Gets the SAML identity provider {@link URI} to configure Metadata resolver
	 * @return SAML identity provider URL
	 */
	default URI getIdentityProviderUri() {
		return null;
	}

	/**
	 * Gets the list of assertion Consumer {@link URI} 
	 * @return allowed list of assertion consumer {@link URI}
	 */
	List<URI> getAcsUris();

	/**
	 * Gets the unique service provider id that register with identity provider
	 * @return unique service provide id
	 */
	String getServiceProviderEntityId();


	/**
	 * Gets the X.509 certificate to validate SAML response
	 * that came from identity provider by decrypt the signature,
	 * using the public key on the X.509 certificate, and checking if the values match.
	 * @return certificate
	 */
	X509Certificate getCertificate();

	/**
	 * Gets the private key to validate SAML response that came from identity provider by decrypt the
	 * X.509 certificate using private key and match the values
	 * 
	 * @return private key
	 */

	String getPrivateKey();
}
