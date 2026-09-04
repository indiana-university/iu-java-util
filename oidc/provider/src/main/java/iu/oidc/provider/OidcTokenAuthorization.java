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
package iu.oidc.provider;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.config.OidcProviderConfiguration;

/**
 * What an access token this provider issued authorizes, once verified.
 *
 * <p>
 * An OpenID Provider's own endpoints &mdash; UserInfo among them &mdash; are
 * resource servers for the tokens it issues, so each has to verify one before
 * it answers. This does that once, in one place: it resolves the signing key
 * from the keys the provider publishes, verifies the signature, validates the
 * registered claims against the configured issuer, and reports the principal,
 * the client, and the granted scope the endpoint then acts on.
 * </p>
 *
 * <p>
 * The token is a string here rather than a request: reading it out of an
 * {@code Authorization} header is the caller's, and reporting the
 * {@code WWW-Authenticate} challenge on failure is too.
 * </p>
 *
 * <h2>Which failures are whose</h2>
 *
 * <p>
 * A token that cannot be trusted &mdash; unparseable, signed by a key this
 * provider doesn't publish, or failing the registered claims &mdash; raises
 * {@link SecurityException}, which is the caller's cue to answer
 * {@code invalid_token}. Configuration that isn't there to verify against
 * raises {@link NullPointerException} instead, so a provider missing its own
 * issuer or keys reads as a server fault rather than as a refused credential.
 * </p>
 */
public class OidcTokenAuthorization {

	/**
	 * Verifies an access token this provider issued.
	 *
	 * @param accessToken JWS compact serialization, as presented
	 * @param provider    provider configuration, supplying the issuer to match and
	 *                    the keys to verify against
	 * @param audience    resource the token must be addressed to, which for one of
	 *                    this provider's own endpoints is its issuer identifier
	 * @return verified authorization
	 * @throws SecurityException    if the token can't be verified, or its
	 *                              registered claims don't hold
	 * @throws NullPointerException if the provider declares no metadata, issuer,
	 *                              keys, or access token time to live
	 */
	public static OidcTokenAuthorization verify(String accessToken, OidcProviderConfiguration provider, URI audience) {
		final var issuer = Objects.requireNonNull(
				Objects.requireNonNull(provider.getMetadata(), "Missing provider metadata").getIssuer(),
				"Missing issuer");
		final var ttl = Objects.requireNonNull(provider.getAccessTokenTimeToLive(), "Missing access token TTL");

		final var issuerKey = issuerKey(accessToken, provider);

		final WebToken token;
		try {
			token = WebToken.verify(accessToken, issuerKey);
			token.validateClaims(issuer, audience, ttl);
		} catch (RuntimeException e) {
			// every way a token can fail reads the same to a caller, which answers
			// invalid_token; what distinguishes them is the cause, and that is kept
			throw new SecurityException("Invalid access token", e);
		}

		return new OidcTokenAuthorization(token);
	}

	/**
	 * Resolves the key an access token was signed with, from the keys this provider
	 * publishes.
	 *
	 * <p>
	 * Selected by the {@code kid} the token names, so a token signed by anything
	 * this provider doesn't publish is refused before its signature is examined.
	 * The header is read unverified, which is safe because it only chooses a
	 * candidate key: naming a key doesn't make a signature verify against it.
	 * </p>
	 *
	 * @param accessToken access token, as presented
	 * @param provider    provider configuration
	 * @return signing key
	 * @throws SecurityException if the token names no key, or names one this
	 *                           provider doesn't publish
	 */
	private static WebKey issuerKey(String accessToken, OidcProviderConfiguration provider) {
		final String keyId;
		try {
			keyId = WebCryptoHeader.getProtectedHeader(accessToken).getKeyId();
		} catch (RuntimeException e) {
			throw new SecurityException("Unreadable access token", e);
		}

		if (keyId == null)
			throw new SecurityException("Access token names no signing key");

		final var jwks = Objects.requireNonNull(provider.getJwks(), "Missing issuer keys");
		for (final var jwk : jwks)
			if (jwk != null //
					&& keyId.equals(jwk.getKeyId()))
				return jwk;

		throw new SecurityException("Access token signing key is not published by this issuer");
	}

	private final WebToken token;

	private OidcTokenAuthorization(WebToken token) {
		this.token = token;
	}

	/**
	 * Gets the principal the token was issued for, which is the identifier a
	 * UserInfo response binds its {@code sub} claim to.
	 *
	 * @return {@code sub} claim
	 */
	public String getSubject() {
		return token.getSubject();
	}

	/**
	 * Gets the client the token was issued to.
	 *
	 * @return {@code client_id} claim; null if the token names no client
	 */
	public String getClientId() {
		return (String) token.getClaim("client_id", String.class);
	}

	/**
	 * Gets the scope the grant behind this token was authorized for, which bounds
	 * the claims an endpoint may answer with.
	 *
	 * @return granted scope, in the order the token names it; empty if the token
	 *         carries no scope
	 */
	public Set<String> getScope() {
		final var scope = token.getScope();
		if (scope == null)
			return Set.of();

		final Set<String> scopes = new LinkedHashSet<>();
		for (final var value : scope.split(" "))
			if (!value.isEmpty())
				scopes.add(value);

		return Collections.unmodifiableSet(scopes);
	}

	/**
	 * Gets the verified token, for a caller that needs a claim this class doesn't
	 * report.
	 *
	 * @return verified {@link WebToken}
	 */
	public WebToken getToken() {
		return token;
	}

	@Override
	public String toString() {
		return "OidcTokenAuthorization [sub=" + getSubject() + ", client_id=" + getClientId() + ", scope=" + getScope()
				+ "]";
	}

}
