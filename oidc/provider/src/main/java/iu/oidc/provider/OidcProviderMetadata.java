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

import edu.iu.crypt.WebKey.Use;
import edu.iu.oidc.IuOidcProviderMetadata;
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
 * Three kinds of property are answered here rather than read from the
 * configured document, because configuring them would only create a way for the
 * document to disagree with the running provider:
 * </p>
 * <ul>
 * <li>Endpoint URIs are derived from the issuer, so an endpoint is advertised
 * at the path this module actually serves it from. The path constants below are
 * what a deployment maps its handlers to, so the two cannot drift.</li>
 * <li>Signing algorithms are derived from the provider's own keys, so nothing
 * is advertised that no configured key could sign with.</li>
 * <li>{@link #getIssuer()} is read from configuration, since only the
 * deployment knows the URI it is reachable at, but every derived endpoint is
 * built from it.</li>
 * </ul>
 *
 * <p>
 * Everything else &mdash; the claims, locales, and policy documents a
 * deployment declares, and the encryption algorithms it accepts &mdash;
 * delegates to the configured metadata, so a property added to the
 * configuration is published without a code change. By the same token, anything
 * put in that property is public.
 * </p>
 *
 * <p>
 * An instance wraps one read of {@link IuOidcProviderConfiguration}, so a caller
 * constructs one per request rather than holding it: a configuration change
 * then takes effect on the next request, and what a request reports cannot
 * change while it is being read.
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
	public static final String USERINFO_PATH = "/id";

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

	/**
	 * Wraps a provider's configured metadata.
	 *
	 * @param provider provider configuration
	 * @throws NullPointerException if the configuration declares no metadata
	 */
	public OidcProviderMetadata(IuOidcProviderConfiguration provider) {
		this.provider = provider;
		this.metadata = Objects.requireNonNull(provider.getMetadata(), "Missing provider metadata");
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

	@Override
	public Iterable<String> getScopesSupported() {
		return metadata.getScopesSupported();
	}

	@Override
	public Iterable<String> getResponseTypesSupported() {
		return metadata.getResponseTypesSupported();
	}

	@Override
	public Iterable<String> getResponseModesSupported() {
		return metadata.getResponseModesSupported();
	}

	@Override
	public Iterable<String> getGrantTypesSupported() {
		return metadata.getGrantTypesSupported();
	}

	@Override
	public Iterable<String> getAcrValuesSupported() {
		return metadata.getAcrValuesSupported();
	}

	@Override
	public Iterable<String> getSubjectTypesSupported() {
		return metadata.getSubjectTypesSupported();
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

	@Override
	public Iterable<String> getClaimsSupported() {
		return metadata.getClaimsSupported();
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
