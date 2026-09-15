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
import java.util.function.Supplier;

import edu.iu.IuIterable;
import edu.iu.crypt.WebKey.Use;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcClaimsSource;
import edu.iu.oidc.config.IuOidcClaimsSource.Usage;
import edu.iu.oidc.config.IuOidcProviderConfiguration;

/**
 * Derived view of an OpenID Connect provider's discovery metadata.
 *
 * <p>
 * Reports what the running provider actually is rather than what a deployment
 * happened to configure. Serializing this to the Discovery document, and
 * serving it, are the deployment's: how the document is represented and where
 * it is published from are transport concerns, and neither appears here.
 * </p>
 *
 * <h2>What is derived rather than configured</h2>
 *
 * <p>
 * These properties are answered here rather than read from the configured
 * document, because configuring them would only create a way for the document
 * to disagree with the running provider:
 * </p>
 * <ul>
 * <li>Endpoint URIs are derived from the issuer, so an endpoint is advertised
 * at the path this module actually serves it from. The path constants below are
 * what a deployment maps its handlers to, so the two cannot drift.</li>
 * <li>Signing algorithms are derived from the provider's own keys, so nothing
 * is advertised that no configured key could sign with.</li>
 * <li>{@link #getScopesSupported() Scopes} add the two this provider implements
 * whatever a deployment configured, and {@link #getClaimsSupported() claims} are
 * derived from the scopes in turn, so what a relying party is told it may ask
 * for and what it is told it may receive both follow from what is actually
 * released.</li>
 * <li>{@link #getIssuer()} is read from configuration, since only the
 * deployment knows the URI it is reachable at, but every derived endpoint is
 * built from it.</li>
 * </ul>
 *
 * <p>
 * Everything else &mdash; the locales and policy documents a deployment
 * declares, and the encryption algorithms it accepts &mdash; delegates to the
 * configured metadata, so a property added to the configuration is published
 * without a code change. By the same token, anything put in that property is
 * public.
 * </p>
 *
 * <p>
 * An instance wraps one read of {@link IuOidcProviderConfiguration}, so a
 * caller constructs one per request rather than holding it: a configuration
 * change then takes effect on the next request, and what a request reports
 * cannot change while it is being read.
 * </p>
 *
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata">OpenID
 *      Connect Discovery 1.0 &sect;3</a>
 */
public class OidcProviderMetadata implements IuOidcProviderMetadata {

	/** Path of the authorization endpoint, relative to the issuer. */
	public static final String AUTHORIZE_PATH = "/authorize";

	/** Path of the token endpoint, relative to the issuer. */
	public static final String TOKEN_PATH = "/token";

	/** Path of the UserInfo endpoint, relative to the issuer. */
	public static final String USERINFO_PATH = "/userinfo";

	/** Path of the JWKS endpoint, relative to the issuer. */
	public static final String JWKS_PATH = "/.well-known/jwks";

	/**
	 * Names a URI relative to a provider's issuer identifier.
	 *
	 * <p>
	 * Built by appending rather than by {@link URI#resolve(String)}, which would
	 * drop the issuer's own path segment, and tolerating a trailing slash on the
	 * issuer so it doesn't turn into an empty path segment.
	 * </p>
	 *
	 * @param metadata provider metadata naming the issuer
	 * @param path     endpoint path, or the empty string for the issuer itself
	 * @return endpoint URI
	 * @throws NullPointerException if the metadata declares no issuer
	 */
	public static URI endpointUri(IuOidcProviderMetadata metadata, String path) {
		final var issuer = Objects.requireNonNull(metadata.getIssuer(), "Missing issuer").toString();
		final var base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
		return URI.create(base + path);
	}

	private final IuOidcProviderConfiguration provider;
	private final IuOidcProviderMetadata metadata;
	private final Supplier<IuOidcClaimsSource> claimsSource;

	/**
	 * Wraps a provider's configured metadata.
	 *
	 * @param provider     provider configuration
	 * @param claimsSource supplies the deployment's claims source, for the claim
	 *                     names its own scopes release. Read only by
	 *                     {@link #getClaimsSupported()}, and only where the
	 *                     configured scopes name one OpenID Connect doesn't define,
	 *                     so nothing a request reads on its way through an endpoint
	 *                     ever calls it
	 * @throws NullPointerException if the configuration declares no metadata
	 */
	public OidcProviderMetadata(IuOidcProviderConfiguration provider, Supplier<IuOidcClaimsSource> claimsSource) {
		this.provider = provider;
		this.metadata = Objects.requireNonNull(provider.getMetadata(), "Missing provider metadata");
		this.claimsSource = Objects.requireNonNull(claimsSource, "Missing claims source");
	}

	@Override
	public URI getIssuer() {
		return metadata.getIssuer();
	}

	@Override
	public URI getAuthorizationEndpoint() {
		return endpointUri(metadata, AUTHORIZE_PATH);
	}

	@Override
	public URI getTokenEndpoint() {
		return endpointUri(metadata, TOKEN_PATH);
	}

	@Override
	public URI getUserinfoEndpoint() {
		return endpointUri(metadata, USERINFO_PATH);
	}

	@Override
	public URI getJwksUri() {
		return endpointUri(metadata, JWKS_PATH);
	}

	@Override
	public URI getRegistrationEndpoint() {
		return metadata.getRegistrationEndpoint();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@code openid} and {@code offline_access} are added to whatever a deployment
	 * configured, because this provider supports both whatever a document says
	 * &mdash; OpenID Connect Discovery requires {@code openid} be supported at all,
	 * and the token endpoint answers a refresh token for {@code offline_access}
	 * without a deployment having to say so.
	 * </p>
	 *
	 * <p>
	 * Advertising them is not a promise that any particular client may ask for
	 * them. What a client is entitled to is settled per-registration, from the
	 * scopes its {@link edu.iu.oidc.config.IuOidcClientResource#getScope()
	 * resources} declare, and an authorization request asking for a scope none of
	 * them grants is refused as {@code invalid_scope}. This property says what the
	 * server implements; that check says who may use it.
	 * </p>
	 */
	@Override
	public Iterable<String> getScopesSupported() {
		final Set<String> supportedScopes = new LinkedHashSet<>();
		supportedScopes.add(OidcClaimScopes.OPENID);
		supportedScopes.add(OidcClaimScopes.OFFLINE_ACCESS);

		final var metadataSupportedScopes = metadata.getScopesSupported();
		if (metadataSupportedScopes != null)
			metadataSupportedScopes.forEach(supportedScopes::add);

		return supportedScopes;
	}

	@Override
	public Iterable<String> getResponseTypesSupported() {
		return IuIterable.iter("code");
	}

	@Override
	public Iterable<String> getResponseModesSupported() {
		return metadata.getResponseModesSupported();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Derived rather than configured, for the same reason the endpoint URIs are:
	 * what discovery advertises and what {@link OidcTokenEndpoint} actually answers
	 * cannot be allowed to drift. Token exchange is advertised unconditionally even
	 * though a {@link edu.iu.oidc.config.IuOidcProviderReference#isProduction()
	 * production} deployment refuses every one &mdash; this view is built from the
	 * configuration, which says nothing about whether the deployment is a
	 * production one, and a grant type that exists and refuses is a truer thing to
	 * publish than one that disappears.
	 * </p>
	 */
	@Override
	public Iterable<String> getGrantTypesSupported() {
		return IuIterable.iter("authorization_code", "refresh_token", "client_credentials",
				OidcTokenEndpoint.TOKEN_EXCHANGE);
	}

	@Override
	public Iterable<String> getAcrValuesSupported() {
		return metadata.getAcrValuesSupported();
	}

	@Override
	public Iterable<String> getSubjectTypesSupported() {
		return IuIterable.iter("public");
	}

	@Override
	public Iterable<String> getIdTokenSigningAlgValuesSupported() {
		return signingAlgValues();
	}

	@Override
	public Iterable<String> getIdTokenEncryptionAlgValuesSupported() {
		return metadata.getIdTokenEncryptionAlgValuesSupported();
	}

	@Override
	public Iterable<String> getIdTokenEncryptionEncValuesSupported() {
		return metadata.getIdTokenEncryptionEncValuesSupported();
	}

	@Override
	public Iterable<String> getUserinfoSigningAlgValuesSupported() {
		return signingAlgValues();
	}

	@Override
	public Iterable<String> getUserinfoEncryptionAlgValuesSupported() {
		return metadata.getUserinfoEncryptionAlgValuesSupported();
	}

	@Override
	public Iterable<String> getUserinfoEncryptionEncValuesSupported() {
		return metadata.getUserinfoEncryptionEncValuesSupported();
	}

	@Override
	public Iterable<String> getRequestObjectSigningAlgValuesSupported() {
		return metadata.getRequestObjectSigningAlgValuesSupported();
	}

	@Override
	public Iterable<String> getRequestObjectEncryptionAlgValuesSupported() {
		return metadata.getRequestObjectEncryptionAlgValuesSupported();
	}

	@Override
	public Iterable<String> getRequestObjectEncryptionEncValuesSupported() {
		return metadata.getRequestObjectEncryptionEncValuesSupported();
	}

	@Override
	public Iterable<String> getTokenEndpointAuthMethodsSupported() {
		return metadata.getTokenEndpointAuthMethodsSupported();
	}

	@Override
	public Iterable<String> getTokenEndpointSigningAlgValuesSupported() {
		return metadata.getTokenEndpointSigningAlgValuesSupported();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Derived from {@link #getScopesSupported() the scopes supported}, for the same
	 * reason the endpoint URIs are: a claim named here that no scope admits would
	 * be advertised and never released, and one a scope admits but this omits would
	 * be released and never advertised. Both halves of disclosure are read &mdash;
	 * the sets OpenID Connect &sect;5.4 binds to the scopes it defines, and
	 * whatever the claims source {@link IuOidcClaimsSource#admitted(Set, Usage)
	 * names} for the scopes it doesn't, asked under {@link Usage#USERINFO} since
	 * that is the widest this provider discloses.
	 * </p>
	 *
	 * <p>
	 * A configured {@code claims_supported} is added to rather than replaced, so a
	 * deployment can still declare a claim it releases by some means this doesn't
	 * model. &sect;3 calls the list non-exhaustive, so adding to it is within what
	 * a relying party may expect.
	 * </p>
	 *
	 * <p>
	 * The claims source is consulted only when a scope of the deployment's own is
	 * configured; a deployment that declares none never needs one bound to serve
	 * discovery.
	 * </p>
	 */
	@Override
	public Iterable<String> getClaimsSupported() {
		final Set<String> scopesSupported = new LinkedHashSet<>();
		getScopesSupported().forEach(scopesSupported::add);

		final Set<String> claimsSupported = new LinkedHashSet<>(OidcClaimScopes.admitted(scopesSupported));

		final var additionalScopes = OidcClaimScopes.additional(scopesSupported);
		if (!additionalScopes.isEmpty())
			claimsSupported.addAll(Objects.requireNonNull(claimsSource.get(), "Missing claims source")
					.admitted(additionalScopes, Usage.USERINFO));

		final var configuredClaims = metadata.getClaimsSupported();
		if (configuredClaims != null)
			configuredClaims.forEach(claimsSupported::add);

		return claimsSupported;
	}

	@Override
	public Iterable<String> getDisplayValuesSupported() {
		return metadata.getDisplayValuesSupported();
	}

	@Override
	public Iterable<String> getClaimTypesSupported() {
		return metadata.getClaimTypesSupported();
	}

	@Override
	public URI getServiceDocumentation() {
		return metadata.getServiceDocumentation();
	}

	@Override
	public Iterable<String> getClaimsLocalesSupported() {
		return metadata.getClaimsLocalesSupported();
	}

	@Override
	public Iterable<String> getUiLocalesSupported() {
		return metadata.getUiLocalesSupported();
	}

	@Override
	public boolean isClaimsParameterSupported() {
		return metadata.isClaimsParameterSupported();
	}

	@Override
	public boolean isRequestParameterSupported() {
		return metadata.isRequestParameterSupported();
	}

	@Override
	public boolean isRequireRequestUriRegistration() {
		return metadata.isRequireRequestUriRegistration();
	}

	@Override
	public URI getOpPolicyUri() {
		return metadata.getOpPolicyUri();
	}

	@Override
	public URI getOpTosUri() {
		return metadata.getOpTosUri();
	}

	/**
	 * Names the signature algorithms the provider's own keys can sign with.
	 *
	 * <p>
	 * Answers both the ID token and the UserInfo response, since one set of issuer
	 * keys signs both. A key with no algorithm, or one whose algorithm is for
	 * encryption, is skipped &mdash; advertising it would name an algorithm no
	 * configured key could be selected to sign with.
	 * </p>
	 *
	 * @return JWA signature algorithm names, in the order the keys are configured
	 */
	private Iterable<String> signingAlgValues() {
		final Set<String> algValues = new LinkedHashSet<>();

		final var jwks = provider.getJwks();
		if (jwks != null)
			for (final var jwk : jwks) {
				if (jwk == null)
					continue;

				final var algorithm = jwk.getAlgorithm();
				if (algorithm != null //
						&& Use.SIGN.equals(algorithm.use))
					algValues.add(algorithm.alg);
			}

		return algValues;
	}

}
