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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuBadRequestException;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.config.IuOidcClaimsSource;
import edu.iu.oidc.config.IuOidcClaimsSource.Usage;
import edu.iu.oidc.config.IuOidcClientConfiguration;

/**
 * Answers the OpenID Connect UserInfo request.
 *
 * <p>
 * Reads an access token this provider issued and answers the end user's claims
 * the grant's scope admits &mdash; signed and encrypted as the client
 * registered. A token that names its client as its subject has no end user, so
 * it answers only that subject without consulting the claims source.
 * </p>
 *
 * <h2>What this owns, and what it doesn't</h2>
 *
 * <p>
 * The three things here are the three an identity service has no business
 * doing. It cannot verify this provider's tokens; it doesn't know which relying
 * party is asking, so it cannot decide which of the claims OpenID Connect
 * defines that party may see; and it holds neither this provider's signing keys
 * nor the client's encryption key. So an {@link IuOidcClaimsSource} answers
 * what it knows about a principal and stays free of OpenID Connect entirely.
 * </p>
 *
 * <p>
 * What it does decide is its own half of disclosure. The &sect;5.4 claim sets
 * are mapped here, from the granted scope, deny-by-default; every other scope
 * is the deployment's, and {@link IuOidcClaimsSource#admitted(Set, Usage)
 * admitted} names what those release. This is the widest disclosure the
 * provider makes, so it asks under {@link Usage#USERINFO}.
 * </p>
 *
 * <p>
 * Rendering claims as JSON isn't here either, and neither is any opinion about
 * how a claim prints &mdash; including the two a general-purpose converter gets
 * wrong, {@code updated_at} being a NumericDate and {@code address} being
 * nested. A source states both, either by writing typed claims onto a builder
 * or by rendering the document whole.
 * </p>
 *
 * <h2>Two responses, two shapes</h2>
 *
 * <p>
 * A signed response is a JWT, so it is built the way every other token this
 * provider issues is: the source writes onto a {@link WebToken} builder and the
 * provider signs what comes out. OpenID Connect &sect;5.3.2 requires that one
 * to carry {@code iss} and {@code aud}, so a response lifted out and replayed
 * to a different relying party doesn't verify there &mdash; and those are
 * written here rather than asked of the source, since who this provider is and
 * who asked are both its own to know.
 * </p>
 *
 * <p>
 * An unsigned response is a plain claims document with nothing to lift, names
 * neither party, and is rendered whole by the source; this publishes
 * {@link Object#toString() toString()} without parsing it. Encryption doesn't
 * change which shape it is &mdash; a response that is encrypted but not signed
 * carries the claims themselves as plaintext, not a nested JOSE object.
 * </p>
 *
 * <p>
 * Publishing without parsing means it cannot check what it published. When a
 * claim set is admitted, a source is told which principal to answer about and
 * must name that principal back as {@code sub}; a relying party matches it
 * against the ID token it holds and refuses the response when the two disagree,
 * which is where a source that answered for somebody else is caught. When no
 * claim is admitted, the provider renders the {@code sub}-only response itself,
 * so there is no identity lookup to make.
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
	 * @param reference application resources this provider's endpoints read through
	 */
	public OidcUserinfoEndpoint(IuOidcProviderReference reference) {
		this.reference = Objects.requireNonNull(reference, "Missing provider reference");
		this.issuer = new OidcIssuer(reference::getConfiguration, reference::getClaimsSource);
	}

	/**
	 * Answers a UserInfo request.
	 *
	 * @param accessToken bearer token presented, as the transport read it out of
	 *                    the {@code Authorization} header
	 * @return the response, and what to call it
	 * @throws SecurityException    if the access token can't be verified, or its
	 *                              registered claims don't hold; a caller answers
	 *                              {@code invalid_token}
	 * @throws NullPointerException if this provider or the client is configured for
	 *                              something it hasn't supplied a key for, or the
	 *                              claims source answers nothing for an admitted
	 *                              claim set
	 */
	public OidcUserinfoResult userinfo(String accessToken) {
		final var authorization = OidcTokenAuthorization.verify(accessToken, issuer.configuration(), issuer.issuer());

		final var sub = authorization.getSubject();
		final var scope = authorization.getScope();
		final var clientId = Objects.requireNonNull(authorization.getClientId(), "missing client_id");

		final var client = reference.getClientSource().client(clientId);
		if (client == null)
			throw new IuBadRequestException("client " + clientId + " not registered");

		final var signed = client.getUserinfoAlg() != null;

		// the two halves of disclosure: the sets OpenID Connect fixes, and whatever
		// the deployment releases for scopes of its own
		final Set<String> admitted = new LinkedHashSet<>();

		// a client-credentials token names its client rather than an end user, so no
		// identity claim can be released and no claims-source policy needs consulting
		if (!sub.equals(clientId)) {
			admitted.addAll(OidcClaimScopes.admitted(scope));

			final var additional = OidcClaimScopes.additional(scope);
			if (!additional.isEmpty())
				admitted.addAll(reference.getClaimsSource().admitted(additional, Usage.USERINFO));
		}

		final String document;
		if (signed) {
			// a signed response is a JWT, so it is built the way every other token this
			// provider issues is -- and §5.3.2's iss and aud are the provider's to write
			final var builder = WebToken.builder() //
					.iss(issuer.issuer()) //
					.sub(sub) //
					.aud(URI.create(clientId));

			if (!admitted.isEmpty())
				reference.getClaimsSource().claims(sub, admitted, builder);

			document = builder.build().toString();
		} else if (admitted.isEmpty())
			// a sub-only response is all the provider can answer without an identity
			// lookup, and is the plain-document counterpart to the signed response above
			document = WebToken.builder().sub(sub).build().toString();
		else
			// an unsigned response is a plain claims document with nothing to lift out of
			// it, so it names neither party and the source renders it whole
			document = Objects
					.requireNonNull(reference.getClaimsSource().claims(sub, admitted), "Missing claims for " + sub)
					.toString();

		LOG.info(() -> "userinfo:" + clientId + ":" + sub + " " + scope);

		return secure(document, client);
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
	 * @throws NullPointerException if the client registered an encryption but no
	 *                              key to encrypt to
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
