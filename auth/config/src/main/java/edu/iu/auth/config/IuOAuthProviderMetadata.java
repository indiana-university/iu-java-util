package edu.iu.auth.config;

import java.net.URI;

/**
 * Encapsulates OAuth provider metadata, either published publicly or provided
 * by configuration.
 */
public interface IuOAuthProviderMetadata {

	/**
	 * Gets root URIs for all API endpoints expected to trust tokens issued by the
	 * provider.
	 * 
	 * @return API endpoint URIs
	 */
	Iterable<URI> getEndpointUris();

	/**
	 * URL using the https scheme with no query or fragment components that the OP
	 * asserts as its Issuer Identifier. If Issuer discovery is supported (see
	 * Section 2), this value MUST be identical to the issuer value returned by
	 * WebFinger. This also MUST be identical to the iss Claim value in ID Tokens
	 * issued from this Issuer.
	 * 
	 * @return {@link URI}
	 */
	URI getIssuer();

	/**
	 * URL of the OP's OAuth 2.0 Authorization Endpoint [OpenID.Core]. This URL MUST
	 * use the https scheme and MAY contain port, path, and query parameter
	 * components.
	 * 
	 * @return {@link URI}
	 */
	URI getAuthorizationEndpoint();

	/**
	 * URL of the OP's OAuth 2.0 Token Endpoint [OpenID.Core]. This is REQUIRED
	 * unless only the Implicit Flow is used. This URL MUST use the https scheme and
	 * MAY contain port, path, and query parameter components.
	 * 
	 * @return {@link URI}
	 */
	URI getTokenEndpoint();

	/**
	 * URL of the OP's JWK Set [JWK] document, which MUST use the https scheme. This
	 * contains the signing key(s) the RP uses to validate signatures from the OP.
	 * The JWK Set MAY also contain the Server's encryption key(s), which are used
	 * by RPs to encrypt requests to the Server. When both signing and encryption
	 * keys are made available, a use (public key use) parameter value is REQUIRED
	 * for all keys in the referenced JWK Set to indicate each key's intended usage.
	 * Although some algorithms allow the same key to be used for both signatures
	 * and encryption, doing so is NOT RECOMMENDED, as it is less secure. The JWK
	 * x5c parameter MAY be used to provide X.509 representations of keys provided.
	 * When used, the bare key values MUST still be present and MUST match those in
	 * the certificate. The JWK Set MUST NOT contain private or symmetric key
	 * values.
	 * 
	 * @return {@link URI}
	 */
	URI getJwksUri();

}
