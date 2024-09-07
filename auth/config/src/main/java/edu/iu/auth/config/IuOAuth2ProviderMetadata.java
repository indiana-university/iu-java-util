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

/**
 * Provides metadata for an OAuth 2.0 server that issues JWT access and OPTIONAL
 * refresh tokens with a well-known keyset, but doesn't support the full OpenID
 * Connect Core self-issued provider protocol.
 */
public interface IuOAuth2ProviderMetadata {

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
