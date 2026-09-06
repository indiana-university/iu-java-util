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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

import edu.iu.IuDataStore;
import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.config.IuOidcClientAuthorization;
import edu.iu.oidc.config.IuOidcClientEndpoint;
import edu.iu.oidc.config.IuOidcProviderReference;

/**
 * Authenticates a client at the token endpoint.
 *
 * <p>
 * An endpoint may register more than one authorization record &mdash; to accept
 * a credential being rotated in alongside the one it replaces, say &mdash; and
 * what each record looks like decides which method it may accept, rather than
 * the client naming one:
 * </p>
 * <ul>
 * <li>A {@link WebKey.Type#RAW RAW} key whose {@code kid} is the client ID holds
 * the client's secret. It answers {@code client_secret_basic} and
 * {@code client_secret_post} by comparing the presented value, and
 * {@code client_secret_jwt} by verifying a MAC the same secret keyed &mdash;
 * which is why the secret is held as itself rather than as a digest of
 * itself.</li>
 * <li>Any other key is an asymmetric one and answers {@code private_key_jwt}.
 * When the registration also carries a revocation list it is a certificate
 * authority, and the assertion's own {@code x5c} chain is verified against it;
 * otherwise the registered key verifies the assertion directly.</li>
 * <li>A record with no key at all is an explicit public registration and answers
 * {@code none}. Nothing is verified, so only a grant that proves possession by
 * other means &mdash; an authorization code with PKCE &mdash; may be redeemed.
 * This is distinct from an endpoint that registers no authorization at all,
 * which accepts nothing.</li>
 * </ul>
 *
 * <p>
 * Rejections are {@link SecurityException}, which a token endpoint answers as
 * {@code invalid_client}. A fault that isn't the caller's &mdash; a defective
 * registration, say &mdash; propagates as itself.
 * </p>
 *
 * <p>
 * Which kind of verification a registration warrants is settled here; what does
 * the verifying comes from the
 * {@link IuOidcProviderReference reference}, since every implementation of
 * {@link edu.iu.pki.IuPkiVerifier} lives in a module this one has no business
 * compiling against.
 * </p>
 *
 * <p>
 * Nothing is held in memory across requests. A spent assertion is recorded in
 * the deployment's data store rather than on this instance, so replay is refused
 * across every node rather than only the one that verified the assertion.
 * </p>
 */
final class ClientAuthenticator {

	private static final Logger LOG = Logger.getLogger(ClientAuthenticator.class.getName());

	/** The only {@code client_assertion_type} OpenID Connect defines. */
	static final String JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

	/**
	 * Client authentication methods, as OpenID Connect names them.
	 */
	enum Method {

		/** Secret presented by HTTP Basic. */
		CLIENT_SECRET_BASIC("client_secret_basic"),

		/** Secret presented as a request parameter. */
		CLIENT_SECRET_POST("client_secret_post"),

		/** Assertion signed with the secret as a MAC key. */
		CLIENT_SECRET_JWT("client_secret_jwt"),

		/** Assertion signed with the client's private key. */
		PRIVATE_KEY_JWT("private_key_jwt"),

		/** No client authentication; possession is proven by the grant itself. */
		NONE("none");

		/** Value naming this method in metadata and in a log record. */
		final String parameterValue;

		private Method(String parameterValue) {
			this.parameterValue = parameterValue;
		}
	}

	/**
	 * A credential presented by a client.
	 *
	 * <p>
	 * Exactly one of {@code secret} and {@code assertion} is set; {@code method}
	 * names how a secret arrived, and is unused for an assertion since the
	 * registration decides whether it was signed with a secret or a private key.
	 * </p>
	 *
	 * @param method    method the client presented a secret by
	 * @param secret    secret presented, or {@code null}
	 * @param assertion assertion presented, or {@code null}
	 */
	record Credential(Method method, String secret, String assertion) {

		/**
		 * Names a secret presented by HTTP Basic.
		 *
		 * @param secret secret
		 * @return {@link Credential}
		 */
		static Credential basic(String secret) {
			return new Credential(Method.CLIENT_SECRET_BASIC, secret, null);
		}

		/**
		 * Names a secret presented as a request parameter.
		 *
		 * @param secret secret
		 * @return {@link Credential}
		 */
		static Credential post(String secret) {
			return new Credential(Method.CLIENT_SECRET_POST, secret, null);
		}

		/**
		 * Names an assertion.
		 *
		 * @param assertion assertion
		 * @return {@link Credential}
		 */
		static Credential assertion(String assertion) {
			return new Credential(null, null, assertion);
		}
	}

	/**
	 * Verifies a secret against the registered value.
	 *
	 * <p>
	 * Compared through {@link MessageDigest#isEqual} rather than
	 * {@link String#equals}: the comparison is the same, but it doesn't return
	 * early on the first differing byte, so how long a rejection takes says nothing
	 * about how much of the secret was right.
	 * </p>
	 *
	 * @param jwk    RAW key whose {@link WebKey#getKey()} is the registered secret
	 * @param secret secret presented by the client
	 * @return true if the secret matches; else false
	 * @throws IllegalStateException if the registration carries no secret, which is
	 *                               a defective registration rather than a bad
	 *                               secret
	 */
	static boolean verifySecret(WebKey jwk, String secret) {
		final var registered = jwk.getKey();
		if (registered == null //
				|| registered.length == 0)
			throw new IllegalStateException("Missing client secret");

		return MessageDigest.isEqual(registered, secret.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Determines whether a registration represents a certificate authority.
	 *
	 * @param authorization client authorization record
	 * @return true if the record carries at least one revocation list
	 */
	static boolean hasCrl(IuOidcClientAuthorization authorization) {
		final var crl = authorization.getCrl();
		return crl != null //
				&& crl.iterator().hasNext();
	}

	/**
	 * Determines whether a registration is still honored.
	 *
	 * @param authorization client authorization record
	 * @return true if the record has no expiry or hasn't reached it
	 */
	static boolean isActive(IuOidcClientAuthorization authorization) {
		final var expires = authorization.getExpires();
		return expires == null //
				|| Instant.now().isBefore(expires);
	}

	/**
	 * Verifies an assertion's signature.
	 *
	 * @param assertion assertion presented
	 * @param jwk       verification key
	 * @return verified token
	 */
	private static WebToken verify(String assertion, WebKey jwk) {
		try {
			return WebToken.verify(assertion, jwk);
		} catch (RuntimeException e) {
			throw new SecurityException("Client assertion did not verify", e);
		}
	}

	/**
	 * Verifies the {@code x5t} and {@code x5t#S256} thumbprints an assertion
	 * asserts, when present, against the registered certificate.
	 *
	 * <p>
	 * A thumbprint that cannot be checked &mdash; because the registration carries
	 * no certificate &mdash; is rejected rather than ignored: the assertion claims
	 * to have been signed by a specific certificate, and that claim must not be
	 * silently accepted.
	 * </p>
	 *
	 * @param header assertion's protected header
	 * @param cert   registered certificate, or {@code null} for a bare key
	 */
	@SuppressWarnings("deprecation")
	private static void matchThumbprint(WebCryptoHeader header, X509Certificate cert) {
		final var thumbprint = header.getCertificateThumbprint();
		final var thumbprint256 = header.getCertificateSha256Thumbprint();
		if (thumbprint == null //
				&& thumbprint256 == null)
			return;

		if (cert == null)
			throw new SecurityException(
					"Client assertion asserts a certificate thumbprint but the registration carries none");

		final var encoded = IuException.unchecked(cert::getEncoded);
		if (thumbprint != null //
				&& !Arrays.equals(thumbprint, IuDigest.sha1(encoded)))
			throw new SecurityException("Client certificate thumbprint mismatch");

		if (thumbprint256 != null //
				&& !Arrays.equals(thumbprint256, IuDigest.sha256(encoded)))
			throw new SecurityException("Client certificate thumbprint mismatch");
	}

	private final IuOidcProviderReference reference;
	private final OidcIssuer issuer;
	private final IuDataStore dataStore;

	/**
	 * Creates a client authenticator.
	 *
	 * @param reference application resources this provider's endpoints read through
	 */
	ClientAuthenticator(IuOidcProviderReference reference) {
		this.reference = Objects.requireNonNull(reference, "Missing provider reference");
		this.issuer = new OidcIssuer(reference::getConfiguration);
		this.dataStore = reference.getDataStore();
	}

	/**
	 * Names the audience an assertion presented here must claim.
	 *
	 * @return this deployment's token endpoint
	 */
	URI tokenEndpoint() {
		return issuer.metadata().getTokenEndpoint();
	}

	/**
	 * Authenticates a client against one registered endpoint.
	 *
	 * <p>
	 * An endpoint may register more than one authorization record &mdash; to accept
	 * an old and a new credential during rotation, say. Each is tried in turn; the
	 * first that accepts the presented credential wins, and one match is all that's
	 * required. A record's failure doesn't refuse the client outright since a later
	 * record may still accept it, and if none does, the <em>first</em> failure is
	 * what's thrown, carrying every later one as
	 * {@link Throwable#getSuppressed() suppressed} &mdash; so a rejection reads as
	 * why the most likely record refused, without losing what the others said. An
	 * endpoint with no authorization at all &mdash; not even one record &mdash;
	 * refuses every credential and every public request.
	 * </p>
	 *
	 * @param endpoint   registered endpoint
	 * @param clientId   client ID the request named
	 * @param credential credential presented, or {@code null} for a public client
	 * @return method that verified the credential
	 * @throws SecurityException if nothing registered accepts the credential
	 */
	Method authenticate(IuOidcClientEndpoint endpoint, String clientId, Credential credential) {
		RuntimeException error = null;

		final var authorizations = endpoint.getAuthorization();
		if (authorizations != null)
			for (final var authorization : authorizations) {
				if (authorization == null)
					continue;

				try {
					if (!isActive(authorization))
						throw new SecurityException("Client registration has expired");

					final var jwk = authorization.getJwk();
					if (jwk == null) {
						// an authorization record with no key is an explicit public
						// registration, distinct from having no record at all
						if (credential != null)
							throw new SecurityException("Client presented a credential but is registered public");

						return Method.NONE;
					}

					if (credential == null)
						throw new SecurityException("Client is registered with a credential but presented none");

					final var isSecret = WebKey.Type.RAW.equals(jwk.getType());

					if (credential.assertion() != null)
						return verifyAssertion(authorization, jwk, isSecret, clientId, credential.assertion());

					if (!isSecret)
						throw new SecurityException("Client is registered for private_key_jwt and presented a secret");

					// The registered key's kid is deliberately not checked against the
					// client ID. It would bind nothing: this record was reached through the
					// presented client's own registration -- a token endpoint resolves the
					// client by ID and walks only that client's endpoints -- so one client's
					// secret is never even loaded while another is being authenticated. The
					// kid is metadata here and nothing else reads it; verifySecret compares
					// the key material alone, and the client_secret_jwt path below binds the
					// credential to the client through the assertion's own iss and sub
					// claims rather than through the kid.
					if (!verifySecret(jwk, credential.secret()))
						throw new SecurityException("Invalid client secret");

					return credential.method();
				} catch (RuntimeException e) {
					error = (RuntimeException) IuException.suppress(error, e);
				}
			}

		if (error == null)
			throw new SecurityException("No client authorization configured for endpoint");
		else
			throw error;
	}

	/**
	 * Verifies a client assertion.
	 *
	 * @param authorization client authorization record
	 * @param jwk           registered key
	 * @param isSecret      whether the registered key is a shared secret
	 * @param clientId      client ID the request named
	 * @param assertion     assertion presented
	 * @return method that verified it
	 */
	private Method verifyAssertion(IuOidcClientAuthorization authorization, WebKey jwk, boolean isSecret, String clientId,
			String assertion) {
		final WebCryptoHeader header;
		try {
			header = WebCryptoHeader.getProtectedHeader(assertion);
		} catch (RuntimeException e) {
			throw new SecurityException("Malformed client assertion", e);
		}

		final WebToken token;
		final Method method;
		if (isSecret) {
			// the registered secret is the MAC key, so no certificate is involved and
			// there is no chain or thumbprint to check
			token = verify(assertion, jwk);
			method = Method.CLIENT_SECRET_JWT;
		} else {
			token = verifyKeyed(authorization, jwk, header, assertion);
			method = Method.PRIVATE_KEY_JWT;
		}
		LOG.fine(() -> "client-assertion-verify:" + clientId + " " + token);

		final var ttl = Objects.requireNonNull(authorization.getAssertionTtl(), "Missing assertion TTL");
		try {
			token.validateClaims(URI.create(clientId), tokenEndpoint(), ttl);
		} catch (RuntimeException e) {
			throw new SecurityException("Client assertion claims did not validate", e);
		}

		// OpenID Connect has a client assertion name itself as both issuer and subject
		if (!clientId.equals(token.getSubject()))
			throw new SecurityException("Client assertion subject is not the client");

		spend(token, clientId, ttl);

		return method;
	}

	/**
	 * Verifies an assertion against a registered asymmetric key, or against the
	 * certificate authority the registration represents.
	 *
	 * @param authorization client authorization record
	 * @param jwk           registered key
	 * @param header        assertion's protected header
	 * @param assertion     assertion presented
	 * @return verified token
	 */
	private WebToken verifyKeyed(IuOidcClientAuthorization authorization, WebKey jwk, WebCryptoHeader header,
			String assertion) {
		// A registration carrying a revocation list is a CA, so the assertion brings
		// its own certificate and the registration says whether to trust it. An empty
		// list is not one: the CA branch exists to check revocation, and with nothing
		// to check against it would trust any chain the assertion supplied.
		if (hasCrl(authorization)) {
			final var signerKey = WebCryptoHeader.verify(header);
			if (signerKey == null //
					|| signerKey.getCertificateChain() == null)
				throw new SecurityException("Client assertion is missing the x5c header a CA registration verifies");

			// resolved outside the try: a defective CA registration is a server fault,
			// not a rejected credential
			final var verifier = reference.getCertificateAuthorityVerifier(authorization);
			try {
				verifier.verify(signerKey);
			} catch (IllegalArgumentException e) {
				throw new SecurityException("Client certificate is not trusted by the registered authority", e);
			}

			return verify(assertion, signerKey);
		}

		final var certificateChain = jwk.getCertificateChain();
		final X509Certificate cert;
		if (certificateChain == null)
			cert = null;
		else {
			// a registered key never carries an empty chain -- WebKey refuses one when it
			// is built -- so past this check the chain is the client's own certificate
			// alone
			if (certificateChain.length > 1)
				throw new IllegalArgumentException(
						"Client certificate must be a single self-signed certificate when no revocation list is registered");

			reference.getSelfSignedVerifier(jwk).verify(jwk);
			cert = certificateChain[0];
		}

		matchThumbprint(header, cert);

		return verify(assertion, jwk);
	}

	/**
	 * Records an assertion's {@code jti} as spent, and refuses one already spent.
	 *
	 * <p>
	 * The entry lives exactly as long as the issuer's assertion lifetime, since an
	 * assertion older than that fails claim validation anyway and keeping the
	 * record would only grow the store.
	 * </p>
	 *
	 * @param token    verified assertion
	 * @param clientId client the assertion authenticates
	 * @param ttl      assertion lifetime
	 * @throws SecurityException if the assertion carries no ID, or one already seen
	 */
	private void spend(WebToken token, String clientId, Duration ttl) {
		final var jti = token.getTokenId();
		if (jti == null)
			throw new SecurityException("Client assertion is missing the jti claim replay protection needs");

		final var now = Instant.now();
		final var key = IuText.utf8(clientId + ' ' + jti);
		final var cutoff = now.minus(ttl);
		final var lastUsedEncoded = dataStore.get(key);
		if (lastUsedEncoded != null) {
			final Instant lastUsed;
			try {
				lastUsed = Instant.parse(IuText.utf8(lastUsedEncoded));
			} catch (RuntimeException e) {
				throw new SecurityException("Invalid replay cutoff in data store, assuming jti was previously used", e);
			}

			if (lastUsed.isAfter(cutoff))
				throw new SecurityException("jti was previously used at " + lastUsed);
		}

		dataStore.put(key, IuText.utf8(now.toString()), ttl);

		LOG.finer(() -> "assertion-spent:" + clientId + " " + jti);
	}

}
