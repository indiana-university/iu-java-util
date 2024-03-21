package edu.iu.crypt;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;

/**
 * Unifies algorithm support and maps cryptographic header data from JCE to JSON
 * Object Signing and Encryption (JOSE).
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">JSON Web Key
 *      (JWK) RFC-7517</a>
 */
public interface WebCryptoHeader extends WebCertificateReference {

	/**
	 * Enumerates standard header parameters.
	 */
	enum Param {
		/**
		 * Encryption/signature algorithm.
		 */
		ALGORITHM("alg", EnumSet.allOf(Use.class), WebCryptoHeader::getAlgorithm),

		/**
		 * Content encryption algorithm.
		 */
		ENCRYPTION("enc", EnumSet.of(Use.ENCRYPT), a -> a.getExtendedParameters().get("enc")),

		/**
		 * Plain-text compression algorithm for encryption.
		 */
		ZIP("zip", EnumSet.of(Use.ENCRYPT), a -> a.getExtendedParameters().get("zip")),

		/**
		 * Well-known key identifier.
		 */
		KEY_ID("kid", EnumSet.allOf(Use.class), WebCryptoHeader::getKeyId),

		/**
		 * Well-known key set URI.
		 */
		KEY_SET_URI("jku", EnumSet.allOf(Use.class), WebCryptoHeader::getKeySetUri),

		/**
		 * Well-known public key.
		 */
		KEY("jwk", EnumSet.allOf(Use.class), WebCryptoHeader::getKey),

		/**
		 * Certificate chain URI.
		 */
		CERTIFICATE_URI("x5u", EnumSet.allOf(Use.class), WebCryptoHeader::getCertificateUri),

		/**
		 * Certificate chain.
		 */
		CERTIFICATE_CHAIN("x5c", EnumSet.allOf(Use.class), WebCryptoHeader::getCertificateChain),

		/**
		 * Certificate SHA-1 thumb print.
		 */
		CERTIFICATE_THUMBPRINT("x5t", EnumSet.allOf(Use.class), WebCryptoHeader::getCertificateThumbprint),

		/**
		 * Certificate SHA-1 thumb print.
		 */
		CERTIFICATE_SHA256_THUMBPRINT("x5t#S256", EnumSet.allOf(Use.class),
				WebCryptoHeader::getCertificateSha256Thumbprint),

		/**
		 * Signature/encryption media type.
		 */
		TYPE("typ", EnumSet.allOf(Use.class), WebCryptoHeader::getType),

		/**
		 * Content media type.
		 */
		CONTENT_TYPE("cty", EnumSet.allOf(Use.class), WebCryptoHeader::getContentType),

		/**
		 * Extended parameter names that <em>must</em> be included in the protected
		 * header.
		 */
		CRITICAL_PARAMS("crit", EnumSet.allOf(Use.class), WebCryptoHeader::getCriticalExtendedParameters);

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

		/**
		 * Determines if the parameter name is registered for use with <a href=
		 * "https://datatracker.ietf.org/doc/html/rfc7515#section-4.1">signature</a>
		 * and/or <a href=
		 * "https://datatracker.ietf.org/doc/html/rfc7515#section-4.1">encryption</a>.
		 */
		private final Set<Use> use;

		private final Function<WebCryptoHeader, ?> get;

		private Param(String name, Set<Use> use, Function<WebCryptoHeader, ?> get) {
			this.name = name;
			this.get = get;
			this.use = Collections.unmodifiableSet(use);
		}

		/**
		 * Verifies that the header contains a non-null value.
		 * 
		 * @param header header
		 * @return true if the value is present; else false
		 */
		public boolean isPresent(WebCryptoHeader header) {
			return get.apply(header) != null;
		}

		/**
		 * Determines if a header applies to a public JWK use case.
		 * 
		 * @param use public key use
		 * @return true if the header parameter is registered for the public JWK use
		 *         case.
		 */
		public boolean isUsedFor(Use use) {
			return this.use.contains(use);
		}

		/**
		 * Gets the header value.
		 * 
		 * @param header header
		 * @return header value
		 */
		public Object get(WebCryptoHeader header) {
			return get.apply(header);
		}
	}

	/**
	 * Builder interface for creating {@link WebCryptoHeader} instances.
	 * 
	 * @param <B> builder type
	 */
	interface Builder<B extends Builder<B>> {

		/**
		 * Sets the cryptographic algorithm.
		 * 
		 * @param algorithm {@link Algorithm}
		 * @return this
		 */
		B algorithm(Algorithm algorithm);

		/**
		 * Sets the key ID relative to {@link #getKeySetUri()} corresponding to a JWKS
		 * key entry.
		 *
		 * @param keyId key ID
		 * @return this
		 */
		B id(String keyId);

		/**
		 * Sets the URI where JWKS well-known key data can be retrieved.
		 * 
		 * @param uri JWKS {@link URI}
		 * @return this
		 */
		B jwks(URI uri);

		/**
		 * Sets a key to use for creating the signature or encryption.
		 * 
		 * <p>
		 * This key will not be included in the serialized output. This is the same as
		 * calling {@link #jwk(WebKey, boolean) jwk(key, false)}.
		 * </p>
		 * 
		 * @param key
		 * @return this
		 */
		default B jwk(WebKey key) {
			return jwk(key, false);
		}

		/**
		 * Sets the key data for this header.
		 * 
		 * <p>
		 * This key may contain private and/or symmetric key data to be used for
		 * creating digital signatures or facilitating key agreement for encryption.
		 * However, only public keys and certificate data will be included in the
		 * serialized headers.
		 * </p>
		 * 
		 * @param key    {@link WebKey}, provided by the same module as this builder.
		 * @param silent true to omit all key data from the serialized header; false to
		 *               include public keys and/or certificates only.
		 * @return this
		 */
		B jwk(WebKey key, boolean silent);

		/**
		 * Sets the header type parameter value.
		 * 
		 * @param type header type parameter value.
		 * @return this
		 */
		B type(String type);

		/**
		 * Sets the header type parameter value.
		 * 
		 * @param contentType header type parameter value.
		 * @return this
		 */
		B contentType(String contentType);

		/**
		 * Sets a critical extended parameter, to be required as understood by the
		 * recipient and included in the protected header.
		 * 
		 * @param name  parameter name
		 * @param value parameter value, must be convertible to JSON without type
		 *              introspection (see {@link IuJson#toJson(Object)}.
		 * @return this
		 */
		B crit(String name, Object value);

		/**
		 * Sets an optional extended parameter.
		 * 
		 * @param name  parameter name
		 * @param value parameter value, must be convertible to JSON without type
		 *              introspection (see {@link IuJson#toJson(Object)}.
		 * @return this
		 */
		B ext(String name, Object value);
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
	String getKeyId();

	/**
	 * Gets the URI where JWKS well-known key data can be retrieved.
	 * 
	 * @return {@link URI}
	 */
	URI getKeySetUri();

	/**
	 * Gets the well-known key data.
	 * 
	 * @return {@link WebKey}, <em>should</em> be {@link WebKey#wellKnown(WebKey)}
	 *         if provided.
	 */
	WebKey getKey();

	/**
	 * Gets the header type parameter value.
	 * 
	 * @return header type parameter value.
	 */
	String getType();

	/**
	 * Gets the header type parameter value.
	 * 
	 * @return header type parameter value.
	 */
	String getContentType();

	/**
	 * Gets the set of critical extended parameter names.
	 * 
	 * @return critical extended parameter names; <em>may</em> be null if no
	 *         extended parameters are critical, <em>must not</em> be empty.
	 */
	Set<String> getCriticalExtendedParameters();

	/**
	 * Gets extended parameters.
	 * 
	 * @return extended parameters
	 */
	Map<String, ?> getExtendedParameters();

}
