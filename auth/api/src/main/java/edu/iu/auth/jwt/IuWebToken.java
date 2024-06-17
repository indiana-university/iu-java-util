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
package edu.iu.auth.jwt;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuBearerToken;
import edu.iu.auth.spi.IuJwtSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Represents a JSON Web Token (JWT).
 */
public interface IuWebToken extends IuPrincipalIdentity {

	/**
	 * Parses a JWT.
	 * 
	 * <p>
	 * <em>May</em> be encrypted, <em>must</em> be signed. This method validates the
	 * JWT according to the rules outlined in
	 * <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-7.2">RFC-7519
	 * JWT Section 7.2</a>.
	 * </p>
	 * 
	 * @param jwt serialized JWT
	 * @return {@link IuWebToken}
	 */
	static IuWebToken from(String jwt) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).parse(jwt);
	}

	/**
	 * Gets the algorithm used to sign the JWT.
	 * 
	 * @return signing algorithm
	 */
	String getAlgorithm();

	/**
	 * Gets the authentication realm used to verify the issuer's identity and JWS
	 * signature.
	 * 
	 * @return issuer (iss claim)
	 */
	String getIssuer();

	/**
	 * Gets the subject of the JWT.
	 * 
	 * @return subject (sub claim)
	 */
	@Override
	String getName();

	/**
	 * Gets the token identifier.
	 * 
	 * @return token identifier (jti claim);
	 */
	String getTokenId();

	/**
	 * Gets the audience.
	 * 
	 * @return audience (aud claim)
	 */
	Iterable<String> getAudience();

	/**
	 * Gets the time the JWT was issued.
	 * 
	 * @return issued time (iat claim)
	 */
	Instant getIssuedAt();

	/**
	 * Gets the time before which the JWT should not be accepted.
	 * 
	 * @return not before time (nbf claim)
	 */
	Instant getNotBefore();

	/**
	 * Gets the time after which the JWT should not be accepted.
	 * 
	 * @return token expiration time (exp claim)
	 */
	Instant getExpires();

	/**
	 * Gets an extended claim for a basic type. See see IuJsonAdapter#basic() in the
	 * iu.util.client module for a list of supported types
	 * 
	 * @param <T>  claim value type
	 * @param name claim name
	 * @return extended claim value
	 * @see <a href="https://www.iana.org/assignments/jwt/jwt.xhtml">IANA JWT
	 *      Assignments</a>
	 */
	<T> T getClaim(String name);

	/**
	 * Gets an extended claim.
	 * 
	 * @param <T>  claim value type
	 * @param name claim name
	 * @param type claim type; see IuJsonAdapter#of(Type) in the iu.util.client
	 *             module for a list of supported types
	 * @return extended claim value
	 * @see <a href="https://www.iana.org/assignments/jwt/jwt.xhtml">IANA JWT
	 *      Assignments</a>
	 */
	<T> T getClaim(String name, Class<T> type);

	/**
	 * Gets a view of the JWT for as a bearer token.
	 * 
	 * @param scope scope granted
	 * @return {@link IuBearerToken}
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6750">RFC-6750 OAuth
	 *      Bearer Token Usage</a>
	 */
	IuBearerToken asBearerToken(Set<String> scope);

	/**
	 * Gets a view of the JWT as an OAuth authorization grant.
	 * 
	 * @param scope requested scope
	 * @return {@link IuApiCredentials}
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC-7523 OAuth
	 *      JWT Assertion Profiles</a>
	 */
	IuApiCredentials asAuthorizationGrant(Set<String> scope);

	/**
	 * Gets a view of the JWT as an OAuth client assertion.
	 * 
	 * @param tokenParameters token parameters unrelated to passing credentials;
	 *                        {@link IuApiCredentials#applyTo(HttpRequest.Builder)}
	 *                        adds <strong>client_assertion_type</strong> and
	 *                        <strong>client_assertion</strong>, and sets up a form
	 *                        {@link HttpRequest.Builder#POST(BodyPublisher) POST}
	 *                        for use with an OAuth authorization code or client
	 *                        credentials grant.
	 * @return {@link IuApiCredentials}
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC-7523 OAuth
	 *      JWT Assertion Profiles</a>
	 */
	IuApiCredentials asClientAssertion(Map<String, ? extends Iterable<String>> tokenParameters);

}
