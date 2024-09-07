/*
s * Copyright © 2024 Indiana University
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

import java.io.StringWriter;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.stream.JsonGenerator;

/**
 * Immutable {@link IuWebToken} with JWT signing, signature verification, and
 * encryption methods.
 */
public class Jwt implements IuWebToken {
	static {
		IuObject.assertNotOpen(Jwt.class);
	}

	/** Translates {@link Instant} values as seconds since epoch */
	protected static final IuJsonAdapter<Instant> NUMERIC_DATE = IuJsonAdapter.from( //
			v -> Instant.ofEpochSecond(IuJsonAdapter.of(Long.class).fromJson(v).longValue()),
			v -> IuJsonAdapter.of(Long.class).toJson(v.getEpochSecond()));

	private final String tokenId;
	private final URI issuer;
	private final URI[] audience;
	private final String subject;
	private final Instant issuedAt;
	private final Instant notBefore;
	private final Instant expires;
	private final String nonce;

	/**
	 * Copy constructor
	 * 
	 * @param copy Externally supplied {@link IuWebToken}
	 */
	protected Jwt(IuWebToken copy) {
		tokenId = copy.getTokenId();
		issuer = copy.getIssuer();

		final var aud = copy.getAudience();
		if (aud == null)
			audience = null;
		else
			audience = IuIterable.stream(aud).toArray(URI[]::new);

		subject = copy.getSubject();
		issuedAt = copy.getIssuedAt();
		notBefore = copy.getNotBefore();
		expires = copy.getExpires();
		nonce = copy.getNonce();
		validate();
	}

	/**
	 * Parses and verifies a JWT encoded with {@link WebSignedPayload#compact() JWS
	 * compact serialization}.
	 * 
	 * @param jwt       {@link WebSignedPayload#compact() JWS compact serialization}
	 * @param issuerKey Issuer public {@link WebKey}
	 */
	public Jwt(String jwt, WebKey issuerKey) {
		this(jwt, issuerKey, null);
	}

	/**
	 * Parses, decrypts, and verifies a JWT encoded with
	 * {@link WebSignedPayload#compact() JWS compact serialization}.
	 * 
	 * @param jwt         {@link WebSignedPayload#compact() JWS} or
	 *                    {@link WebEncryption#compact() JWE} compact serialization
	 * @param issuerKey   Issuer public {@link WebKey}
	 * @param audienceKey Audience private {@link WebKey}, ignored if the JWT is not
	 *                    encrypted
	 */
	public Jwt(String jwt, WebKey issuerKey, WebKey audienceKey) {
		final var dot = IuObject.require(//
				Objects.requireNonNull(jwt, "Missing token").indexOf('.'), //
				i -> i != -1, "Invalid token; must be enclosed in a compact JWS or JWE");

		final var encodedProtectedHeader = jwt.substring(0, dot);
		final var parsedProtectedHeader = IuJson
				.parse(IuText.utf8(Base64.getUrlDecoder().decode(encodedProtectedHeader)));
		final var jose = WebCryptoHeader.JSON.fromJson(parsedProtectedHeader);

		final String decryptedJwt;
		if (jose.getAlgorithm().use.equals(Use.SIGN))
			decryptedJwt = jwt;
		else
			decryptedJwt = WebEncryption.parse(jwt)
					.decryptText(Objects.requireNonNull(audienceKey, "Missing audience key for decryption"));

		final var jws = WebSignedPayload.parse(decryptedJwt);
		jws.verify(issuerKey);

		final var claims = IuJson.parse(IuText.utf8(jws.getPayload())).asJsonObject();
		tokenId = IuJson.get(claims, "jti");
		issuer = IuJson.get(claims, "iss", IuJsonAdapter.of(URI.class));
		audience = IuJson.get(claims, "aud", IuJsonAdapter.of(URI[].class));
		subject = IuJson.get(claims, "sub");
		issuedAt = IuJson.get(claims, "iat", NUMERIC_DATE);
		notBefore = IuJson.get(claims, "nbf", NUMERIC_DATE);
		expires = IuJson.get(claims, "exp", NUMERIC_DATE);
		nonce = IuJson.get(claims, "nonce");
		validate();
	}

	private JsonObject toJson() {
		final var builder = IuJson.object();
		addClaims(builder);
		return builder.build();
	}

	/**
	 * Adds token claims to a {@link JsonObjectBuilder} while serializing the JWT
	 * signature payload.
	 * 
	 * <p>
	 * Subclasses SHOULD override with method and call
	 * {@code super.addClaims(builder)} to add extended claims.
	 * </p>
	 * 
	 * @param builder {@link JsonObjectBuilder}
	 * @see #NUMERIC_DATE For RFC-compliant handling of Instant values
	 * @see IuJsonAdapter#of(java.lang.reflect.Type) for additional built-in types
	 */
	protected void addClaims(JsonObjectBuilder builder) {
		IuJson.add(builder, "jti", tokenId);
		IuJson.add(builder, "iss", () -> issuer, IuJsonAdapter.of(URI.class));
		if (audience != null)
			if (audience.length == 1)
				IuJson.add(builder, "aud", () -> audience[0], IuJsonAdapter.of(URI.class));
			else
				IuJson.add(builder, "aud", () -> audience, IuJsonAdapter.of(URI[].class));
		IuJson.add(builder, "sub", subject);
		IuJson.add(builder, "iat", () -> issuedAt, NUMERIC_DATE);
		IuJson.add(builder, "nbf", () -> notBefore, NUMERIC_DATE);
		IuJson.add(builder, "exp", () -> expires, NUMERIC_DATE);
		IuJson.add(builder, "nonce", nonce);
	}

	/**
	 * Performs basic JWT validation logic.
	 * 
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1">RFC-7519
	 *      JSON Web Token Section 4.1</a>
	 */
	protected void validate() {
		validateNotBefore("iat", issuedAt);
		validateNotBefore("nbf", notBefore);
		if (isExpired())
			throw new IllegalArgumentException("Token is expired");
	}

	private static void validateNotBefore(String claimName, Instant notBefore) {
		if (notBefore != null //
				&& notBefore.isAfter(Instant.now().plusSeconds(15L)))
			throw new IllegalArgumentException(
					"Token " + claimName + " claim must be no more than PT15S in the future");
	}

	@Override
	public void validateClaims(URI expectedAudience, Duration ttl) {
		validate();

		Objects.requireNonNull(issuer, "Missing iss claim");
		Objects.requireNonNull(subject, "Missing sub claim");

		Objects.requireNonNull(audience, "Missing aud claim");
		IuObject.require(audience, a -> a.length > 0, "Empty aud claim");

		boolean found = false;
		for (final var aud : audience)
			if (aud.equals(expectedAudience)) {
				found = true;
				break;
			}
		if (!found)
			throw new IllegalArgumentException("Token aud claim doesn't include " + expectedAudience);

		Objects.requireNonNull(issuedAt, "Missing iat claim");
		Objects.requireNonNull(expires, "Missing exp claim");

		final var exp = Objects.requireNonNull(expires, "Missing exp claim");
		if (ttl.compareTo(Duration.between(issuedAt, exp)) < 0)
			throw new IllegalArgumentException("Token exp claim must be no more than " + ttl + " in the future");

	}

	@Override
	public String getTokenId() {
		return tokenId;
	}

	@Override
	public URI getIssuer() {
		return issuer;
	}

	@Override
	public Iterable<URI> getAudience() {
		return audience == null ? null : IuIterable.iter(audience);
	}

	@Override
	public String getSubject() {
		return subject;
	}

	@Override
	public Instant getIssuedAt() {
		return issuedAt;
	}

	@Override
	public Instant getNotBefore() {
		return notBefore;
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	@Override
	public String getNonce() {
		return nonce;
	}

	@Override
	public boolean isExpired() {
		return expires != null //
				&& expires.isBefore(Instant.now().minusSeconds(15L));
	}

	/**
	 * Signs this {@link Jwt}
	 * 
	 * @param algorithm {@link Algorithm}
	 * @param issuerKey Issuer private {@link WebKey}
	 * @return {@link WebSignedPayload#compact() JWS compact serialization}
	 */
	public String sign(Algorithm algorithm, WebKey issuerKey) {
		return WebSignature.builder(algorithm).compact().key(issuerKey).type("JWT").sign(toJson().toString()).compact();
	}

	/**
	 * Signs and encrypts this {@link Jwt}
	 * 
	 * @param signAlgorithm    {@link Algorithm}
	 * @param issuerKey        Issuer private {@link WebKey}
	 * @param encryptAlgorithm {@link Algorithm}
	 * @param encryption       {@link Encryption}
	 * @param audienceKey      Audience public {@link WebKey}
	 * @return {@link WebEncryption#compact() JWE compact serialization}
	 */
	public String signAndEncrypt(Algorithm signAlgorithm, WebKey issuerKey, Algorithm encryptAlgorithm,
			Encryption encryption, WebKey audienceKey) {
		return WebEncryption.builder(encryption).compact().addRecipient(encryptAlgorithm).key(audienceKey)
				.contentType("JWT").encrypt(sign(signAlgorithm, issuerKey)).compact();
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(tokenId, issuer, audience, subject, issuedAt, notBefore, expires, nonce);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		Jwt other = (Jwt) obj;
		return IuObject.equals(tokenId, other.tokenId) //
				&& IuObject.equals(issuer, other.issuer) //
				&& IuObject.equals(audience, other.audience) //
				&& IuObject.equals(subject, other.subject) //
				&& IuObject.equals(issuedAt, other.issuedAt) //
				&& IuObject.equals(notBefore, other.notBefore) //
				&& IuObject.equals(expires, other.expires) //
				&& IuObject.equals(nonce, other.nonce);
	}

	@Override
	public String toString() {
		final var writer = new StringWriter();
		IuJson.PROVIDER.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)) //
				.createWriter(writer) //
				.write(toJson());
		return writer.toString();
	}

}
