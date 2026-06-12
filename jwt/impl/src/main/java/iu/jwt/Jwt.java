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
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.config.IuConfig;
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
import jakarta.json.stream.JsonGenerator;

/**
 * Immutable {@link WebToken} with JWT signing, signature verification, and
 * encryption methods.
 */
public class Jwt implements WebToken {
	static {
		IuObject.assertNotOpen(Jwt.class);
	}

	/** Translates {@link Instant} values as seconds since epoch */
	static final IuJsonAdapter<Instant> NUMERIC_DATE = IuJsonAdapter.from( //
			v -> Instant.ofEpochSecond(IuJsonAdapter.of(Long.class).fromJson(v).longValue()),
			v -> IuJsonAdapter.of(Long.class).toJson(v.getEpochSecond()));

	/**
	 * Gets a JSON adapter for decoding JWT claims.
	 * 
	 * @param <T>  claim value type
	 * @param type claim value type class
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	static final <T> IuJsonAdapter<T> adapt(Type type) {
		if (type == Instant.class)
			return (IuJsonAdapter<T>) NUMERIC_DATE;
		else if ((type instanceof Class) //
				&& IuAuthorizationDetails.class.isAssignableFrom((Class<?>) type))
			return (IuJsonAdapter<T>) IuJsonAdapter.from((Class<?>) type,
					IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, Jwt::adapt);
		else
			return (IuJsonAdapter<T>) IuConfig.adaptJson(type);
	}

	/**
	 * Gets a JSON adapter for decoding JWT claims.
	 * 
	 * @param <T>  claim value type
	 * @param type claim value type class
	 * @return {@link IuJsonAdapter}
	 */
	static final <T> IuJsonAdapter<T> adapt(Class<T> type) {
		return adapt((Type) type);
	}

	/** Parsed JWT claims */
	protected final JsonObject claims;

	/**
	 * JSON claims constructor
	 * 
	 * @param claims {@link JsonObject} of token claims
	 */
	Jwt(JsonObject claims) {
		this.claims = claims;
		validate();
	}

	/**
	 * Parses and verifies a JWT encoded with {@link WebSignedPayload#compact() JWS
	 * compact serialization}.
	 * 
	 * @param jwt       {@link WebSignedPayload#compact() JWS compact serialization}
	 * @param issuerKey Issuer public {@link WebKey}
	 * @return {@link JsonObject} of token claims
	 */
	static Jwt verify(String jwt, WebKey issuerKey) {
		final var jws = WebSignedPayload.parse(jwt);
		jws.verify(issuerKey);
		return new Jwt(IuJson.parse(IuText.utf8(jws.getPayload())).asJsonObject());
	}

	/**
	 * Parses, decrypts, and verifies a JWT encoded with
	 * {@link WebEncryption#compact() JWE compact serialization}.
	 * 
	 * @param jwt        {@link WebEncryption#compact() JWE} compact serialization
	 * @param issuerKey  Issuer public {@link WebKey key}
	 * @param decryptKey Private {@link WebKey key} for decryption
	 * @return {@link JsonObject} of token claims
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
		final var value = claims.get(name);
		if (value == null)
			return null;
		else
			return adapt(type).fromJson(value);
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
		final var aud = getClaim("aud", URI[].class);
		if (aud == null)
			return null;
		else
			return IuIterable.iter(aud);
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
		final var authorizationDetails = claims.get("authorization_details");
		if (authorizationDetails instanceof JsonArray) {
			final var adapter = Jwt.adapt(detailInterface);
			for (final var authorizationDetail : (JsonArray) authorizationDetails)
				if (authorizationDetail instanceof JsonObject) {
					if (type.equals(IuJson.get(authorizationDetail.asJsonObject(), "type")))
						rv.offer(adapter.fromJson(authorizationDetail));
				}
		}
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
		if (certChain != null)
			builder.x5t(IuDigest.sha1(IuException.unchecked(certChain[0]::getEncoded)));

		return builder.sign(claims.toString()).compact();
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
		return claims.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		Jwt other = (Jwt) obj;
		return IuObject.equals(claims, other.claims);
	}

	@Override
	public String toString() {
		final var writer = new StringWriter();
		IuJson.PROVIDER.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)) //
				.createWriter(writer) //
				.write(claims);
		return writer.toString();
	}

}