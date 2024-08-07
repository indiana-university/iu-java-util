/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.iu.auth.config;

import java.net.URI;
import java.util.Set;

import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * OpenID Connect Provider Discovery Metadata.
 * 
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata">OpenID
 *      Connect Discovery 1.0</a>
 */
public interface IuOpenIdProviderMetadata {

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
	 * URL of the OP's UserInfo Endpoint [OpenID.Core]. This URL MUST use the https
	 * scheme and MAY contain port, path, and query parameter components.
	 * 
	 * @return {@link URI}
	 */
	URI getUserinfoEndpoint();

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

	/**
	 * URL of the OP's Dynamic Client Registration Endpoint [OpenID.Registration],
	 * which MUST use the https scheme.
	 * 
	 * @return {@link URI}
	 */
	URI getRegistrationEndpoint();

	/**
	 * JSON array containing a list of the OAuth 2.0 [RFC6749] scope values that
	 * this server supports. The server MUST support the openid scope value. Servers
	 * MAY choose not to advertise some supported scope values even when this
	 * parameter is used, although those defined in [OpenID.Core] SHOULD be listed,
	 * if supported.
	 * 
	 * @return {@link Set} of OAuth 2.0 scope values
	 */
	Set<String> getScopesSupported();

	/**
	 * JSON array containing a list of the OAuth 2.0 response_type values that this
	 * OP supports. Dynamic OpenID Providers MUST support the code, id_token, and
	 * the id_token token Response Type values.
	 * 
	 * @return {@link Set} of OAuth 2.0 response types
	 */
	Set<String> getResponseTypesSupported();

	/**
	 * JSON array containing a list of the OAuth 2.0 response_mode values that this
	 * OP supports, as specified in OAuth 2.0 Multiple Response Type Encoding
	 * Practices [OAuth.Responses]. If omitted, the default for Dynamic OpenID
	 * Providers is ["query", "fragment"].
	 * 
	 * @return {@link Set} of OAuth 2.0 response modes
	 */
	Set<String> getResponseModesSupported();

	/**
	 * JSON array containing a list of the OAuth 2.0 Grant Type values that this OP
	 * supports. Dynamic OpenID Providers MUST support the authorization_code and
	 * implicit Grant Type values and MAY support other Grant Types. If omitted, the
	 * default value is ["authorization_code", "implicit"].
	 * 
	 * @return {@link Set} of OAuth 2.0 grant types
	 */
	Set<String> getGrantTypesSupported();

	/**
	 * JSON array containing a list of the Authentication Context Class Reference
	 * values that this OP supports.
	 * 
	 * @return {@link Set} of ACR values
	 */
	Set<String> getAcrValuesSupported();

	/**
	 * JSON array containing a list of the Subject Identifier types that this OP
	 * supports. Valid types include pairwise and public.
	 * 
	 * @return {@link Set} of subject types
	 */
	Set<String> getSubjectTypesSupported();

	/**
	 * JSON array containing a list of the JWS signing algorithms (alg values)
	 * supported by the OP for the ID Token to encode the Claims in a JWT [JWT]. The
	 * algorithm RS256 MUST be included. The value none MAY be supported but MUST
	 * NOT be used unless the Response Type used returns no ID Token from the
	 * Authorization Endpoint (such as when using the Authorization Code Flow).
	 * 
	 * @return {@link Set} of {@link Algorithm}
	 */
	Set<Algorithm> getIdTokenSigningAlgValuesSupported();

	/**
	 * JSON array containing a list of the JWE encryption algorithms (alg values)
	 * supported by the OP for the ID Token to encode the Claims in a JWT [JWT].
	 * 
	 * @return {@link Set} of {@link Algorithm}
	 */
	Set<Algorithm> getIdTokenEncryptionAlgValuesSupported();

	/**
	 * JSON array containing a list of the JWE encryption algorithms (enc values)
	 * supported by the OP for the ID Token to encode the Claims in a JWT [JWT].
	 * 
	 * @return {@link Set} of {@link Encryption}
	 */
	Set<Encryption> getIdTokenEncryptionEncValuesSupported();

	/**
	 * JSON array containing a list of the JWS [JWS] signing algorithms (alg values)
	 * [JWA] supported by the UserInfo Endpoint to encode the Claims in a JWT [JWT].
	 * The value none MAY be included.
	 * 
	 * @return {@link Set} of {@link Algorithm}
	 */
	Set<Algorithm> getUserinfoSigningAlgValuesSupported();

	/**
	 * JSON array containing a list of the JWE [JWE] encryption algorithms (alg
	 * values) [JWA] supported by the UserInfo Endpoint to encode the Claims in a
	 * JWT [JWT].
	 * 
	 * @return {@link Set} of {@link Algorithm}
	 */
	Set<Algorithm> getUserinfoEncryptionAlgValuesSupported();

	/**
	 * JSON array containing a list of the JWE encryption algorithms (enc values)
	 * [JWA] supported by the UserInfo Endpoint to encode the Claims in a JWT [JWT].
	 * 
	 * @return {@link Set} of {@link Encryption}
	 */
	Set<Encryption> getUserinfoEncryptionEncValuesSupported();

	/**
	 * JSON array containing a list of the JWS signing algorithms (alg values)
	 * supported by the OP for Request Objects, which are described in Section 6.1
	 * of OpenID Connect Core 1.0 [OpenID.Core]. These algorithms are used both when
	 * the Request Object is passed by value (using the request parameter) and when
	 * it is passed by reference (using the request_uri parameter). Servers SHOULD
	 * support none and RS256.
	 * 
	 * @return {@link Set} of {@link Algorithm}
	 */
	Set<Algorithm> getRequestObjectSigningAlgValuesSupported();

	/**
	 * JSON array containing a list of the JWE encryption algorithms (alg values)
	 * supported by the OP for Request Objects. These algorithms are used both when
	 * the Request Object is passed by value and when it is passed by reference.
	 * 
	 * @return {@link Set} of {@link Algorithm}
	 */
	Set<Algorithm> getRequestObjectEncryptionAlgValuesSupported();

	/**
	 * JSON array containing a list of the JWE encryption algorithms (enc values)
	 * supported by the OP for Request Objects. These algorithms are used both when
	 * the Request Object is passed by value and when it is passed by reference.
	 * 
	 * @return {@link Set} of {@link Encryption}
	 */
	Set<Encryption> getRequestObjectEncryptionEncValuesSupported();

	/**
	 * JSON array containing a list of Client Authentication methods supported by
	 * this Token Endpoint. The options are client_secret_post, client_secret_basic,
	 * client_secret_jwt, and private_key_jwt, as described in Section 9 of OpenID
	 * Connect Core 1.0 [OpenID.Core]. Other authentication methods MAY be defined
	 * by extensions. If omitted, the default is client_secret_basic -- the HTTP
	 * Basic Authentication Scheme specified in Section 2.3.1 of OAuth 2.0
	 * [RFC6749].
	 * 
	 * @return {@link Set} of {@link AuthMethod}
	 */
	Set<AuthMethod> getTokenEndpointAuthMethodsSupported();

	/**
	 * JSON array containing a list of the JWS signing algorithms (alg values)
	 * supported by the Token Endpoint for the signature on the JWT [JWT] used to
	 * authenticate the Client at the Token Endpoint for the private_key_jwt and
	 * client_secret_jwt authentication methods. Servers SHOULD support RS256. The
	 * value none MUST NOT be used.
	 * 
	 * @return {@link Set} of {@link Algorithm}
	 */
	Set<Algorithm> getTokenEndpointSigningAlgValuesSupported();

	/**
	 * JSON array containing a list of the Claim Names of the Claims that the OpenID
	 * Provider MAY be able to supply values for. Note that for privacy or other
	 * reasons, this might not be an exhaustive list.
	 * 
	 * @return {@link Set} of supported claims names
	 */
	Set<String> getClaimsSupported();

	/**
	 * JSON array containing a list of the display parameter values that the OpenID
	 * Provider supports. These values are described in Section 3.1.2.1 of OpenID
	 * Connect Core 1.0 [OpenID.Core].
	 * 
	 * @return supported display values
	 */
	Set<String> getDisplayValuesSupported();

	/**
	 * JSON array containing a list of the Claim Types that the OpenID Provider
	 * supports. These Claim Types are described in Section 5.6 of OpenID Connect
	 * Core 1.0 [OpenID.Core]. Values defined by this specification are normal,
	 * aggregated, and distributed. If omitted, the implementation supports only
	 * normal Claims.
	 * 
	 * @return supported claim types
	 */
	Set<String> getClaimTypesSupported();

	/**
	 * URL of a page containing human-readable information that developers might
	 * want or need to know when using the OpenID Provider. In particular, if the
	 * OpenID Provider does not support Dynamic Client Registration, then
	 * information on how to register Clients needs to be provided in this
	 * documentation.
	 * 
	 * @return service documentation {@link URI}
	 */
	URI getServiceDocumentation();

	/**
	 * Languages and scripts supported for values in Claims being returned,
	 * represented as a JSON array of BCP47 [RFC5646] language tag values. Not all
	 * languages and scripts are necessarily supported for all Claim values.
	 * 
	 * @return supported claim locales
	 */
	Set<String> getClaimsLocalesSupported();

	/**
	 * Languages and scripts supported for the user interface, represented as a JSON
	 * array of BCP47 [RFC5646] language tag values.
	 * 
	 * @return supported ui locales
	 */
	Set<String> getUiLocalesSupported();

	/**
	 * Boolean value specifying whether the OP supports use of the claims parameter,
	 * with true indicating support. If omitted, the default value is false.
	 * 
	 * @return true if claims parameter is supported; else false
	 */
	boolean isClaimsParameterSupported();

	/**
	 * Boolean value specifying whether the OP supports use of the request
	 * parameter, with true indicating support. If omitted, the default value is
	 * false.
	 * 
	 * @return true if request parameter is supported; else false
	 */
	boolean isRequestParameterSupported();

	/**
	 * Boolean value specifying whether the OP supports use of the request_uri
	 * parameter, with true indicating support. If omitted, the default value is
	 * true.
	 * 
	 * @return true if request_uri parameter is supported; else false
	 */
	default boolean isRequestUriParameterSupported() {
		return true;
	}

	/**
	 * Boolean value specifying whether the OP requires any request_uri values used
	 * to be pre-registered using the request_uris registration parameter.
	 * Pre-registration is REQUIRED when the value is true. If omitted, the default
	 * value is false.
	 * 
	 * @return true if claims parameter is supported; else false
	 */
	boolean isRequireRequestUriRegistration();

	/**
	 * URL that the OpenID Provider provides to the person registering the Client to
	 * read about the OP's requirements on how the Relying Party can use the data
	 * provided by the OP. The registration process SHOULD display this URL to the
	 * person registering the Client if it is given.
	 * 
	 * @return {@link URI}
	 */
	URI getOpPolicyUri();

	/**
	 * URL that the OpenID Provider provides to the person registering the Client to
	 * read about the OpenID Provider's terms of service. The registration process
	 * SHOULD display this URL to the person registering the Client if it is given.
	 * 
	 * @return {@link URI}
	 */
	URI getOpTosUri();

}
