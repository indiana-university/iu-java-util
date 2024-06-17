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
package iu.auth.jwt;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.AuthConfig;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.oauth.IuBearerToken;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;

/**
 * {@link IuWebToken} implementation class.
 */
final class Jwt implements IuWebToken {
	private static final long serialVersionUID = 1L;

	/**
	 * JSON adapter for StringOrURI values.
	 * 
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7519#section-2">RFC-7519 JWT
	 *      Terminology</a>
	 */
	static final IuJsonAdapter<String> STRING_OR_URI = IuJsonAdapter
			.text(a -> a.indexOf(':') == -1 ? a : URI.create(a).toString());

	/**
	 * JSON adapter for NumericDate values.
	 * 
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7519#section-2">RFC-7519 JWT
	 *      Terminology</a>
	 */
	static final IuJsonAdapter<Instant> NUMERIC_DATE = IuJsonAdapter
			.from(v -> Instant.ofEpochSecond(((JsonNumber) v).longValue()), i -> IuJson.number(i.getEpochSecond()));

	/**
	 * Parses the JWT header from serialized form.
	 * 
	 * @param jwt serialized JWT
	 * @return {@link JsonObject} parsed header
	 */
	static JsonObject getHeader(String jwt) {
		// 1. Verify that the JWT contains at least one period ('.') character.
		var dot = jwt.indexOf('.');
		if (dot == -1)
			throw new IllegalArgumentException("Invalid JWT");

		// 2. Let the Encoded JOSE Header be the portion of the JWT before the first
		// period ('.') character.
		//
		// 3. Base64url decode the Encoded JOSE Header following the restriction that no
		// line breaks, whitespace, or other additional characters have been used.
		//
		// 4. Verify that the resulting octet sequence is a UTF-8-encoded representation
		// of a completely valid JSON object conforming to RFC 7159 [RFC7159]; let the
		// JOSE Header be this JSON object.
		return IuJson.parse(IuText.utf8(Base64.getUrlDecoder().decode(jwt.substring(0, dot)))).asJsonObject();
	}

	/**
	 * Determines whether or not a JWT is encrypted by inspecting its header.
	 * 
	 * @param jose JWT header
	 * @return true if encrypted; else false
	 */
	static boolean isEncrypted(JsonObject jose) {
		// 6. Determine whether the JWT is a JWS or a JWE using any of the methods
		// described in Section 9 of [JWE]: The JOSE Header for a JWS can be
		// distinguished from the JOSE Header for a JWE by examining the "alg"
		// (algorithm) Header Parameter value.
		final var alg = Algorithm.from(jose.getString("alg"));
		return alg.use.equals(Use.ENCRYPT);
	}

	private final String realm;
	private final String token;
	private transient byte[] payload;
	private transient WebSignature signature;
	private transient JsonObject claims;

	/**
	 * Constructor.
	 * 
	 * @param realm JWT authentication realm
	 * @param token parsed JWS containing token claims as payload
	 */
	Jwt(String realm, String token) {
		this.realm = realm;
		this.token = token;
		materialize();
	}

	@Override
	public String getAlgorithm() {
		return signature().getHeader().getAlgorithm().name();
	}

	@Override
	public String getIssuer() {
		return IuJson.get(claims, "iss", STRING_OR_URI);
	}

	@Override
	public String getName() {
		return IuJson.get(claims, "sub", STRING_OR_URI);
	}

	@Override
	public String getTokenId() {
		return IuJson.get(claims, "jti");
	}

	@Override
	public Iterable<String> getAudience() {
		return IuJson.get(claims, "aud", IuJsonAdapter.of(Iterable.class, STRING_OR_URI));
	}

	@Override
	public Instant getIssuedAt() {
		return IuJson.get(claims, "iat", NUMERIC_DATE);
	}

	@Override
	public Instant getNotBefore() {
		return IuJson.get(claims, "nbf", NUMERIC_DATE);
	}

	@Override
	public Instant getExpires() {
		return IuJson.get(claims, "exp", NUMERIC_DATE);
	}

	@Override
	public <T> T getClaim(String name) {
		return IuJson.get(claims, name);
	}

	@Override
	public <T> T getClaim(String name, Class<T> type) {
		return IuJson.get(claims, name, IuJsonAdapter.of(type));
	}

	@Override
	public Subject getSubject() {
		return new Subject(true, Set.of(this), Set.of(), Set.of());
	}

	@Override
	public String toString() {
		return "Jwt [alg=" + getAlgorithm() + ", iss=" + getIssuer() + ", sub=" + getName() + ", jti=" + getTokenId()
				+ ", aud=" + getAudience() + ", iat=" + getIssuedAt() + ", nbf=" + getNotBefore() + ", exp="
				+ getExpires() + "]";
	}

	@Override
	public IuBearerToken asBearerToken(Set<String> scope) {
		return new JwtBearer(this, scope);
	}

	@Override
	public IuApiCredentials asAuthorizationGrant(Set<String> scope) {
		return new JwtAuthorizationGrant(this, scope);
	}

	@Override
	public IuApiCredentials asClientAssertion(Map<String, ? extends Iterable<String>> tokenParameters) {
		return new JwtClientAssertion(this, tokenParameters);
	}

	/**
	 * Gets the signature.
	 * 
	 * @return {@link WebSignature}
	 */
	WebSignature signature() {
		return signature;
	}

	/**
	 * Gets the raw signature payload.
	 * 
	 * @return payload
	 */
	byte[] payload() {
		return payload;
	}

	/**
	 * Gets the realm.
	 * 
	 * @return realm
	 */
	String realm() {
		return realm;
	}

	/**
	 * Gets the token.
	 * 
	 * @return token
	 */
	String token() {
		return token;
	}

	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		materialize();
	}

	private void materialize() {
		final var jose = getHeader(token);
		final WebSignedPayload jws;
		if (isEncrypted(jose)) {
			final IuPrivateKeyPrincipal pkp = AuthConfig.get(realm);
			final var audience = pkp.getIdentity();
			IuException.unchecked(() -> IuPrincipalIdentity.verify(audience, realm));
			jws = WebSignedPayload.parse(WebEncryption.parse(token) //
					.decryptText(JwtSpi.getDecryptKey(audience)));
		} else
			jws = WebSignedPayload.parse(token);

		final var signatureIterator = jws.getSignatures().iterator();
		final var signature = signatureIterator.next();
		if (signatureIterator.hasNext())
			throw new IllegalArgumentException();
		payload = jws.getPayload();
		claims = IuJson.parse(IuText.utf8(payload)).asJsonObject();

		final IuPrivateKeyPrincipal pkp = AuthConfig.get(getIssuer());
		jws.verify(JwtSpi.getVerifyKey(pkp.getIdentity()));

		this.signature = signature;
	}

}
