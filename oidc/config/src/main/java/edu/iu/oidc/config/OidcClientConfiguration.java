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

/**
 * Describes one relying party registered with this provider.
 *
 * <p>
 * A client is registered on its own rather than as an entry inside the
 * provider's configuration, so registering or revoking one is a change scoped
 * to that client alone. Having nothing to read for a {@code client_id} is how
 * an unregistered client is refused.
 * </p>
 *
 * <p>
 * Property names use lower case with underscores, so {@link #getClientId()}
 * reads {@code client_id}.
 * </p>
 */
public interface OidcClientConfiguration {

	/**
	 * Gets the client ID, which is also the key this registration is read by.
	 *
	 * @return Client ID
	 */
	String getClientId();

	/**
	 * Determines whether this client may be used.
	 *
	 * <p>
	 * A disabled client stays registered but is refused, so taking one out of
	 * service doesn't discard the endpoints, keys, and resources it was registered
	 * with, and restoring it doesn't mean registering it again.
	 * </p>
	 *
	 * @return true if the client may be used; false to refuse it as if it were
	 *         unregistered
	 */
	boolean isEnabled();

	/**
	 * Gets the identity roles that entitle an end user to administer this client's
	 * own registration.
	 *
	 * <p>
	 * These govern who may change what this registration describes, not what the
	 * client may do once registered; a request through the client is governed by
	 * the endpoint it names. Holding any one of them is enough.
	 * </p>
	 *
	 * @return identity role names; {@code null} or empty to admit no one
	 */
	Iterable<String> getAdminRoles();

	/**
	 * Gets the endpoints this client is registered for, one per redirect URI.
	 *
	 * <p>
	 * The requested {@code redirect_uri} selects one, and only that endpoint's
	 * resources bear on what the request may ask for.
	 * </p>
	 *
	 * @return registered endpoints
	 */
	Iterable<OidcClientEndpoint> getEndpoints();

	/**
	 * Iterates roles mapped for this client.
	 *
	 * @return mapped roles
	 */
	Iterable<OidcClientRole> getRoles();

}
