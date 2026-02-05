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
package iu.auth.config;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.oauth.OAuthClient;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignedPayload;
import iu.crypt.Jwt;
import jakarta.json.JsonObject;

/**
 * Extends {@link Jwt} to include OIDC ID token claim validation.
 */
public class OidcIdToken extends Jwt {

	private final Algorithm alg;
	private final OAuthClient client;
	private final String nonce;
	private final String accessToken;
	private final Duration maxAge;

	/**
	 * Constructor.
	 * 
	 * @param alg         Signature algorithm used to verify ID Token authenticity
	 * @param client      Client to which this token was issued
	 * @param nonce       One-Time number (nonce) value provided with the original
	 *                    authentication request
	 * @param accessToken Access token issued with this ID token.
	 * @param maxAge      Maximum length of time to allow since the user's
	 *                    authentication credentials were last verified.
	 * @param claims      JWT claims
	 */
	public OidcIdToken(Algorithm alg, OAuthClient client, String nonce, String accessToken, Duration maxAge,
			JsonObject claims) {
		super(claims);
		this.alg = alg;
		this.client = client;
		this.nonce = nonce;
		this.accessToken = accessToken;
		this.maxAge = maxAge;
	}

	/**
	 * Parses and verifies an ID token encoded with
	 * {@link WebSignedPayload#compact() JWS compact serialization}.
	 * 
	 * @param jwt         {@link WebSignedPayload#compact() JWS compact
	 *                    serialization}
	 * @param issuerKey   Issuer public {@link WebKey}
	 * @param client      Client to which this token was issued
	 * @param nonce       One-Time number (nonce) value provided with the original
	 *                    authentication request
	 * @param accessToken Access token issued with this ID token.
	 * @param maxAge      Maximum length of time to allow since the user's
	 *                    authentication credentials were last verified.
	 * @return {@link OidcIdToken}
	 */
	public static OidcIdToken verify(String jwt, WebKey issuerKey, OAuthClient client, String nonce, String accessToken,
			Duration maxAge) {
		final var jws = WebSignedPayload.parse(jwt);
		jws.verify(issuerKey);
		final var claims = IuJson.parse(IuText.utf8(jws.getPayload())).asJsonObject();

		final var alg = jws.getSignatures().iterator().next().getHeader().getAlgorithm();
		return new OidcIdToken(alg, client, nonce, accessToken, maxAge, claims);
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
	 * @param client      Client to which this token was issued
	 * @param nonce       One-Time number (nonce) value provided with the original
	 *                    authentication request
	 * @param accessToken Access token issued with this ID token.
	 * @param maxAge      Maximum length of time to allow since the user's
	 *                    authentication credentials were last verified.
	 * @return {@link JsonObject} of token claims
	 */
	public static OidcIdToken decryptAndVerify(String jwt, WebKey issuerKey, WebKey audienceKey, OAuthClient client,
			String nonce, String accessToken, Duration maxAge) {
		return verify(
				WebEncryption.parse(jwt)
						.decryptText(Objects.requireNonNull(audienceKey, "Missing audience key for decryption")),
				issuerKey, client, nonce, accessToken, maxAge);
	}

	@Override
	public void validateClaims(URI expectedAudience, Duration ttl) {
		super.validateClaims(expectedAudience, ttl);

		final var azp = IuJson.get(claims, "azp");
		if (azp != null)
			IuObject.once(client.getClientId(), azp, "azp must match client_id");

		final var nonce = getNonce();
		if (this.nonce == null) {
			if (nonce != null)
				throw new IllegalArgumentException("Unexpected nonce claim");
		} else if (nonce == null)
			throw new IllegalArgumentException("Expected nonce claim");
		else
			IuObject.once(nonce, IuJson.get(claims, "nonce"), "nonce mismatch");

		if (maxAge != null) {
			final var authTime = Objects.requireNonNull(getAuthTime(), "Missing auth_time claim");
			final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
			final var authAge = Duration.between(authTime, now);
			if (authAge.compareTo(maxAge) >= 0)
				throw new IllegalArgumentException(
						"Authenticated session lifetime " + authAge + " exceeds maximum " + maxAge);
		}

		if (accessToken != null) {
			final var atHash = Objects.requireNonNull(IuJson.get(claims, "at_hash"), "Missing at_hash claim");
			final var sha = IuException.unchecked(() -> MessageDigest.getInstance("SHA-" + alg.size));
			final var hash = sha.digest(IuText.ascii(accessToken));
			final var halfHash = Arrays.copyOfRange(hash, 0, alg.size / 16);
			if (!IuText.base64Url(halfHash).equals(atHash))
				throw new IllegalArgumentException("at_hash mismatch");
		}
	}

	/**
	 * Gets the full display name.
	 * 
	 * @return full display name
	 */
	public String getFullName() {
		return IuJson.get(claims, "name");
	}

	/**
	 * Gets the point in time authentication occurred.
	 * 
	 * @return auth_time claim
	 */
	public Instant getAuthTime() {
		return IuJson.get(claims, "auth_time", NUMERIC_DATE);
	}

	/**
	 * Gets the access token provided with this ID token.
	 * 
	 * @return access token
	 */
	public String getAccessToken() {
		return accessToken;
	}

}
