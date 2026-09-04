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

import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.oidc.IuOidcClaims;
import edu.iu.oidc.config.OidcClaimsSource;
import edu.iu.oidc.config.OidcClientConfiguration;
import edu.iu.oidc.config.OidcClientSource;

/**
 * Answers the OpenID Connect UserInfo request.
 *
 * <p>
 * Reads an access token this provider issued, asks a claims source what it
 * holds about the principal that token names, and answers what the grant's
 * scope admits &mdash; signed and encrypted as the client registered.
 * </p>
 *
 * <h2>What this owns, and what it doesn't</h2>
 *
 * <p>
 * The three things here are the three an identity service has no business
 * doing. It cannot verify this provider's tokens; it doesn't know which relying
 * party is asking, so it cannot decide which claims that party may see; and it
 * holds neither this provider's signing keys nor the client's encryption key. So
 * an {@link OidcClaimsSource} answers what it knows about a principal and
 * nothing more, and stays free of OpenID Connect entirely.
 * </p>
 *
 * <p>
 * Rendering claims as JSON isn't here either. The caller passes a
 * {@code serializer}, which is called once, on the filtered claims. That keeps
 * this module free of any opinion about how a claim prints &mdash; including
 * the two that a general-purpose converter gets wrong, {@code updated_at} being
 * a NumericDate and {@code address} being nested. Signing covers exact bytes, so
 * the serialization has to happen before the crypto and after the filtering;
 * taking it as a function is what lets all three stay in one call while only
 * the middle one belongs to the caller.
 * </p>
 *
 * <h2>The subject is bound to the token</h2>
 *
 * <p>
 * {@link IuOidcClaims#getSub() sub} is answered from the verified access token,
 * not from the claims source, whatever the source says. A relying party matches
 * it against the ID token it holds and refuses the response when the two
 * disagree, so a source that resolves a principal name to some other form
 * &mdash; a username for a numeric ID, say &mdash; must not be able to break
 * that comparison.
 * </p>
 */
public class OidcUserinfoEndpoint {

	private static final Logger LOG = Logger.getLogger(OidcUserinfoEndpoint.class.getName());

	/** {@code typ} of a signed UserInfo response. */
	private static final String TYPE = "JWT";

	private final OidcIssuer issuer;
	private final OidcClientSource clients;
	private final OidcClaimsSource claimsSource;

	/**
	 * Creates a UserInfo endpoint.
	 *
	 * @param issuer       this provider's own identity and signing keys
	 * @param clients      reads a relying party's registration, for how a response
	 *                     to it is signed and encrypted
	 * @param claimsSource holds what is known about an end user
	 */
	public OidcUserinfoEndpoint(OidcIssuer issuer, OidcClientSource clients, OidcClaimsSource claimsSource) {
		this.issuer = Objects.requireNonNull(issuer, "Missing issuer");
		this.clients = Objects.requireNonNull(clients, "Missing client source");
		this.claimsSource = Objects.requireNonNull(claimsSource, "Missing claims source");
	}

	/**
	 * Answers a UserInfo request.
	 *
	 * @param accessToken bearer token presented, as the transport read it out of
	 *                    the {@code Authorization} header
	 * @param serializer  renders the admitted claims; called exactly once, and only
	 *                    once the token has been verified
	 * @return the response, and what to call it
	 * @throws SecurityException    if the access token can't be verified, or its
	 *                              registered claims don't hold; a caller answers
	 *                              {@code invalid_token}
	 * @throws NullPointerException if this provider or the client is configured for
	 *                              something it hasn't supplied a key for
	 */
	public OidcUserinfoResult userinfo(String accessToken, Function<IuOidcClaims, String> serializer) {
		final var authorization = OidcTokenAuthorization.verify(accessToken, issuer.configuration(), issuer.issuer());

		final var sub = authorization.getSubject();
		final var scope = authorization.getScope();

		final var claims = new OidcScopedClaims(sub, claimsSource.claims(sub), scope);
		final var serialized = serializer.apply(claims);

		final var clientId = authorization.getClientId();
		LOG.info(() -> "userinfo:" + clientId + ":" + sub + " " + scope);

		return secure(serialized, client(clientId));
	}

	/**
	 * Reads the registration of the client a token was issued to, for the response
	 * settings alone.
	 *
	 * <p>
	 * A token this provider signed is already proof the client was registered when
	 * it was issued, so a registration that has since gone missing isn't a refusal
	 * here: it means nothing asked for the response to be signed or encrypted, and
	 * the claims answer as a plain document. Refusing instead would let a client's
	 * registration being edited take a working token out of service.
	 * </p>
	 *
	 * @param clientId {@code client_id} the token names; may be {@code null}
	 * @return registration, or {@code null} if there is none to read
	 */
	private OidcClientConfiguration client(String clientId) {
		if (clientId == null)
			return null;

		try {
			return clients.client(clientId);
		} catch (Exception e) {
			LOG.log(Level.INFO, e, () -> "userinfo-unregistered-client:" + clientId);
			return null;
		}
	}

	/**
	 * Signs and encrypts serialized claims as one client registered.
	 *
	 * <p>
	 * The four combinations are the ones OpenID Connect defines, and a client that
	 * registered neither gets the claims as they were serialized. Registering
	 * encryption alone answers something confidential but unauthenticated, which is
	 * the client's choice to have made.
	 * </p>
	 *
	 * @param serialized claims, as the caller serialized them
	 * @param client     registration, or {@code null} for a plain document
	 * @return the response, and what to call it
	 * @throws NullPointerException if the client registered an encryption but no key
	 *                              to encrypt to
	 */
	private OidcUserinfoResult secure(String serialized, OidcClientConfiguration client) {
		if (client == null)
			return new OidcUserinfoResult.Json(serialized);

		final var algorithm = client.getUserinfoAlg();
		final var encryption = client.getUserinfoEnc();

		if (encryption == null) {
			if (algorithm == null)
				return new OidcUserinfoResult.Json(serialized);

			return new OidcUserinfoResult.Jwt(OidcJose.sign(serialized, TYPE, issuer.issuerKey(algorithm)));
		}

		final var audienceKey = Objects.requireNonNull(client.getUserinfoJwk(),
				"Missing userinfo encryption key for " + client.getClientId());

		if (algorithm == null)
			// no cty: the plaintext is the claims themselves, not a nested JOSE object
			return new OidcUserinfoResult.Jwt(OidcJose.encrypt(serialized, null, audienceKey, encryption));

		return new OidcUserinfoResult.Jwt(
				OidcJose.signAndEncrypt(serialized, TYPE, issuer.issuerKey(algorithm), audienceKey, encryption));
	}

}
