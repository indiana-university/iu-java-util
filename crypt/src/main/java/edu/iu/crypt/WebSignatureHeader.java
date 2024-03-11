package edu.iu.crypt;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import edu.iu.crypt.WebKey.Algorithm;

/**
 * Unifies algorithm support and maps cryptographic header data from JCE to JSON
 * Object Signing and Encryption (JOSE).
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">JSON Web Key
 *      (JWK) RFC-7517</a>
 */
public interface WebSignatureHeader extends WebCertificateReference {

	/**
	 * Gets the cryptographic algorithm.
	 * 
	 * @return {@link Algorithm}
	 */
	Algorithm getAlgorithm();

	/**
	 * Gets the protected header parameter names.
	 * 
	 * @return protected header names
	 */
	default Set<String> getProtectedParameters() {
		final Set<String> p = new LinkedHashSet<>();
		p.add("alg");
		if (WebSignatureHeader.this instanceof WebEncryptionHeader)
			p.add("enc");
		if (getKeyId() != null)
			p.add("kid");
		if (getType() != null)
			p.add("typ");
		if (getContentType() != null)
			p.add("cty");

		final var crit = getCriticalExtendedParameters();
		if (crit != null)
			p.addAll(crit);

		return p;
	}

	/**
	 * Gets the key ID relative to {@link #getKeySetUri()} corresponding to a JWKS
	 * key entry.
	 * 
	 * @return key ID
	 */
	default String getKeyId() {
		return null;
	}

	/**
	 * Gets the URI where JWKS well-known key data can be retrieved.
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
	default URI getKeySetUri() {
		return null;
	}

	/**
	 * Gets the well-known key data.
	 * 
	 * @return {@link WebKey}, <em>should</em> be {@link WebKey#wellKnown(WebKey)}
	 *         if provided.
	 */
	default WebKey getKey() {
		return null;
	}

	/**
	 * Gets the header type parameter value.
	 * 
	 * @return header type parameter value.
	 */
	default String getType() {
		return null;
	}

	/**
	 * Gets the header type parameter value.
	 * 
	 * @return header type parameter value.
	 */
	default String getContentType() {
		return null;
	}

	/**
	 * Gets the set of critical extended parameter names.
	 * 
	 * @return critical extended parameter names; <em>may</em> be null if no
	 *         extended parameters are critical, <em>must not</em> be empty.
	 */
	default Set<String> getCriticalExtendedParameters() {
		return null;
	}

	/**
	 * Gets extended parameters.
	 * 
	 * @return extended parameter names
	 */
	default Map<String, ?> getExtendedParameters() {
		return null;
	}

}
