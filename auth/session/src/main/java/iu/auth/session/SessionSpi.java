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
package iu.auth.session;

import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import javax.security.auth.Subject;

import com.auth0.jwt.JWT;

import edu.iu.IdGenerator;
import edu.iu.IuObject;
import edu.iu.auth.oauth.IuAuthorizationScope;
import edu.iu.auth.session.IuSessionAttribute;
import edu.iu.auth.session.IuSessionHeader;
import edu.iu.auth.session.IuSessionProviderKey;
import edu.iu.auth.session.IuSessionToken;
import edu.iu.auth.spi.IuSessionSpi;
import iu.auth.util.AccessTokenVerifier;
import iu.auth.util.AlgorithmFactory;
import iu.auth.util.TokenIssuerKeySet;

/**
 * Service provider implementation bootstrap.
 */
public class SessionSpi implements IuSessionSpi {

	private static final Set<String> STANDARD_CLAIMS = Set.of("kid", "alg", "jti", "iss", "aud", "iat", "exp", "sub",
			"scope");

	private static final class Id implements Principal {
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
			final var other = (Id) obj;
			return IuObject.equals(name, other.name);
		}

		@Override
		public String toString() {
			return "Authenticated Principal [name=" + name + "]";
		}
	}

	private static final class Issuer {
		private final Collection<String> scopes;
		private final AlgorithmFactory algorithmFactory;
		private final AccessTokenVerifier verifier;

		private Issuer(String issuer, Collection<String> scopes, AlgorithmFactory algorithmFactory) {
			this.scopes = scopes;
			this.algorithmFactory = algorithmFactory;
			this.verifier = new AccessTokenVerifier(issuer, algorithmFactory);
		}
	}

	private Map<String, Issuer> issuers = new HashMap<>();

	/**
	 * Default constructor.
	 */
	public SessionSpi() {
	}

	@Override
	public String register(Subject provider) {
		synchronized (issuers) {
			final Principal principal = provider.getPrincipals().iterator().next();
			final String issuer;
			try {
				issuer = URI.create(principal.getName()).toString();
			} catch (Throwable e) {
				throw new IllegalArgumentException("Issuer principal name must be a valid URI", e);
			}
			if (issuers.containsKey(issuer))
				throw new IllegalStateException("Issuer is already registered");

			final Queue<String> scopes = new ArrayDeque<>();
			for (final var scope : provider.getPrincipals(IuAuthorizationScope.class))
				if (issuer.equals(scope.getRealm()))
					scopes.add(scope.getName());
			if (scopes.isEmpty())
				throw new IllegalArgumentException("Issuer must define at least one scope");

			final var issuerKeys = provider.getPrivateCredentials(IuSessionProviderKey.class);
			if (issuerKeys.isEmpty())
				throw new IllegalArgumentException("Issuer must define at least one session provider key");

			final var keyset = new TokenIssuerKeySet(issuerKeys);
			final var wellKnownJwks = keyset.publish();
			issuers.put(issuer, new Issuer(issuer, scopes, keyset));

			return wellKnownJwks;
		}
	}

	@Override
	public void register(String realm, URI jwksUri, Duration refreshInterval) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

	@Override
	public IuSessionToken create(IuSessionHeader header) {
		final var issuer = Objects.requireNonNull(header.getIssuer(), "issuer");
		final var issuerRegistration = Objects.requireNonNull(issuers.get(issuer), "issuer");
		final var audience = Objects.requireNonNull(header.getAudience(), "audience");
		final var keyId = Objects.requireNonNull(header.getKeyId(), "keyId");
		final var algorithm = issuerRegistration.algorithmFactory.getAlgorithm(keyId,
				Objects.requireNonNull(header.getSignatureAlgorithm(), "signatureAlgorithm"));

		final var principals = header.getAuthorizedPrincipals().iterator();
		final var principal = principals.next();
		final var sub = principal.getName();

		final var subject = new Subject();
		subject.getPrincipals().add(new Id(sub));

		final var jti = IdGenerator.generateId();

		final var now = Instant.now();
		final var tokenExpires = now.plus(header.getTokenExpires());
		final var sessionExpires = now.plus(header.getSessionExpires());

		final var accessTokenBuilder = JWT.create() //
				.withKeyId(keyId) //
				.withJWTId(jti) //
				.withIssuer(issuer) //
				.withAudience(audience) //
				.withIssuedAt(now) //
				.withExpiresAt(tokenExpires) //
				.withSubject(principal.getName());

		final Set<String> claims = new HashSet<>(STANDARD_CLAIMS);
		final Queue<String> scopes = new ArrayDeque<>();

		while (principals.hasNext()) {
			final var secondary = principals.next();
			if (secondary instanceof IuAuthorizationScope) {
				final var scope = (IuAuthorizationScope) secondary;
				if (issuer.equals(scope.getRealm()))
					scopes.add(scope.getName());
				else
					throw new IllegalArgumentException("scope realm/issuer mismatch");
			} else if (secondary instanceof IuSessionAttribute) {
				final var attr = (IuSessionAttribute<?>) secondary;
				if (principal.getName().equals(attr.getName())) {
					final var name = attr.getAttributeName();
					if (!claims.add(name))
						throw new IllegalArgumentException("duplicate or invalid session attribute claim");

					final var value = attr.getAttributeValue();
					if (value instanceof String)
						accessTokenBuilder.withClaim(name, (String) value);
					else
						// TODO: single-value conversion
						throw new UnsupportedOperationException("unsupported session attribute value type");
				}
			} else
				throw new IllegalArgumentException("invalid principal for session token");

			subject.getPrincipals().add(secondary);
		}

		final String scope;
		if (scopes.isEmpty())
			throw new IllegalArgumentException("must provide at least one scope");
		else
			scope = String.join(",", scopes);
		accessTokenBuilder.withClaim("scope", scope);

		final var accessToken = accessTokenBuilder.sign(algorithm);

		final String refreshToken;
		if (header.isRefresh()) {
			final var refreshTokenBuilder = JWT.create() //
					.withKeyId(keyId) //
					.withIssuer(issuer) //
					.withAudience(issuer) //
					.withIssuedAt(now) //
					.withExpiresAt(sessionExpires) //
					.withSubject(audience) //
					.withClaim("principal", principal.getName()) //
					.withClaim("scope", scope);
			// TODO: review with refresh() implementation

			refreshToken = refreshTokenBuilder.sign(algorithm);
		} else
			refreshToken = null;

		subject.setReadOnly();
		return new SessionToken(subject, accessToken, refreshToken, tokenExpires, sessionExpires);
	}

	@Override
	public IuSessionToken refresh(Iterable<Principal> authorizedPrincipals, String refreshToken) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

	@Override
	public IuSessionToken authorize(String audience, String accessToken) {
		final var decoded = JWT.decode(accessToken);
		final var issuer = Objects.requireNonNull(decoded.getIssuer(), "issuer");
		final var issuerRegistration = Objects.requireNonNull(issuers.get(issuer), "issuer not registered");
		final var verifiedAccessToken = issuerRegistration.verifier.verify(audience, accessToken);

		final var subject = new Subject();
		final var principals = subject.getPrincipals();

		final var sub = Objects.requireNonNull(verifiedAccessToken.getSubject(), "sub");
		principals.add(new Id(sub));

		for (final var claimEntry : decoded.getClaims().entrySet())
			if (!STANDARD_CLAIMS.contains(claimEntry.getKey()))
				principals.add(IuSessionAttribute.of(sub, claimEntry.getKey(), claimEntry.getValue().asString()));

		final var scopes = Objects.requireNonNull(verifiedAccessToken.getClaim("scope").asString()).split(" ");
		for (final var scope : scopes)
			if (!issuerRegistration.scopes.contains(scope))
				throw new IllegalArgumentException("invalid scope for issuer");
			else
				principals.add(IuAuthorizationScope.of(scope, issuer));

		return new SessionToken(subject, accessToken, null, decoded.getExpiresAtAsInstant(), null);
	}

	@Override
	public <T> IuSessionAttribute<T> createAttribute(String name, String attributeName, T attributeValue) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

}
