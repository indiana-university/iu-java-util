package edu.iu.auth;

import java.net.InetAddress;

import edu.iu.IuWebUtils;
import edu.iu.auth.spi.IuNonceSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * One-time number (nonce) engine.
 *
 * <img src="doc-files/Nonce.svg" alt="UML Sequence Diagram">
 * 
 * <p>
 * Provides securely generated one-time numbers. Clients <em>should</em>
 * optimistically limit concurrent access to a single thread. Single-use
 * tracking is performed internally:
 * </p>
 * 
 * <ul>
 * <li><strong>Nonce</strong> values <em>must</em> be used within the configured
 * time to live interval. PT15S is <em>recommended</em> as a default value.</li>
 * <li>Creating a new <strong>nonce</strong> value causes all previously created
 * <strong>nonce</strong> values for the same client to expire if generated more
 * than PT0.25S in the past.</li>
 * <li>Client is thumbprinted by
 * {@code sha256(utf8(remoteAddr || userAgent))}</li>
 * <li>remoteAddr is resolved by {@link IuWebUtils#getInetAddress(String)} then
 * canonicalized with {@link InetAddress#getAddress()}</li>
 * <li>userAgent is {@link IuWebUtils#validateUserAgent(String) validated}</li>
 * <li>When pruning stale <strong>nonce</strong> challenges, 25ms artificial
 * delay is inserted to prevent excessive use</li>
 * <li>Regardless of validation status, a <strong>nonce</strong> value
 * <em>may</em> only be used once.</li>
 * </ul>
 */
public interface IuOneTimeNumber extends AutoCloseable {

	/**
	 * Initializes a new one-time number generator.
	 * 
	 * @param config configuration properties
	 * @return {@link IuOneTimeNumber}
	 */
	static IuOneTimeNumber initialize(IuOneTimeNumberConfig config) {
		return IuAuthSpiFactory.get(IuNonceSpi.class).initialize(config);
	}

	/**
	 * Creates a one-time number (nonce) value.
	 * 
	 * @param remoteAddress textual representation of the client IP address
	 * @param userAgent     user agent string
	 * @return one-time number
	 */
	String create(String remoteAddress, String userAgent);

	/**
	 * Validates a one-time number (nonce) value.
	 * 
	 * @param remoteAddress textual representation of the client IP address
	 * @param userAgent     user agent string
	 * @param nonce         one-time number
	 */
	void validate(String remoteAddress, String userAgent, String nonce);

}
