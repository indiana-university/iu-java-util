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
package edu.iu.oidc.config;

import edu.iu.IuDataStore;
import edu.iu.IuRequestAttributes;
import edu.iu.pki.IuCertificateAuthority;
import edu.iu.pki.IuPkiVerifier;
import edu.iu.session.IuSessionHandler;

/**
 * Provides application resources to the endpoints of an OpenID Provider.
 *
 * <p>
 * One thing an integration implements, rather than a collaborator per seam. A
 * servlet, a JAX-RS application, or whatever else stands the provider up binds
 * what it has &mdash; a session handler, a data store, a way to authenticate,
 * whatever reads registrations and identity &mdash; and hands over a single
 * reference. Every endpoint reads through it, so adding an endpoint doesn't add
 * a constructor argument, and so two endpoints cannot be wired to disagree
 * about who this provider is.
 * </p>
 *
 * <p>
 * This is not intended to be a structural interface. An implementation declares
 * it explicitly and overrides what it uses; everything a deployment doesn't
 * reach for refuses by default rather than answering something plausible, so a
 * missing binding fails where it is needed and says which one it was.
 * </p>
 *
 * <h2>What isn't here</h2>
 *
 * <p>
 * No JSON. An integration depends on {@code IuConfig} for itself, and
 * configures both the JSON its endpoints answer with and the claim adapters a
 * stored grant round-trips through. What this hands the provider is typed or
 * opaque, never a document, and the one thing the provider does publish
 * verbatim &mdash; a UserInfo claims document &mdash; comes rendered from
 * {@link #getClaimsSource() the claims source}.
 * </p>
 */
public interface IuOidcProviderReference {

	/**
	 * Gets this provider's own configuration.
	 *
	 * <p>
	 * Read afresh on every use rather than captured, so a change takes effect on
	 * the next request. An implementation backed by a caching configuration source
	 * is what bounds how often that read costs anything.
	 * </p>
	 *
	 * @return {@link OidcProviderConfiguration}
	 */
	OidcProviderConfiguration getConfiguration();

	/**
	 * Gets the source a relying party's registration is read through.
	 *
	 * @return {@link OidcClientSource}
	 * @throws UnsupportedOperationException if the deployment binds none
	 */
	default OidcClientSource getClientSource() {
		throw new UnsupportedOperationException("Missing client source");
	}

	/**
	 * Gets the source an end user's claims are read through.
	 *
	 * @return {@link OidcClaimsSource}
	 * @throws UnsupportedOperationException if the deployment binds none
	 */
	default OidcClaimsSource getClaimsSource() {
		throw new UnsupportedOperationException("Missing claims source");
	}

	/**
	 * Gets the source that decides what a client's requested authorization details
	 * release to an end user.
	 *
	 * <p>
	 * Answers {@code null} by default, which releases nothing. A deployment that
	 * grants nothing this way needs no binding.
	 * </p>
	 *
	 * @return {@link OidcAuthorizationDetailsSource}
	 */
	default OidcAuthorizationDetailsSource getAuthorizationDetailsSource() {
		return (details, principalName) -> null;
	}

	/**
	 * Gets the session handler an authorization request is held in across the round
	 * trip to the identity provider.
	 *
	 * <p>
	 * <em>Must</em> be a handler of its own. One shared with the authentication
	 * mechanism would have each overwrite the other's cookie.
	 * </p>
	 *
	 * @return {@link IuSessionHandler}
	 * @throws UnsupportedOperationException if the deployment binds none
	 */
	default IuSessionHandler getSessionHandler() {
		throw new UnsupportedOperationException("Missing session handler");
	}

	/**
	 * Gets the store authorization codes and refresh tokens are filed in.
	 *
	 * <p>
	 * Typically the same store the deployment keeps authenticated sessions in,
	 * rather than a second connection to the same server. What is filed there is
	 * encrypted to a key the reference itself carries, so the store holds nothing
	 * it can read.
	 * </p>
	 *
	 * @return {@link IuDataStore}
	 * @throws UnsupportedOperationException if the deployment binds none
	 */
	default IuDataStore getDataStore() {
		throw new UnsupportedOperationException("Missing data store");
	}

	/**
	 * Gets the principal an identity provider has established for a request.
	 *
	 * <p>
	 * However a deployment authenticates &mdash; SAML, Kerberos, a container login
	 * &mdash; this is where the result arrives, and nothing about the mechanism
	 * reaches the provider. An unauthenticated request is the ordinary first pass
	 * rather than a failure, and an implementation reports it either by answering
	 * {@code null} or by refusing the lookup; both read the same way.
	 * </p>
	 *
	 * @param attributes incoming request attributes, which is how a session is
	 *                   found
	 * @return {@link OidcAuthenticatedPrincipal}; {@code null} if nothing is
	 *         established
	 */
	default OidcAuthenticatedPrincipal getAuthenticatedPrincipal(IuRequestAttributes attributes) {
		return null;
	}

	/**
	 * Gets a verifier for one client's registered certificate authority.
	 *
	 * <p>
	 * The provider decides <em>which</em> registration warrants verification and
	 * what a failure means; the deployment decides what does the verifying, since
	 * an implementation of {@link IuPkiVerifier} lives in a module a library has no
	 * business compiling against.
	 * </p>
	 *
	 * @param certificateAuthority authority a client's key must chain to
	 * @return {@link IuPkiVerifier}
	 * @throws UnsupportedOperationException if the deployment binds none, which
	 *                                       refuses every client authenticating by
	 *                                       certificate
	 */
	default IuPkiVerifier getPkiVerifier(IuCertificateAuthority certificateAuthority) {
		throw new UnsupportedOperationException("Missing PKI verifier");
	}

	/**
	 * Determines whether this is a production deployment.
	 *
	 * <p>
	 * Gates impersonation, which is honored only outside production and only for a
	 * principal holding one of the endpoint's backdoor roles. Defaults to
	 * {@code true}, so a deployment that says nothing is treated as production and
	 * a forgotten binding closes the backdoor rather than opening it.
	 * </p>
	 *
	 * @return true if this deployment is a production one
	 */
	default boolean isProduction() {
		return true;
	}

}
