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

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.Principal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.security.auth.Subject;

import com.auth0.jwt.interfaces.DecodedJWT;

import edu.iu.IdGenerator;
import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuOutOfServiceException;
import edu.iu.IuText;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuTokenResponse;
import edu.iu.auth.oidc.IuOpenIdClient;
import iu.auth.util.AccessTokenVerifier;
import iu.auth.util.HttpUtils;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * OpenID Connect {@link IuAuthorizationClient} implementation.
 */
class OidcAuthorizationClient implements IuAuthorizationClient {

	private static final Iterable<String> OIDC_SCOPE = IuIterable.iter("openid");
	private static final Predicate<String> IS_OIDC = "openid"::equals;

	private static class Id implements Principal, Serializable {
		private static final long serialVersionUID = 1L;

		private final String name;

		private Id(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int hashCode() {
			return IuObject.hashCode(name);
		}

		@Override
		public boolean equals(Object obj) {
			if (!IuObject.typeCheck(this, obj))
				return false;
			Id other = (Id) obj;
			return IuObject.equals(name, other.name);
		}
	}

	private final String realm;
	private final URI authorizationEndpoint;
	private final URI tokenEndpoint;
	private final URI userinfoEndpoint;
	private final IuOpenIdClient client;
	private final AccessTokenVerifier idTokenVerifier;

	private final Set<String> nonces = new HashSet<>();

	/**
	 * Constructor.
	 * 
	 * @param config          parsed OIDC provider configuration
	 * @param client          client configuration metadata
	 * @param idTokenVerifier ID token verifier
	 */
	OidcAuthorizationClient(JsonObject config, IuOpenIdClient client, AccessTokenVerifier idTokenVerifier) {
		realm = config.getString("issuer");
		authorizationEndpoint = IuException.unchecked(() -> new URI(config.getString("authorization_endpoint")));
		tokenEndpoint = IuException.unchecked(() -> new URI(config.getString("token_endpoint")));
		userinfoEndpoint = IuException.unchecked(() -> new URI(config.getString("userinfo_endpoint")));
		this.client = client;
		this.idTokenVerifier = idTokenVerifier;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public URI getAuthorizationEndpoint() {
		return authorizationEndpoint;
	}

	@Override
	public URI getTokenEndpoint() {
		return tokenEndpoint;
	}

	@Override
	public URI getRedirectUri() {
		return client.getRedirectUri();
	}

	@Override
	public Map<String, String> getAuthorizationCodeAttributes() {
		final var nonce = IdGenerator.generateId();
		synchronized (nonces) {
			nonces.add(nonce);
		}
		return Map.of("nonce", nonce);
	}

	@Override
	public Map<String, String> getClientCredentialsAttributes() {
		return client.getClientCredentialsAttributes();
	}

	@Override
	public IuApiCredentials getCredentials() {
		return client.getCredentials();
	}

	@Override
	public Duration getAuthenticationTimeout() {
		return client.getAuthenticationTimeout();
	}

	@Override
	public URI getResourceUri() {
		return client.getResourceUri();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Iterable<String> getScope() {
		final var scope = client.getScope();
		if (scope == null)
			return OIDC_SCOPE;
		else
			return (Iterable<String>) IuIterable.cat(OIDC_SCOPE, IuIterable.filter(scope, a -> !"openid".equals(a)));
	}

	@Override
	public Subject verify(IuTokenResponse tokenResponse) throws IuAuthenticationException, IuBadRequestException,
			IuAuthorizationFailedException, IuOutOfServiceException, IllegalStateException {
		if (!IuIterable.filter(tokenResponse.getScope(), IS_OIDC).iterator().hasNext())
			throw new IuAuthorizationFailedException("missing openid scope");

		final var accessToken = tokenResponse.getAccessToken();

		// TODO: STARCH-595 resolve String cast
		final var clientId = client.getCredentials().getName();
		final var idToken = tokenResponse.getTokenAttributes().get("id_token");
		final DecodedJWT verifiedIdToken;
		if (idToken != null) {
			verifiedIdToken = idTokenVerifier.verify(clientId, (String) idToken);
			if (!"RS256".equals(verifiedIdToken.getAlgorithm()))
				throw new IllegalArgumentException("RS256 required");

			final var encodedhash = IuCrypt.sha256(IuText.utf8(accessToken));
			final var halfOfEncodedHash = Arrays.copyOf(encodedhash, (encodedhash.length / 2));
			final var atHashGeneratedfromAccessToken = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(halfOfEncodedHash);

			final var atHash = Objects.requireNonNull(verifiedIdToken.getClaim("at_hash").asString(), "at_hash");
			if (!atHash.equals(atHashGeneratedfromAccessToken))
				throw new IllegalStateException("Invalid at_hash");

			final var nonce = verifiedIdToken.getClaim("nonce").asString();
			IdGenerator.verifyId(nonce, client.getAuthenticationTimeout().toMillis());
			synchronized (nonces) {
				if (!nonces.remove(nonce))
					throw new IllegalArgumentException("Invalid nonce");
			}
		} else
			verifiedIdToken = null;

		final var userinfo = HttpUtils.read(HttpRequest.newBuilder(userinfoEndpoint) //
				.header("Authorization", "Bearer " + accessToken).build()).asJsonObject();
		final var principal = userinfo.getString("principal");
		final var sub = userinfo.getString("sub");
		if (clientId.equals(principal) && clientId.equals(sub))
			return new Subject(true, Set.of(new Id(principal)), Set.of(), Set.of());

		if (idToken == null)
			throw new IllegalStateException("Token response missing id_token");

		final Set<String> seen = new HashSet<>();
		final var subject = new Subject();
		final var principals = subject.getPrincipals();
		final BiConsumer<String, Supplier<?>> claimConsumer = //
				(claimName, claimSupplier) -> {
					if (seen.add(claimName))
						principals.add(new OidcClaim<>(principal, claimName,
								Objects.requireNonNull(claimSupplier.get(), claimName)));
				};

		claimConsumer.accept("principal", () -> principal);
		claimConsumer.accept("sub", () -> sub);
		claimConsumer.accept("aud", () -> clientId);
		claimConsumer.accept("iat", verifiedIdToken::getIssuedAtAsInstant);
		claimConsumer.accept("exp", verifiedIdToken::getExpiresAtAsInstant);
		claimConsumer.accept("auth_time", verifiedIdToken.getClaim("auth_time")::asInstant);

		for (final var userinfoClaimEntry : userinfo.entrySet())
			claimConsumer.accept(userinfoClaimEntry.getKey(), () -> {
				final var claimJsonValue = userinfoClaimEntry.getValue();
				if (claimJsonValue instanceof JsonString js)
					return js.getString();
				else
					return claimJsonValue.toString();
			});

		return subject;
	}

	@Override
	public Subject verify(IuTokenResponse refreshTokenResponse, IuTokenResponse originalTokenResponse)
			throws IuAuthenticationException, IuBadRequestException, IuAuthorizationFailedException,
			IuOutOfServiceException, IllegalStateException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void activate(IuApiCredentials credentials) throws IuAuthenticationException, IuBadRequestException,
			IuAuthorizationFailedException, IuOutOfServiceException, IllegalStateException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
