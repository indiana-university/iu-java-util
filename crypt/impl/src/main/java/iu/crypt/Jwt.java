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
package iu.crypt;

import java.io.StringWriter;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.crypt.WebToken;
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
	protected static final IuJsonAdapter<Instant> NUMERIC_DATE = IuJsonAdapter.from( //
			v -> Instant.ofEpochSecond(IuJsonAdapter.of(Long.class).fromJson(v).longValue()),
			v -> IuJsonAdapter.of(Long.class).toJson(v.getEpochSecond()));

	/** Parsed JWT claims */
	protected final JsonObject claims;

	/**
	 * JSON claims constructor
	 * 
	 * @param claims {@link JsonObject} of token claims
	 */
	protected Jwt(JsonObject claims) {
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
	public static JsonObject verify(String jwt, WebKey issuerKey) {
		final var jws = WebSignedPayload.parse(jwt);
		jws.verify(issuerKey);
		return IuJson.parse(IuText.utf8(jws.getPayload())).asJsonObject();
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
	 * @return {@link JsonObject} of token claims
	 */
	public static JsonObject decryptAndVerify(String jwt, WebKey issuerKey, WebKey audienceKey) {
		return verify(WebEncryption.parse(jwt)
				.decryptText(Objects.requireNonNull(audienceKey, "Missing audience key for decryption")), issuerKey);
	}

	/**
	 * Performs basic JWT validation logic.
	 * 
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1">RFC-7519
	 *      JSON Web Token Section 4.1</a>
	 */
	protected void validate() {
		validateNotBefore("iat", getIssuedAt());
		validateNotBefore("nbf", getNotBefore());
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

		Objects.requireNonNull(getIssuer(), "Missing iss claim");
		Objects.requireNonNull(getSubject(), "Missing sub claim");

		boolean found = false;
		for (final var aud : Objects.requireNonNull(getAudience(), "Missing aud claim"))
			if (aud.equals(expectedAudience)) {
				found = true;
				break;
			}
		if (!found)
			throw new IllegalArgumentException("Token aud claim doesn't include " + expectedAudience);

		final var issuedAt = Objects.requireNonNull(getIssuedAt(), "Missing iat claim");
		final var expires = Objects.requireNonNull(getExpires(), "Missing exp claim");
		if (ttl.compareTo(Duration.between(issuedAt, expires)) < 0)
			throw new IllegalArgumentException("Token exp claim must be no more than " + ttl + " in the future");

	}

	@Override
	public String getTokenId() {
		return IuJson.get(claims, "jti");
	}

	@Override
	public URI getIssuer() {
		return IuJson.get(claims, "iss", IuJsonAdapter.of(URI.class));
	}

	@Override
	public Iterable<URI> getAudience() {
		return IuJson.get(claims, "aud", IuJsonAdapter.of(Iterable.class, IuJsonAdapter.of(URI.class)));
	}

	@Override
	public String getSubject() {
		return IuJson.get(claims, "sub");
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
	public String getNonce() {
		return IuJson.get(claims, "nonce");
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
	public String sign(String type, Algorithm algorithm, WebKey issuerKey) {
		return WebSignature.builder(algorithm).compact().key(issuerKey).type(type).sign(claims.toString()).compact();
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
	public String signAndEncrypt(String type, Algorithm signAlgorithm, WebKey issuerKey, Algorithm encryptAlgorithm,
			Encryption encryption, WebKey audienceKey) {
		return WebEncryption.builder(encryption).compact().addRecipient(encryptAlgorithm).key(audienceKey)
				.contentType(type).encrypt(sign(type, signAlgorithm, issuerKey)).compact();
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