/*
 * Copyright © 2026 Indiana University
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
package iu.jwt;

import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.jwt.IuAuthorizationDetails;
import edu.iu.jwt.WebToken;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

/**
 * Immutable {@link WebToken} with JWT signing, signature verification, and
 * encryption methods.
 *
 * <p>
 * Claims convert by {@link WebToken#jsonb()}.
 * </p>
 */
public class Jwt implements WebToken {
	static {
		IuObject.assertNotOpen(Jwt.class);
	}

	/** Parsed JWT claims */
	protected final IuJsonProperties claims;

	/**
	 * JSON claims constructor
	 *
	 * @param claims {@link JsonObject} of token claims
	 */
	Jwt(JsonObject claims) {
		this(IuJsonProperties.of(claims, TokenJsonb.get()));
	}

	/**
	 * Claims constructor
	 *
	 * @param claims token claims
	 */
	Jwt(IuJsonProperties claims) {
		this.claims = claims;
		validate();
	}

	/**
	 * Parses and verifies a JWT encoded with {@link WebSignedPayload#compact() JWS
	 * compact serialization}.
	 *
	 * @param jwt       {@link WebSignedPayload#compact() JWS compact serialization}
	 * @param issuerKey Issuer public {@link WebKey}
	 * @return {@link Jwt} of token claims
	 */
	static Jwt verify(String jwt, WebKey issuerKey) {
		final var jws = WebSignedPayload.parse(jwt);
		jws.verify(issuerKey);
		return new Jwt(TokenJsonb.get().fromJson(IuText.utf8(jws.getPayload()), IuJsonProperties.class));
	}

	/**
	 * Parses, decrypts, and verifies a JWT encoded with
	 * {@link WebEncryption#compact() JWE compact serialization}.
	 *
	 * @param jwt        {@link WebEncryption#compact() JWE} compact serialization
	 * @param issuerKey  Issuer public {@link WebKey key}
	 * @param decryptKey Private {@link WebKey key} for decryption
	 * @return {@link Jwt} of token claims
	 */
	static Jwt decryptAndVerify(String jwt, WebKey issuerKey, WebKey decryptKey) {
		return verify(WebEncryption.parse(jwt).decryptText(Objects.requireNonNull(decryptKey, "missing decyptKey")),
				issuerKey);
	}

	private static void validateNotBefore(String claimName, Instant notBefore) {
		if (notBefore != null //
				&& notBefore.isAfter(Instant.now().plusSeconds(15L)))
			throw new IllegalArgumentException(
					"Token " + claimName + " claim must be no more than PT15S in the future");
	}

	/**
	 * Performs JWT point in time validation.
	 *
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1">RFC-7519
	 *      JSON Web Token Section 4.1</a>
	 */
	void validate() {
		validateNotBefore("iat", getIssuedAt());
		validateNotBefore("nbf", getNotBefore());
		if (isExpired())
			throw new IllegalArgumentException("Token is expired");
	}

	@Override
	public void validateClaims(URI expectedIssuer, URI expectedAudience, Duration ttl) {
		validate();

		Objects.requireNonNull(expectedIssuer, "Missing expectedIssuer");
		final var iss = Objects.requireNonNull(getIssuer(), "Missing iss claim");
		IuObject.once(expectedIssuer, iss, "Token iss claim " + iss + " mismatch, expected " + expectedIssuer);

		Objects.requireNonNull(getSubject(), "Missing sub claim");

		Objects.requireNonNull(expectedAudience, "Missing expectedAudience");
		final var aud = Objects.requireNonNull(getAudience(), "Missing aud claim");
		IuIterable.select(aud, expectedAudience::equals,
				"Token aud claim " + aud + " doesn't include " + expectedAudience);

		final var issuedAt = Objects.requireNonNull(getIssuedAt(), "Missing iat claim");
		final var expires = Objects.requireNonNull(getExpires(), "Missing exp claim");
		if (ttl.compareTo(Duration.between(issuedAt, expires)) < 0)
			throw new IllegalArgumentException("Token exp claim must be no more than " + ttl + " in the future");

	}

	@Override
	public <T> T getClaim(String name, Class<T> type) {
		return type.cast(getClaim(name, (Type) type));
	}

	@Override
	public Object getClaim(String name, Type type) {
		return claims.get(name, type);
	}

	@Override
	public String getTokenId() {
		return getClaim("jti", String.class);
	}

	@Override
	public URI getIssuer() {
		return getClaim("iss", URI.class);
	}

	@Override
	public Iterable<URI> getAudience() {
		// a single audience may be a string, rather than an array of one
		final var aud = getClaim("aud", JsonValue.class);
		if (aud instanceof JsonArray)
			return IuIterable.iter(getClaim("aud", URI[].class));
		else if (aud instanceof JsonString)
			return IuIterable.iter(getClaim("aud", URI.class));
		else
			return null;
	}

	@Override
	public String getSubject() {
		return getClaim("sub", String.class);
	}

	@Override
	public Instant getIssuedAt() {
		return getClaim("iat", Instant.class);
	}

	@Override
	public Instant getNotBefore() {
		return getClaim("nbf", Instant.class);
	}

	@Override
	public Instant getExpires() {
		return getClaim("exp", Instant.class);
	}

	@Override
	public String getNonce() {
		return getClaim("nonce", String.class);
	}

	@Override
	public String getScope() {
		return getClaim("scope", String.class);
	}

	@Override
	public <T extends IuAuthorizationDetails> Iterable<T> getAuthorizationDetails(Class<T> detailInterface,
			String type) {
		final Queue<T> rv = new ArrayDeque<>();
		final var authorizationDetails = getClaim("authorization_details", JsonValue.class);
		if (authorizationDetails instanceof JsonArray)
			for (final var authorizationDetail : (JsonArray) authorizationDetails)
				if (authorizationDetail instanceof JsonObject //
						&& type.equals(IuJson.get(authorizationDetail.asJsonObject(), "type")))
					rv.offer(TokenJsonb.get().fromJson(authorizationDetail.toString(), detailInterface));
		return rv;
	}

	@Override
	public boolean isExpired() {
		final var expires = getExpires();
		return expires != null //
				&& expires.isBefore(Instant.now().minusSeconds(15L));
	}

	/**
	 * Signs this {@link Jwt}
	 *
	 * @param type      Token type
	 * @param algorithm {@link Algorithm}
	 * @param issuerKey Issuer private {@link WebKey}
	 * @return {@link WebSignedPayload#compact() JWS compact serialization}
	 */
	@Override
	@SuppressWarnings("deprecation")
	public String sign(String type, Algorithm algorithm, WebKey issuerKey) {
		final var builder = WebSignature.builder(algorithm).compact().key(issuerKey).type(type);

		final var keyId = issuerKey.getKeyId();
		if (keyId != null)
			builder.keyId(keyId);

		final var certChain = issuerKey.getCertificateChain();
		if (certChain != null) {
			if (certChain.length == 1)
				builder.x5t(IuDigest.sha1(IuException.unchecked(certChain[0]::getEncoded)));
			else // include full cert for CA-signed
				builder.cert(certChain);
		}

		return builder.sign(TokenJsonb.get().toJson(claims)).compact();
	}

	/**
	 * Signs and encrypts this {@link Jwt}
	 *
	 * @param type             Token type
	 * @param signAlgorithm    {@link Algorithm}
	 * @param issuerKey        Issuer private {@link WebKey}
	 * @param encryptAlgorithm {@link Algorithm}
	 * @param encryption       {@link Encryption}
	 * @param audienceKey      Audience public {@link WebKey}
	 * @return {@link WebEncryption#compact() JWE compact serialization}
	 */
	@Override
	public String signAndEncrypt(String type, Algorithm signAlgorithm, WebKey issuerKey, Algorithm encryptAlgorithm,
			Encryption encryption, WebKey audienceKey) {
		return WebEncryption.builder(encryption).compact().addRecipient(encryptAlgorithm).keyId(audienceKey.getKeyId())
				.key(audienceKey).contentType(type).encrypt(sign(type, signAlgorithm, issuerKey)).compact();
	}

	@Override
	public int hashCode() {
		return claims.toJsonObject().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		Jwt other = (Jwt) obj;
		return IuObject.equals(claims.toJsonObject(), other.claims.toJsonObject());
	}

	@Override
	public String toString() {
		final var writer = new StringWriter();
		IuJson.PROVIDER.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)) //
				.createWriter(writer) //
				.write(claims.toJsonObject());
		return writer.toString();
	}

}
