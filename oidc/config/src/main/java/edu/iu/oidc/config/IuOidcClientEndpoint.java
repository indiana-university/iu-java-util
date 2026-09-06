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

import java.net.URI;

import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Describes one redirect URI a client is registered for, and what it may do
 * through it.
 *
 * <p>
 * A client registers an endpoint per redirect URI rather than a bare list of
 * URIs, so the keys a token is signed and encrypted with, and the resources the
 * token may be used against, can differ between a client's environments or
 * deployments without registering a separate client for each.
 * </p>
 *
 * <p>
 * Property names use lower case with underscores, so {@link #getRedirectUri()}
 * reads {@code redirect_uri} and {@link #getEncryptJwk()} reads
 * {@code encrypt_jwk}.
 * </p>
 */
public interface IuOidcClientEndpoint {

	/**
	 * Gets how a client redeeming a code issued to this endpoint authenticates
	 * itself at the token endpoint.
	 *
	 * <p>
	 * Registered per endpoint rather than per client, so one deployment's key can
	 * be rotated or revoked without disturbing another's.
	 * </p>
	 *
	 * <p>
	 * More than one record may be registered; the first that accepts the presented
	 * credential is used, which is what lets an old and a new credential both be
	 * honored while one is being rotated in. An endpoint with no record at all
	 * &mdash; {@code null} or empty &mdash; accepts nothing, including a public
	 * request with no credential; a record that carries no key is what registers an
	 * endpoint as public.
	 * </p>
	 *
	 * @return authorization records, tried in order; {@code null} or empty to
	 *         accept no credential at all
	 */
	Iterable<IuOidcClientAuthorization> getAuthorization();

	/**
	 * Gets the identity roles that entitle an end user to a token from this
	 * endpoint at all.
	 *
	 * <p>
	 * Checked against the effective principal &mdash; the impersonated principal
	 * when a backdoor request is honored, otherwise the one the identity provider
	 * authenticated. Holding any one is enough. {@code null} or empty denies every
	 * principal; the literal role {@code ALL} is what registers an endpoint as open
	 * to anyone, since a role check treats it as matching unconditionally.
	 * </p>
	 *
	 * @return identity role names; {@code null} or empty to admit no one
	 */
	Iterable<String> getAccessRoles();

	/**
	 * Gets the identity roles that entitle a principal to request another
	 * principal's token through the {@code impersonated_principal} request
	 * parameter.
	 *
	 * <p>
	 * Checked against the principal the identity provider actually authenticated,
	 * never the one being impersonated, so holding a backdoor role is what lets a
	 * principal act as someone else rather than something the target of
	 * impersonation grants. Only honored outside a production deployment; a request
	 * naming one in production is answered as if it had named none, whether or not
	 * the authenticated principal holds a backdoor role. {@code null} or empty
	 * denies the backdoor to every principal; {@code ALL} opens it to anyone, the
	 * same as {@link #getAccessRoles()}.
	 * </p>
	 *
	 * @return identity role names; {@code null} or empty to refuse impersonation
	 *         to everyone
	 */
	Iterable<String> getBackdoorRoles();

	/**
	 * Gets the roles a request through this endpoint may act in, each naming the
	 * identity roles that entitle an end user to it.
	 *
	 * @return roles; null or empty if the client makes no role claims
	 */
	Iterable<IuOidcClientRole> getRoles();

	/**
	 * Gets the algorithm an ID token issued to this endpoint is signed with.
	 *
	 * @return signature {@link Algorithm}
	 */
	Algorithm getAlg();

	/**
	 * Gets the content encryption algorithm an ID token issued to this endpoint is
	 * encrypted with.
	 *
	 * @return {@link Encryption}
	 */
	Encryption getEnc();

	/**
	 * Gets the key an ID token issued to this endpoint is encrypted to.
	 *
	 * @return encryption {@link WebKey}
	 */
	WebKey getEncryptJwk();

	/**
	 * Gets the redirect URI this endpoint is registered for, which is the value an
	 * authorization request must repeat exactly to select it.
	 *
	 * @return application redirect URI
	 */
	URI getRedirectUri();

	/**
	 * Gets the resources a request through this endpoint may act on.
	 *
	 * <p>
	 * The requested {@code resource} selects among these, and the scopes the
	 * matching entries declare are what the request may ask for. An entry with no
	 * {@link IuOidcClientResource#getUri() URI} is the one a request naming no
	 * resource matches.
	 * </p>
	 *
	 * @return resources
	 */
	Iterable<IuOidcClientResource> getResources();

}
