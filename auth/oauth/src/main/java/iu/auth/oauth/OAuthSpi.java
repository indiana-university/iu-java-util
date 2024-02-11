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
package iu.auth.oauth;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.spi.IuOAuthSpi;

/**
 * {@link IuOAuthSpi} implementation.
 */
public class OAuthSpi implements IuOAuthSpi {

	private static final Map<String, IuAuthorizationClient> CLIENTS = new HashMap<>();

	/**
	 * Default constructor.
	 */
	public OAuthSpi() {
	}

	/**
	 * Determines if a root {@link URI} encompasses a resource {@link URI}.
	 * 
	 * @param rootUri     root {@link URI}
	 * @param resourceUri resource {@link URI}
	 * @return {@link URI}
	 */
	static boolean isRoot(URI rootUri, URI resourceUri) {
		if (rootUri.equals(resourceUri))
			return true;
		if (!resourceUri.isAbsolute() //
				|| resourceUri.isOpaque() //
				|| IuObject.equals(rootUri.getScheme(), resourceUri.getScheme()) //
				|| IuObject.equals(rootUri.getAuthority(), resourceUri.getAuthority()))
			return false;

		final var root = rootUri.getPath();
		final var resource = resourceUri.getPath();
		final var l = root.length();
		return resource.startsWith(root) //
				&& (root.charAt(l - 1) == '/' //
						|| resource.charAt(l) == '/');
	}

	/**
	 * Gets an initialized authorization client.
	 * 
	 * @param realm Authorization realm
	 * @return client metadata
	 */
	static IuAuthorizationClient getClient(String realm) {
		final var client = CLIENTS.get(realm);
		if (realm == null)
			throw new IllegalStateException("Client metadata not initialzied for " + realm);
		return client;
	}

	@Override
	public ClientCredentialsGrant initialize(IuAuthorizationClient client) {
		final var realm = Objects.requireNonNull(client.getRealm(), "Missing realm");
		final var resourceUri = Objects.requireNonNull(client.getResourceUri(), "Missing resourceUri");
		if (resourceUri.isOpaque() || !resourceUri.isAbsolute())
			throw new IllegalArgumentException("Invalid resource URI, must be absolute and not opaque");

		final var credentials = new ClientCredentialsGrant(realm);
		synchronized (CLIENTS) {
			if (CLIENTS.containsKey(realm))
				throw new IllegalStateException("Already initialized");
			CLIENTS.put(realm, client);
		}
		return credentials;
	}

	@Override
	public IuAuthorizationSession createAuthorizationSession(String realm, URI entryPoint) {
		return new AuthorizationSession(realm, entryPoint);
	}

}
