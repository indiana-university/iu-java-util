package edu.iu.auth.saml;

import java.net.URI;
import java.time.Duration;

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
	
	
	void authorize();


	/**
	 * Gets the maximum time interval to re-established metadata resolver 
	 * typically measured in seconds. Once this interval is passed,
	 * metadata resolver will be re-established using metadata URLs.
	 * 
	 * return metadaaTtl {@link Duration}
	 */
    default Duration getMetadataTtl() {
		return null;
    	
    }
    
    /**
     * Gets the SAML metadata URL to retrieve configure metadata file that is configured directly 
     * into the SAML provider by administrator 
     * @return metadata URL
     */
	default String[] getMetaDataUrls() {
		return null;
	}
	
	/**
	 * Gets the application {@link URI} to configure post SAML URI.
	 * 
	 * @return application URL {@link URI}
	 */
	default URI getApplicationUrl() {
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
	 * Gets the issuer globally-unique identifier register with identity provider
	 * @return issuer
	 */
	default String getIssuer() {
		return null;
	}
}
