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
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.oidc.IuOidcClaims;
import edu.iu.oidc.config.IuOidcClaimsSource;
import edu.iu.oidc.config.IuOidcClientConfiguration;
import edu.iu.oidc.config.IuOidcProviderReference;

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
 * an {@link IuOidcClaimsSource} answers what it knows about a principal and
 * nothing more, and stays free of OpenID Connect entirely.
 * </p>
 *
 * <p>
 * Rendering claims as JSON isn't here either. What a source answers renders
 * itself, so this publishes {@link Object#toString() toString()} without
 * looking at it &mdash; which keeps the module free of any opinion about how a
 * claim prints, including the two a general-purpose converter gets wrong,
 * {@code updated_at} being a NumericDate and {@code address} being nested.
 * </p>
 *
 * <h2>A signed response names both parties</h2>
 *
 * <p>
 * OpenID Connect &sect;5.3.2 requires a signed response to carry {@code iss} and
 * {@code aud}, so one lifted out of its response and replayed to a different
 * relying party doesn't verify there. An unsigned response carries neither: it
 * is a plain claims document with nothing to lift. Which of the two it will be
 * is settled from the registration before the claims are asked for, and the
 * source is told what to render rather than the document being edited
 * afterwards &mdash; nothing here parses what it publishes.
 * </p>
 *
 * <h2>The subject is checked against the token</h2>
 *
 * <p>
 * A source is told which principal to answer about and <em>must</em> answer
 * that principal's name back as {@link IuOidcClaims#getSub() sub}. This checks
 * rather than trusting: a relying party matches {@code sub} against the ID
 * token it holds and refuses the response when the two disagree, so a source
 * that resolves a principal name to some other form &mdash; a username for a
 * numeric ID, say &mdash; would break that comparison silently. Checking is all
 * that is available now that the document renders itself; there is no longer a
 * view in between to bind the claim from.
 * </p>
 */
public class OidcUserinfoEndpoint {

	private static final Logger LOG = Logger.getLogger(OidcUserinfoEndpoint.class.getName());

	/** {@code typ} of a signed UserInfo response. */
	private static final String TYPE = "JWT";

	private final IuOidcProviderReference reference;
	private final OidcIssuer issuer;

	/**
	 * Creates a UserInfo endpoint.
	 *
	 * @param reference application resources this provider's endpoints read
	 *                  through
	 */
	public OidcUserinfoEndpoint(IuOidcProviderReference reference) {
		this.reference = Objects.requireNonNull(reference, "Missing provider reference");
		this.issuer = new OidcIssuer(reference::getConfiguration);
	}

	/**
	 * Answers a UserInfo request.
	 *
	 * @param accessToken bearer token presented, as the transport read it out of
	 *                    the {@code Authorization} header
	 * @return the response, and what to call it
	 * @throws SecurityException     if the access token can't be verified, or its
	 *                               registered claims don't hold; a caller answers
	 *                               {@code invalid_token}
	 * @throws IllegalStateException if the claims source answers about somebody
	 *                               other than the principal it was asked about
	 * @throws NullPointerException  if this provider or the client is configured
	 *                               for something it hasn't supplied a key for, or
	 *                               the claims source answers nothing
	 */
	public OidcUserinfoResult userinfo(String accessToken) {
		final var authorization = OidcTokenAuthorization.verify(accessToken, issuer.configuration(), issuer.issuer());

		final var sub = authorization.getSubject();
		final var scope = authorization.getScope();
		final var clientId = authorization.getClientId();

		// read before the claims are asked for, since whether the response will be
		// signed decides what the rendered document has to carry
		final var client = client(clientId);
		final var signed = client != null //
				&& client.getUserinfoAlg() != null;

		final var claims = Objects.requireNonNull(
				reference.getClaimsSource().claims(sub, OidcClaimScopes.admitted(scope),
						signed ? issuer.issuer() : null, signed ? clientId : null),
				"Missing claims for " + sub);

		// checked rather than trusted: a relying party refuses a response whose sub
		// disagrees with the ID token it holds, and publishing claims about somebody
		// else would be worse than answering nothing
		if (!sub.equals(claims.getSub()))
			throw new IllegalStateException("Claims source answered for " + claims.getSub() + " rather than " + sub);

		LOG.info(() -> "userinfo:" + clientId + ":" + sub + " " + scope);

		return secure(claims.toString(), client);
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
	private IuOidcClientConfiguration client(String clientId) {
		if (clientId == null)
			return null;

		try {
			return reference.getClientSource().client(clientId);
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
	private OidcUserinfoResult secure(String serialized, IuOidcClientConfiguration client) {
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
