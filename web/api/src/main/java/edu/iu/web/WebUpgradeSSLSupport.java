
package edu.iu.web;

import java.io.IOException;
import java.security.cert.X509Certificate;

/**
 * Interface for providing SSL support during a web upgrade.
 */
public interface WebUpgradeSSLSupport {

	/**
	 * Gets the cipher suite used for the SSL connection.
	 *
	 * @return the cipher suite
	 * @throws IOException if an I/O error occurs
	 */
	String getCipherSuite() throws IOException;

	/**
	 * Gets the peer certificate chain for the SSL connection.
	 *
	 * @return an array of X509 certificates
	 * @throws IOException if an I/O error occurs
	 */
	X509Certificate[] getPeerCertificateChain() throws IOException;

	/**
	 * Gets the key size used for the SSL connection.
	 *
	 * @return the key size
	 * @throws IOException if an I/O error occurs
	 */
	Integer getKeySize() throws IOException;

	/**
	 * Gets the session ID for the SSL connection.
	 *
	 * @return the session ID
	 * @throws IOException if an I/O error occurs
	 */
	String getSessionId() throws IOException;

	/**
	 * Gets the protocol used for the SSL connection.
	 *
	 * @return the protocol
	 * @throws IOException if an I/O error occurs
	 */
	String getProtocol() throws IOException;

	/**
	 * Gets the requested protocols for the SSL connection.
	 *
	 * @return the requested protocols
	 * @throws IOException if an I/O error occurs
	 */
	String getRequestedProtocols() throws IOException;

	/**
	 * Gets the requested ciphers for the SSL connection.
	 *
	 * @return the requested ciphers
	 * @throws IOException if an I/O error occurs
	 */
	String getRequestedCiphers() throws IOException;

}
