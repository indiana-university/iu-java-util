package edu.iu.auth.saml;

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
	 * Gets the maximum time interval to re-established metadata resolver 
	 * typically measured in seconds. Once this interval is passed,
	 * metadata resolver will be re-established using metadata URLs.
	 * 
	 * return metadaaTtl {@link Duration}
	 */
	default Duration getMetadataTtl() {
		return Duration.ofMillis(300000L);
	}

	/**
	 * Gets the SAML metadata URL to retrieve configure metadata file that is configured directly 
	 * into the SAML provider by administrator 
	 * @return metadata URL
	 */
	default List<URI> getMetaDataUrls() {
		return null;
	}

	/**
	 * Gets the SAML identity provider {@link URI} to configure Metadata resolver
	 * @return SAML identity provider URL
	 */
	default URI getIdentityProviderURL() {
		return null;
	}

	/**
	 * Gets the list of assertion Consumer urls 
	 * @return allowed list of assertion consumer urls
	 */
	List<URI> getAcsUrls();

	/**
	 * Gets the unique service provider id that register with identity provider
	 * @return unique service provide id
	 */
	String getServiceProviderEntityId();


	/**
	 * 
	 * @return
	 */
	X509Certificate getCertificate();

	/**
	 * 
	 * @return
	 */

	String getPrivateKey();
}
