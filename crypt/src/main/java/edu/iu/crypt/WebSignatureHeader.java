package edu.iu.crypt;

import java.net.URI;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import edu.iu.IuObject;
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
	 * Enumerates standard header parameters.
	 */
	enum Param {
		/**
		 * Encryption/signature algorithm.
		 */
		ALGORITHM("alg", WebSignatureHeader::getAlgorithm),

		/**
		 * Well-known key identifier.
		 */
		KEY_ID("kid", WebSignatureHeader::getKeyId),

		/**
		 * Well-known key set URI.
		 */
		KEY_SET_URI("jku", WebSignatureHeader::getKeySetUri),

		/**
		 * Well-known public key.
		 */
		KEY("jwk", WebSignatureHeader::getKey),

		/**
		 * Certificate chain URI.
		 */
		CERTIFICATE_URI("x5u", WebSignatureHeader::getCertificateUri),

		/**
		 * Certificate chain.
		 */
		CERTIFICATE_CHAIN("x5c", WebSignatureHeader::getCertificateChain),

		/**
		 * Certificate SHA-1 thumb print.
		 */
		CERTIFICATE_THUMBPRINT("x5t", WebSignatureHeader::getCertificateThumbprint),

		/**
		 * Certificate SHA-1 thumb print.
		 */
		CERTIFICATE_SHA256_THUMBPRINT("x5t#S256", WebSignatureHeader::getCertificateSha256Thumbprint),

		/**
		 * Signature/encryption media type.
		 */
		TYPE("typ", WebSignatureHeader::getType),

		/**
		 * Content media type.
		 */
		CONTENT_TYPE("cty", WebSignatureHeader::getContentType),

		/**
		 * Encryption type.
		 */
		ENCRYPTION("enc", h -> {
			if (h instanceof WebEncryptionHeader)
				return ((WebEncryptionHeader) h).getEncryption();
			else
				return null;
		}),

		/**
		 * Encrypted payload compression type.
		 */
		DEFALATE("zip", h -> {
			if ((h instanceof WebEncryptionHeader) //
					&& ((WebEncryptionHeader) h).isDeflate())
				return "DEF";
			else
				return null;
		}),

		/**
		 * Extended parameter names that <em>must</em> be included in the protected
		 * header.
		 */
		CRITICAL_PARAMS("crit", WebSignatureHeader::getCriticalExtendedParameters);

		/**
		 * Gets a parameter by JOSE standard parameter name.
		 * 
		 * @param name JOSE standard name
		 * @return {@link Param}
		 */
		public static Param from(String name) {
			return EnumSet.allOf(Param.class).stream().filter(a -> IuObject.equals(name, a.name)).findFirst()
					.orElse(null);
		}

		/**
		 * JOSE standard parameter name.
		 */
		public final String name;

		private final Function<WebSignatureHeader, ?> get;

		private Param(String name, Function<WebSignatureHeader, ?> get) {
			this.name = name;
			this.get = get;
		}

		/**
		 * Verifies that the header contains a non-null value.
		 * 
		 * @param header header
		 * @return true if the value is present; else false
		 */
		public boolean isPresent(WebSignatureHeader header) {
			return get.apply(header) != null;
		}

		/**
		 * Gets the header value.
		 * 
		 * @param header header
		 * @return header value
		 */
		public Object get(WebSignatureHeader header) {
			return get.apply(header);
		}
	}

	/**
	 * Gets the cryptographic algorithm.
	 * 
	 * @return {@link Algorithm}
	 */
	Algorithm getAlgorithm();

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
