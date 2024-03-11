package edu.iu.crypt;

import java.net.URI;
import java.security.cert.X509Certificate;

/**
 * Common super-interface for components that hold a reference to a web
 * certificate and/or chain.
 */
public interface WebCertificateReference {

	/**
	 * Gets the URI where X.509 certificate associated with this key can be
	 * retrieved.
	 * 
	 * <p>
	 * The protocol used to acquire the resource MUST provide integrity protection;
	 * an HTTP GET request to retrieve the certificate MUST use TLS [RFC2818]
	 * [RFC5246]; the identity of the server MUST be validated, as per Section 6 of
	 * RFC 6125 [RFC6125].
	 * </p>
	 * 
	 * @return {@link URI}
	 */
	default URI getCertificateUri() {
		return null;
	}

	/**
	 * Gets the certificate chain.
	 * 
	 * @return parsed JSON x5c attribute value
	 */
	default X509Certificate[] getCertificateChain() {
		return null;
	}

	/**
	 * Gets the certificate thumbprint.
	 * 
	 * @return JSON x5t attribute value
	 */
	default byte[] getCertificateThumbprint() {
		return null;
	}

	/**
	 * Gets the certificate SHA-256 thumbprint.
	 * 
	 * @return JSON x5t#S256 attribute value
	 */
	default byte[] getCertificateSha256Thumbprint() {
		return null;
	}

}
