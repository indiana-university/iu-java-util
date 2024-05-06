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
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.XECPublicKey;
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
	 * Builder interface for issuing a JWT signed by an authoritative principal with
	 * an applicable private key.
	 */
	interface Builder {

		/**
		 * Sets the subject.
		 * 
		 * @param sub   subject principal; <em>must</em> be
		 *              {@link IuPrincipalIdentity#verify(IuPrincipalIdentity, String)
		 *              verifiable as authoritative} for the authentication realm
		 * @param realm authentication realm
		 * @return this
		 */
		Builder subject(IuPrincipalIdentity sub, String realm);

		/**
		 * Sets the audience.
		 * 
		 * @param aud   audience principal; <em>must</em> be
		 *              {@link IuPrincipalIdentity#verify(IuPrincipalIdentity, String)
		 *              verifiable} for the authentication realm; <em>may</em>
		 *              non-authoritative. If the
		 *              {@link IuPrincipalIdentity#getSubject()} includes a public key
		 *              designated with <strong>use</strong> = "enc", and/or
		 *              <strong>key_op</strong> including "wrapKey" or "deriveKey" and
		 *              only one audience is provided to the builder, the JWT will be
		 *              encrypted.
		 * @param realm authentication realm
		 * @return this
		 * @see #encrypt(String)
		 */
		Builder audience(IuPrincipalIdentity aud, String realm);

		/**
		 * Sets the time before which the JWT should not be accepted.
		 * 
		 * @param nbf not before time
		 * @return this
		 */
		Builder notBefore(Instant nbf);

		/**
		 * Sets the time after which the JWT should not be accepted.
		 * 
		 * @param exp not before time
		 * @return this
		 */
		Builder expires(Instant exp);

		/**
		 * Sets an extended claim value.
		 * 
		 * <p>
		 * <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1">RFC-7519
		 * JWT Registered Claims</a> are not included. Public claim names registered
		 * with IANA <em>should</em> be used in accordance with linked specifications.
		 * </p>
		 * 
		 * @param name  claim name
		 * @param value claim value
		 * @return this
		 * @see <a href="https://www.iana.org/assignments/jwt/jwt.xhtml">IANA JWT
		 *      Assignments</a>
		 */
		Builder claim(String name, Object value);

		/**
		 * Requires a single {@link #audience(IuPrincipalIdentity, String) audience}
		 * principal that includes a public key, and sets content encryption algorithm
		 * to use for encryption.
		 * 
		 * <p>
		 * Algorithm parameters <em>must</em> be valid registered JOSE header values. If
		 * not specified, but the JWT is for a single audience principal that includes a
		 * public key, key encryption will be based on key type. Default content
		 * encryption algorithm is A128CBC-HS256.
		 * </p>
		 * 
		 * <dl>
		 * <dt>{@link RSAPublicKey}</dt>
		 * <dd>RSA-OAEP</dd>
		 * <dt>{@link ECPublicKey} or {@link XECPublicKey}</dt>
		 * <dd>ECDH-ES</dd>
		 * </dl>
		 * 
		 * @param alg key encryption algorithm
		 * @param enc content encryption algorithm
		 * 
		 * @return this
		 * @see <a href=
		 *      "https://www.iana.org/assignments/jose/jose.xhtml#web-signature-encryption-algorithms">IANA
		 *      JOSE Registry</a>
		 */
		Builder encrypt(String alg, String enc);

		/**
		 * Signs, <em>optionally</em> encrypts, and issues the JWT using the default
		 * signature algorithm by issuer key type.
		 * 
		 * <dl>
		 * <dt>{@link RSAPrivateKey} with {@link RSAPrivateKey#getAlgorithm()} of
		 * "RSA"</dt>
		 * <dd>RS256</dd>
		 * <dt>{@link RSAPrivateKey} with {@link RSAPrivateKey#getAlgorithm()} of
		 * "RSASSA-PSS"</dt>
		 * <dd>PS256</dd>
		 * <dt>{@link ECPrivateKey}</dt>
		 * <dd>ES256</dd>
		 * <dt>EdECPrivateKey (JDK 15 or higher)</dt>
		 * <dd>EdDSA</dd>
		 * </dl>
		 * 
		 * @return {@link IuWebToken}
		 */
		IuWebToken sign();

		/**
		 * Signs, <em>optionally</em> encrypts, and issues the JWT.
		 * 
		 * @param alg Signature algorithm
		 * @return {@link IuWebToken}
		 */
		IuWebToken sign(String alg);
	}

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
	 * Registers a trusted JWT issuer.
	 * 
	 * <p>
	 * If the issuer principal includes a private key matching its certificate, its
	 * principal name <em>may<em> be used with {@link #issue(String)} to create new
	 * JWTs.
	 * </p>
	 * 
	 * @param issuer Issuer principal; <em>must</em> include a valid certificate
	 *               with CN matching the principal name.
	 */
	static void register(IuPrincipalIdentity issuer) {
		IuAuthSpiFactory.get(IuJwtSpi.class).register(issuer);
	}

	/**
	 * Registers an JWT authentication realm.
	 * 
	 * <p>
	 * If the audience principal includes a private key, {@link #from(String)} will
	 * <em>require</em> the JWT to be encrypted to the audience as well as signed.
	 * </p>
	 * 
	 * @param jwtRealm JWT authentication realm
	 * @param audience Audience principal; <em>must</em> include a private key and
	 *                 valid certificate with CN matching the principal name.
	 * @param realm    Authentication realm for verifying the audience principal
	 */
	static void register(String jwtRealm, IuPrincipalIdentity audience, String realm) {
		IuAuthSpiFactory.get(IuJwtSpi.class).register(jwtRealm, audience, realm);
	}

	/**
	 * Seals the JWT verification registry.
	 * 
	 * <p>
	 * Once this method has been invoked, further calls to register
	 * {@link #register(IuPrincipalIdentity) issuer} or
	 * {@link #register(String, IuPrincipalIdentity) audience} identifying
	 * principals will be rejected.
	 * </p>
	 */
	static void seal() {
		IuAuthSpiFactory.get(IuJwtSpi.class).seal();
	}

	/**
	 * Issues a new JWT.
	 * 
	 * @param issuer Issuer principal name; <em>must</em> have be
	 *               {@link #register(IuPrincipalIdentity) registered} with a
	 *               private key and valid certificate.
	 * @return {@link Builder}
	 */
	static Builder issue(String issuer) {
		return IuAuthSpiFactory.get(IuJwtSpi.class).issue(issuer);
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
	 * @param name claim name
	 * @return extended claim value
	 * @see <a href="https://www.iana.org/assignments/jwt/jwt.xhtml">IANA JWT
	 *      Assignments</a>
	 */
	<T> T getClaim(String name);

	/**
	 * Gets an extended claim.
	 * 
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
