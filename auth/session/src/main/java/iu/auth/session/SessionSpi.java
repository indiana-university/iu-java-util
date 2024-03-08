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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.security.auth.Subject;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuAuthorizationScope;
import edu.iu.auth.session.IuSessionAttribute;
import edu.iu.auth.session.IuSessionHeader;
import edu.iu.auth.session.IuSessionProviderKey;
import edu.iu.auth.session.IuSessionToken;
import edu.iu.auth.spi.IuSessionSpi;
import iu.auth.util.AccessTokenVerifier;
import iu.auth.util.AlgorithmFactory;
import iu.auth.util.PrincipalVerifierRegistry;
import iu.auth.util.TokenIssuerKeySet;
import iu.auth.util.WellKnownKeySet;

/**
 * Service provider implementation bootstrap.
 */
public class SessionSpi implements IuSessionSpi {

	private static final Set<String> STANDARD_CLAIMS = Set.of("kid", "alg", "jti", "iss", "aud", "iat", "exp", "sub",
			"realm", "principal", "scope");
	private static final Pattern CHALLENGE_ATTRIBUTE_REGEX = Pattern
			.compile("(\\w+)=\"([\\x20-\\x21,\\x23-\\x5B,\\x5D-\\x7E]+)\"");

	private static class Id implements IuPrincipalIdentity {
		private static final long serialVersionUID = 1L;

		private final String realm;
		private final String issuer;
		private final String name;

		private Id(String realm, String issuer, String name) {
			this.realm = realm;
			this.issuer = issuer;
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int hashCode() {
			return IuObject.hashCode(realm, issuer, name);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Id))
				return false;
			final var other = (Id) obj;
			return IuObject.equals(realm, other.realm) //
					&& IuObject.equals(issuer, other.issuer) //
					&& IuObject.equals(name, other.name);
		}

		@Override
		public String toString() {
			return "Session Principal [" + name + "], issued by " + issuer + " for realm " + realm;
		}
	}

	private static class VerifiedId extends Id {
		private static final long serialVersionUID = 1L;

		private final IuPrincipalIdentity id;

		private VerifiedId(String realm, IuPrincipalIdentity id, Issuer issuer) {
			super(realm, issuer.uri.toString(), id.getName());
			this.id = id;
		}

		@Override
		public String toString() {
			return "Verified " + super.toString();
		}
	}

	private static final class Issuer {
		private final URI uri;
		private final Supplier<Set<String>> realm;
		private final Supplier<Collection<String>> scopes;
		private final AlgorithmFactory algorithmFactory;
		private final AccessTokenVerifier verifier;

		private Issuer(URI uri, Supplier<Set<String>> realm, Supplier<Collection<String>> scopes,
				AlgorithmFactory algorithmFactory) {
			this.uri = uri;
			this.realm = realm;
			this.scopes = scopes;
			this.algorithmFactory = algorithmFactory;
			this.verifier = new AccessTokenVerifier(uri.toString(), algorithmFactory);
		}
	}

	private Map<String, Issuer> issuers = new HashMap<>();

	/**
	 * Default constructor.
	 */
	public SessionSpi() {
	}

	@Override
	public String register(Set<String> realm, Subject provider) {
		synchronized (issuers) {
			final var principals = provider.getPrincipals(IuPrincipalIdentity.class).iterator();
			final Principal principal = principals.next();
			if (principals.hasNext())
				throw new IllegalArgumentException();

			final URI issuer;
			final String iss;
			try {
				issuer = URI.create(principal.getName());
				iss = issuer.toString();
			} catch (Throwable e) {
				throw new IllegalArgumentException("Issuer principal name must be a valid URI", e);
			}
			if (issuers.containsKey(iss))
				throw new IllegalStateException("Issuer is already registered");

			final Queue<String> scopes = new ArrayDeque<>();
			for (final var scope : provider.getPrincipals(IuAuthorizationScope.class))
				if (iss.equals(scope.getRealm()))
					scopes.add(scope.getName());
				else
					throw new IllegalArgumentException("Invalid scope for issuer");
			if (scopes.isEmpty())
				throw new IllegalArgumentException("Issuer must define at least one scope");

			final var issuerKeys = provider.getPrivateCredentials(IuSessionProviderKey.class);
			if (issuerKeys.isEmpty())
				throw new IllegalArgumentException("Issuer must define at least one session provider key");

			final var keyset = new TokenIssuerKeySet(issuerKeys);
			final var wellKnownJwks = keyset.publish();
			final var issuerRegistration = new Issuer(issuer, () -> realm, () -> scopes, keyset);

			PrincipalVerifierRegistry.registerVerifier(iss, id -> {
				if (id == principal)
					return;

				final var verifiedRealm = ((Id) id).realm;
				if (!realm.contains(verifiedRealm))
					throw new IllegalArgumentException();

				if (id instanceof VerifiedId)
					IuPrincipalIdentity.verify(((VerifiedId) id).id, verifiedRealm);

			}, true);

			issuers.put(iss, issuerRegistration);

			return wellKnownJwks;
		}
	}

	@Override
	public void register(URI issuer, URI jwksUri, Duration tokenTtl, Duration refreshInterval) {
		class ResolvedIssuer {
			private Set<String> realms;
			private Collection<String> scopes;

			private Set<String> getRealms() {
				resolveMetadata();
				return realms;
			}

			private Collection<String> getScopes() {
				resolveMetadata();
				return scopes;
			}

			private void resolveMetadata() {
				if (realms == null)
					IuException.unchecked(() -> {
						final var client = HttpClient.newHttpClient();
						final var knock = client.send(HttpRequest.newBuilder(issuer).build(),
								BodyHandlers.discarding());
						final var headers = knock.headers();
						final var status = knock.statusCode();
						if (status != 401)
							throw new IllegalStateException(
									"Issuer knock at " + issuer + " didn't return 401 UNAUTHORIZED; status=" + status
											+ " headers=" + headers.map());

						final var challenge = headers.firstValue("WWW-Authenticate").get();
						final var parsedChallenge = challenge.split(" ");
						if (parsedChallenge.length < 3)
							throw new IllegalStateException(
									"Incomplete WWW-Authenticate header; not enough attributes " + challenge);

						if (!"Bearer".equals(parsedChallenge[0]))
							throw new IllegalStateException(
									"Incomplete WWW-Authenticate header; missing 'Bearer' " + challenge);

						for (var i = 1; i < parsedChallenge.length; i++) {
							final var matcher = CHALLENGE_ATTRIBUTE_REGEX.matcher(parsedChallenge[i]);
							if (!matcher.matches())
								throw new IllegalStateException(
										"Malformed WWW-Authenticate header; invalid attribute " + challenge);

							final var name = matcher.group(1);
							final var value = matcher.group(2);
							if (name.equals("scope"))
								scopes = Arrays.asList(value.split(" "));
							else if (name.equals("realm"))
								realms = Set.of(name.split(" "));
						}

						if (realms == null || realms.iterator().next().isBlank())
							throw new IllegalStateException(
									"Malformed WWW-Authenticate header; missing realm " + challenge);

						if (scopes == null || scopes.iterator().next().isBlank())
							throw new IllegalStateException(
									"Malformed WWW-Authenticate header; missing scope " + challenge);
					});
			}
		}
		final var resolvedIssuer = new ResolvedIssuer();

		final var issuerRegistration = new Issuer(issuer, resolvedIssuer::getRealms, resolvedIssuer::getScopes,
				new WellKnownKeySet(jwksUri, () -> refreshInterval));

		PrincipalVerifierRegistry.registerVerifier(issuer.toString(), id -> {
			if (!(id instanceof Id) || (id instanceof VerifiedId))
				throw new IllegalArgumentException();

			final var verifiedRealm = ((Id) id).realm;
			if (!resolvedIssuer.getRealms().contains(verifiedRealm))
				throw new IllegalArgumentException();
		}, false);

		issuers.put(issuer.toString(), issuerRegistration);
	}

	@Override
	public IuSessionToken create(IuSessionHeader header) {
		final var issuer = Objects.requireNonNull(header.getIssuer(), "issuer");
		final var issuerRegistration = Objects.requireNonNull(issuers.get(issuer), "issuer");
		final var realm = Objects.requireNonNull(header.getRealm(), "realm");
		if (!issuerRegistration.realm.get().contains(realm))
			throw new IllegalArgumentException();

		if (!PrincipalVerifierRegistry.isAuthoritative(issuer))
			throw new IllegalStateException("Not authoritative " + issuer);

		final var keyId = Objects.requireNonNull(header.getKeyId(), "keyId");
		final var algorithm = issuerRegistration.algorithmFactory.getAlgorithm(keyId,
				Objects.requireNonNull(header.getSignatureAlgorithm(), "signatureAlgorithm"));

		final var subject = Objects.requireNonNull(header.getSubject(), "subject");
		final var audience = Objects.requireNonNull(header.getAudience(), "audience");

		final var issuedAt = Instant.now();
		final var tokenTtl = header.getTokenExpires();

		if (header.isRefresh()) {
			final var sessionExpires = issuedAt.plus(header.getSessionExpires()).plus(999L, ChronoUnit.MILLIS)
					.truncatedTo(ChronoUnit.SECONDS);
			return createToken(realm, algorithm, keyId, issuerRegistration, audience, subject, issuedAt, tokenTtl,
					sessionExpires, null);
		} else
			return createToken(realm, algorithm, keyId, issuerRegistration, audience, subject, issuedAt, tokenTtl, null,
					null);
	}

	@Override
	public IuSessionToken refresh(Subject subject, String refreshToken) {
		final var decoded = JWT.decode(refreshToken);
		final var issuer = Objects.requireNonNull(decoded.getIssuer(), "issuer");
		final var issuerRegistration = Objects.requireNonNull(issuers.get(issuer), "issuer not registered");

		final var verifiedRefreshToken = issuerRegistration.verifier.verify(issuer, refreshToken);

		final var realm = Objects.requireNonNull(verifiedRefreshToken.getClaim("realm").asString(), "realm");
		if (!issuerRegistration.realm.get().contains(realm))
			throw new IllegalArgumentException();

		final var audience = Objects.requireNonNull(verifiedRefreshToken.getSubject(), "audience");
		if (!PrincipalVerifierRegistry.isAuthoritative(audience))
			throw new IllegalArgumentException();

		final var keyId = verifiedRefreshToken.getKeyId();
		final var algorithm = issuerRegistration.algorithmFactory.getAlgorithm(keyId,
				Objects.requireNonNull(verifiedRefreshToken.getAlgorithm(), "signatureAlgorithm"));

		final var subhash = Objects.requireNonNull(verifiedRefreshToken.getClaim("sha").asString(), "sha");

		final var tokenTtl = Duration.parse(verifiedRefreshToken.getClaim("ttl").asString());
		final var sessionExpires = verifiedRefreshToken.getExpiresAtAsInstant();
		final var issuedAt = Instant.now();

		if (!sessionExpires.isBefore(issuedAt.plus(tokenTtl)))
			return createToken(realm, algorithm, keyId, issuerRegistration, audience, subject, issuedAt, tokenTtl,
					sessionExpires, subhash);
		else
			return createToken(realm, algorithm, keyId, issuerRegistration, audience, subject, issuedAt, tokenTtl, null,
					subhash);
	}

	private IuSessionToken createToken(String realm, Algorithm algorithm, String keyId, Issuer issuer, String audience,
			Subject subject, Instant issuedAt, Duration tokenTtl, Instant sessionExpires, String refreshSubhash) {
		final var tokenExpires = issuedAt.plus(tokenTtl).plus(999L, ChronoUnit.MILLIS).truncatedTo(ChronoUnit.SECONDS);

		final var sessionSubject = new Subject();
		final var principal = IuPrincipalIdentity.from(subject, realm);
		sessionSubject.getPrincipals().add(new VerifiedId(realm, principal, issuer));

		final var iss = issuer.uri.toString();
		final var jti = IdGenerator.generateId();
		final var accessTokenBuilder = JWT.create() //
				.withKeyId(keyId) //
				.withJWTId(jti) //
				.withIssuer(iss) //
				.withAudience(audience) //
				.withIssuedAt(issuedAt) //
				.withExpiresAt(tokenExpires) //
				.withClaim("realm", realm) //
				.withSubject(principal.getName());

		final var tohash = new StringBuilder();
		tohash.append(issuer);
		tohash.append(principal.getName());

		final Set<String> claims = new HashSet<>(STANDARD_CLAIMS);
		final Queue<String> scopes = new ArrayDeque<>();
		for (final var p : subject.getPrincipals())
			if (p != principal) {
				if (p instanceof IuAuthorizationScope) {
					final var scope = (IuAuthorizationScope) p;
					if (iss.equals(scope.getRealm()))
						scopes.add(scope.getName());
					else
						throw new IllegalArgumentException("scope realm/issuer mismatch");
				} else if (p instanceof IuSessionAttribute) {
					final var attr = (IuSessionAttribute<?>) p;
					if (principal.getName().equals(attr.getName())) {
						final var name = attr.getAttributeName();
						if (!claims.add(name))
							throw new IllegalArgumentException("duplicate or invalid session attribute claim");

						final var value = attr.getAttributeValue();
						tohash.append(name).append(value);

						if (value instanceof String)
							accessTokenBuilder.withClaim(name, (String) value);
						else
							// TODO: single-value conversion
							throw new UnsupportedOperationException("unsupported session attribute value type");
					}
				} else
					throw new IllegalArgumentException("invalid principal for session token");

				sessionSubject.getPrincipals().add(p);
			}

		final String scope;
		if (scopes.isEmpty())
			throw new IllegalArgumentException("must provide at least one scope");
		else
			scope = String.join(",", scopes);
		accessTokenBuilder.withClaim("scope", scope);

		tohash.append(scope);

		final var subhash = IuException.unchecked(() -> Base64.getEncoder()
				.encodeToString(MessageDigest.getInstance("SHA-256").digest(tohash.toString().getBytes("UTF-8"))));
		if (refreshSubhash != null && !refreshSubhash.equals(subhash))
			throw new IllegalArgumentException();

		final var accessToken = accessTokenBuilder.sign(algorithm);

		sessionSubject.setReadOnly();
		if (sessionExpires != null) {
			final var refreshToken = JWT.create() //
					.withKeyId(keyId) //
					.withIssuer(iss) //
					.withAudience(iss) //
					.withIssuedAt(issuedAt) //
					.withExpiresAt(sessionExpires) //
					.withSubject(audience) //
					.withClaim("sha", subhash) //
					.withClaim("ttl", tokenTtl.toString()) //
					.withClaim("realm", realm) //
					.sign(algorithm);

			return new SessionToken(sessionSubject, accessToken, tokenExpires, refreshToken, sessionExpires);
		} else
			return new SessionToken(sessionSubject, accessToken, tokenExpires);
	}

	@Override
	public IuSessionToken authorize(String audience, String accessToken) {
		final var decoded = JWT.decode(accessToken);
		final var issuer = Objects.requireNonNull(decoded.getIssuer(), "issuer");
		final var issuerRegistration = Objects.requireNonNull(issuers.get(issuer), "issuer not registered");
		final var verifiedAccessToken = issuerRegistration.verifier.verify(audience, accessToken);

		final var subject = new Subject();
		final var principals = subject.getPrincipals();

		final var realm = Objects.requireNonNull(verifiedAccessToken.getClaim("realm").asString(), "realm");
		if (!issuerRegistration.realm.get().contains(realm))
			throw new IllegalArgumentException("Invalid realm for issuer");

		final var sub = Objects.requireNonNull(verifiedAccessToken.getSubject(), "sub");
		principals.add(new Id(realm, issuer, sub));

		for (final var claimEntry : decoded.getClaims().entrySet())
			if (!STANDARD_CLAIMS.contains(claimEntry.getKey()))
				principals.add(IuSessionAttribute.of(sub, claimEntry.getKey(), claimEntry.getValue().asString()));

		final var scopes = Objects.requireNonNull(verifiedAccessToken.getClaim("scope").asString()).split(" ");
		for (final var scope : scopes)
			if (issuerRegistration.scopes.get().contains(scope))
				principals.add(IuAuthorizationScope.of(scope, issuer));

		return new SessionToken(subject, accessToken, decoded.getExpiresAtAsInstant());
	}

	@Override
	public <T> IuSessionAttribute<T> createAttribute(String name, String attributeName, T attributeValue) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

}
