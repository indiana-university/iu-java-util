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
package iu.oidc.client;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.client.IuHttp;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebKey;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcTokenResponse;
import iu.oidc.client.config.IuOidcClientReference;

/**
 * Authenticates to a token endpoint, verifies and holds token response until
 * expired.
 */
public abstract class OidcTokenGrant {

	/**
	 * Client configuration reference.
	 */
	protected final IuOidcClientReference config;

	private IuOidcTokenResponse tokenResponse;
	private WebToken idToken;
	private Instant notAfter;

	/**
	 * Constructor.
	 * 
	 * @param config {@link IuOidcClientReference}
	 */
	public OidcTokenGrant(IuOidcClientReference config) {
		this.config = config;
	}

	/**
	 * Constructor.
	 * 
	 * @param config        {@link IuOidcClientReference}
	 * @param tokenResponse {@link IuOidcTokenResponse}
	 * @param notAfter      point in time expiration of tokenResponse
	 */
	public OidcTokenGrant(IuOidcClientReference config, IuOidcTokenResponse tokenResponse, Instant notAfter) {
		this.notAfter = notAfter;
		this.config = config;
		this.tokenResponse = tokenResponse;
		this.idToken = validateTokenResponse(tokenResponse);
	}

	/**
	 * Prepares an {@link HttpRequest.Builder} for the token endpoint.
	 * 
	 * @param requestBuilder {@link HttpRequest.Builder}
	 * @param params         in-progress POST params
	 */
	protected abstract void tokenAuth(HttpRequest.Builder requestBuilder, Map<String, Iterable<String>> params);

	/**
	 * Creates POST params and passes to
	 * {@link #tokenAuth(java.net.http.HttpRequest.Builder, Map)}.
	 * 
	 * @param requestBuilder {@link HttpRequest.Builder}
	 */
	final void tokenAuth(HttpRequest.Builder requestBuilder) {
		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		tokenAuth(requestBuilder, params);

		final var scope = config.getScope();
		if (scope != null)
			params.put("scope", IuIterable.iter(scope));

		requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		requestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(params)));
	}

	/**
	 * Creates a client or bearer assertion.
	 * 
	 * @return JWT signed by the client's assertion key
	 */
	protected String createAssertion() {
		final var oidcClient = config.getClient();
		final var clientId = oidcClient.getClientId();
		final var metadata = OidcProviders.getMetadata(config.getProvider());
		final var tokenEndpoint = metadata.getTokenEndpoint();
		final var assertionJwk = oidcClient.getAssertionJwk();
		return WebToken.builder() //
				.jti() //
				.iss(URI.create(clientId)) //
				.aud(tokenEndpoint) //
				.sub(clientId) //
				.iat() //
				.exp(Instant.now().plus(oidcClient.getAssertionTtl())) //
				.build() //
				.sign("JWT", assertionJwk.getAlgorithm(), assertionJwk);
	}

	/**
	 * Adds client authentication to pending token endpoint POST params.
	 * 
	 * @param params         POST params in progress; client authn params will be
	 *                       added
	 * @param requestBuilder {@link HttpRequest.Builder}
	 */
	protected void addClientAuth(HttpRequest.Builder requestBuilder, Map<String, Iterable<String>> params) {
		final var oidcClient = config.getClient();
		final var clientId = oidcClient.getClientId();
		params.put("client_id", IuIterable.iter(clientId));

		final var assertionJwk = oidcClient.getAssertionJwk();
		if (assertionJwk != null) {
			final var assertion = createAssertion();
			params.put("client_assertion_type",
					IuIterable.iter("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
			params.put("client_assertion", IuIterable.iter(assertion));
		} else {
			final var clientSecret = Objects.requireNonNull(oidcClient.getClientSecret(),
					"missing client_secret or assertion_jwk");
			if (oidcClient.isUseBasicAuth())
				requestBuilder.header("Authorization",
						"Basic " + IuText.base64(IuText.utf8(clientId + ":" + clientSecret)));
			else
				params.put("client_secret", IuIterable.iter(clientSecret));
		}
	}

	/**
	 * Gets the verified ID token.
	 * 
	 * @return ID token; null if token endpoint interactions succeeded, but an ID
	 *         token was not returned
	 */
	public WebToken getIdToken() {
		getTokenResponse();
		return idToken;
	}

	/**
	 * Gets the point in time the current response expires.
	 * 
	 * @return point in time the current token response expires
	 */
	public Instant getNotAfter() {
		getTokenResponse();
		return notAfter;
	}

	/**
	 * Validates a token response.
	 * 
	 * @param response token response
	 * @return fully verified ID token; null if no ID token was sent
	 */
	public WebToken validateTokenResponse(IuOidcTokenResponse response) {
		final var metadata = OidcProviders.getMetadata(config.getProvider());

		final String encryptedIdToken = response.getIdToken();
		if (encryptedIdToken == null)
			return null;

		final var oidcClient = config.getClient();
		final var clientId = oidcClient.getClientId();
		final var decryptKeys = oidcClient.getDecryptJwk();

		final String idToken;
		if (decryptKeys != null) {
			final var jose = WebCryptoHeader.getProtectedHeader(encryptedIdToken);
			final var kid = Objects.requireNonNull(jose.getKeyId(), "ID token header missing decryption key ID");
			final var decryptJwk = IuIterable.select(decryptKeys, k -> kid.equals(k.getKeyId()),
					"decryption key not found using kid " + kid);
			idToken = WebEncryption.parse(encryptedIdToken).decryptText(decryptJwk);
		} else
			idToken = encryptedIdToken; // not encrypted

		final var jose = WebCryptoHeader.getProtectedHeader(idToken);
		final var alg = Objects.requireNonNull(jose.getAlgorithm(), "ID token header missing signature algorithm");
		final var kid = Objects.requireNonNull(jose.getKeyId(), "ID token header missing issuer key ID");

		final var issuerKey = IuIterable.select(WebKey.readJwks(metadata.getJwksUri()), k -> kid.equals(k.getKeyId()),
				"issuer key not found using kid " + kid);

		final var verifiedIdToken = WebToken.verify(idToken, issuerKey);
		verifiedIdToken.validateClaims(metadata.getIssuer(), URI.create(clientId), oidcClient.getTokenTtl());

		final var azp = verifiedIdToken.getClaim("azp", String.class);
		if (azp != null)
			IuObject.once(clientId, azp, "azp must match client_id");

		final var maxAge = oidcClient.getMaxAge();
		if (maxAge != null) {
			final var authTime = Objects.requireNonNull(verifiedIdToken.getClaim("auth_time", Instant.class),
					"Missing auth_time claim");
			final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
			final var authAge = Duration.between(authTime, now);
			if (authAge.compareTo(maxAge) >= 0)
				throw new IllegalArgumentException(
						"Authenticated session lifetime " + authAge + " exceeds maximum " + maxAge);
		}

		final var accessToken = response.getAccessToken();
		if (accessToken != null) {
			final var atHash = Objects.requireNonNull(verifiedIdToken.getClaim("at_hash", String.class),
					"Missing at_hash claim");
			final var sha = IuException.unchecked(() -> MessageDigest.getInstance("SHA-" + alg.size));
			final var hash = sha.digest(IuText.ascii(accessToken));
			final var halfHash = Arrays.copyOfRange(hash, 0, alg.size / 16);
			if (!IuText.base64Url(halfHash).equals(atHash))
				throw new IllegalArgumentException("at_hash mismatch");
		}

		return verifiedIdToken;
	}

	/**
	 * Gets the most recently verified token response.
	 * 
	 * @return token response
	 */
	public IuOidcTokenResponse getTokenResponse() {
		if (tokenResponse == null //
				|| Instant.now().isAfter(notAfter)) {

			final var metadata = OidcProviders.getMetadata(config.getProvider());
			final var tokenEndpoint = metadata.getTokenEndpoint();

			final var tokenResponse = config.adaptJson(IuOidcTokenResponse.class).fromJson(
					IuException.unchecked(() -> IuHttp.send(tokenEndpoint, this::tokenAuth, IuHttp.READ_JSON_OBJECT)));

			idToken = validateTokenResponse(tokenResponse);

			final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
			notAfter = IuObject.require(now.plusSeconds(tokenResponse.getExpiresIn()), now::isBefore,
					"non-positive expires_in");
			this.tokenResponse = tokenResponse;
		}

		return tokenResponse;
	}

}
