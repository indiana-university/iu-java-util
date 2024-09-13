/**
 * Low-level web cryptography API module.
 * 
 * <p>
 * Provides interfaces for:
 * </p>
 * <ul>
 * <li><a href="https://datatracker.ietf.org/doc/html/rfc7515">RFC-7515 JSON Web
 * Signature (JWS)</a></li>
 * <li><a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC-7516 JSON Web
 * Encryption (JWE)</a></li>
 * <li><a href="https://datatracker.ietf.org/doc/html/rfc7517">RFC-7517 JSON Web
 * Key (JWK)</a></li>
 * <li><a href="https://datatracker.ietf.org/doc/html/rfc7518">RFC-7518 JSON Web
 * Algorithms (JWA)</a></li>
 * </ul>
 * 
 * @uses iu.crypt.spi.IuCryptSpi For methods requiring HTTP and/or JSON
 *       processing capabilities
 */
module iu.util.crypt {
	exports edu.iu.crypt;
	exports iu.crypt.spi to iu.util.crypt.impl;

	requires iu.util;

	uses iu.crypt.spi.IuCryptSpi;
}
