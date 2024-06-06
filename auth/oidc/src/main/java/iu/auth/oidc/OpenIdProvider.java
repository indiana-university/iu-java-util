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
package iu.auth.oidc;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import iu.auth.oauth.IuAuthorizationClient;
import jakarta.json.JsonObject;

/**
 * {@link IuOpenIdProvider} implementation.
 */
class OpenIdProvider implements IuOpenIdProvider {

	private final IuOpenIdClient client;
	private final IuAuthorizationGrant clientCredentials;
	private JsonObject config;

	/**
	 * Constructor.
	 * 
	 * @param client client configuration metadata
	 */
	OpenIdProvider(IuOpenIdClient client) {
		this.client = client;
		if (client instanceof IuAuthoritativeOpenIdClient)
			clientCredentials = IuAuthorizationClient.initialize(new OidcAuthorizationClient(this));
		else
			clientCredentials = null;
	}

	@Override
	public IuAuthorizationGrant clientCredentials() {
		return Objects.requireNonNull(clientCredentials, "Not authoritative for " + client.getRealm());
	}

	@Override
	public OidcPrincipal hydrate(String accessToken) throws IuAuthenticationException {
		return new OidcPrincipal(null, accessToken, this);
	}

	/**
	 * Gets the client configuration.
	 * 
	 * @return client configuration
	 */
	IuOpenIdClient client() {
		return client;
	}

	/**
	 * Gets the authoritative client configuration.
	 * 
	 * @return authoritative client configuration
	 * @throws ClassCastException if client is not an instance of
	 *                            {@link IuAuthoritativeOpenIdClient}.
	 */
	IuAuthoritativeOpenIdClient authClient() {
		return (IuAuthoritativeOpenIdClient) client;
	}

	/**
	 * Gets the OpenID Provider configuration, parsed as a JSON object.
	 * 
	 * @return {@link JsonObject} OP configuration
	 */
	JsonObject config() {
		if (config == null)
			config = IuException.unchecked(() -> IuHttp.get(client.getProviderConfigUri(), IuHttp.READ_JSON_OBJECT));
		return config;
	}

	/**
	 * Gets claims from the OP userinfo endpoint.
	 * 
	 * @param accessToken OIDC access token
	 * @return {@link JsonObject} parsed userinfo claims
	 */
	Map<String, ?> userinfo(String accessToken) {
		// TODO: support signed/encrypted content
		return IuJsonAdapter.<Map<String, ?>>basic()
				.fromJson(IuException.unchecked(
						() -> IuHttp.send(IuJson.get(config(), "userinfo_endpoint", IuJsonAdapter.of(URI.class)),
								b -> b.header("Authorization", "Bearer " + accessToken), IuHttp.READ_JSON_OBJECT)));
	}

	/**
	 * Verifies tokens and returns consolidated claims from both ID token and
	 * userinfo endpoint.
	 * 
	 * @param idToken     OIDC ID token
	 * @param accessToken OIDC Access token
	 * @return consolidated claims
	 */
	Map<String, ?> getClaims(String idToken, String accessToken) {
		if (!(client instanceof IuAuthoritativeOpenIdClient))
			return userinfo(accessToken);

		final var authClient = (IuAuthoritativeOpenIdClient) client;
		final var clientId = authClient.getCredentials().getName();
		if (idToken == null) {
			final var userinfo = userinfo(accessToken);
			final var principal = (String) Objects.requireNonNull(userinfo.get("principal"),
					"Userinfo missing principal claim");
			final var sub = (String) Objects.requireNonNull(userinfo.get("sub"), "Userinfo missing sub claim");
			if (clientId.equals(principal) //
					&& sub.equals(principal))
				return userinfo;
			else
				throw new IllegalArgumentException("Missing ID token");
		}

		final var idTokenAlgorithm = authClient.getIdTokenAlgorithm();
		final var verifiedIdToken = IuWebToken.from(idToken);
		if (!idTokenAlgorithm.equals(verifiedIdToken.getAlgorithm()))
			throw new IllegalArgumentException(idTokenAlgorithm + " required");

		final var encodedHash = IuException
				.unchecked(() -> MessageDigest.getInstance("SHA-256").digest(IuText.utf8(accessToken)));
		final var halfOfEncodedHash = Arrays.copyOf(encodedHash, (encodedHash.length / 2));
		final var atHashGeneratedfromAccessToken = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(halfOfEncodedHash);

		final var atHash = Objects.requireNonNull(verifiedIdToken.getClaim("at_hash"), "at_hash");
		if (!atHash.equals(atHashGeneratedfromAccessToken))
			throw new IllegalArgumentException("Invalid at_hash");

		final String nonce = verifiedIdToken.getClaim("nonce");
		authClient.verifyNonce(nonce);

		final var now = Instant.now();
		final Instant authTime = verifiedIdToken.getClaim("auth_time");
		final var authExpires = authTime.plus(authClient.getAuthenticatedSessionTimeout());
		if (now.isAfter(authExpires))
			throw new IllegalArgumentException("OIDC authenticated session is expired");

		// TODO: provide directly from ID Token JWT payload
		final Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("sub", verifiedIdToken.getSubject());
		claims.put("aud", verifiedIdToken.getAudience().iterator().next());
		claims.put("iat", verifiedIdToken.getIssuedAt());
		claims.put("exp", verifiedIdToken.getExpires());
		claims.put("auth_time", authTime);

		for (final var userinfoClaimEntry : userinfo(accessToken).entrySet())
			claims.compute(userinfoClaimEntry.getKey(),
					(name, value) -> IuObject.once(value, userinfoClaimEntry.getValue()));

		return Collections.unmodifiableMap(claims);
	}

}
