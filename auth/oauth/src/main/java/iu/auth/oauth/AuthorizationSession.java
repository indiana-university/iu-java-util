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

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;
import iu.auth.util.HttpUtils;

/**
 * OAuth authorization session implementation.
 */
class AuthorizationSession implements IuAuthorizationSession, Serializable {

	private static final long serialVersionUID = 1L;

	private final String realm;
	private final URI entryPoint;
	private final Map<URI, AuthorizationCodeGrant> grants = new HashMap<>();

	/**
	 * Constructor.
	 * 
	 * @param realm      authentication realm
	 * @param entryPoint {@link URI} to redirect the user agent to in order restart
	 *                   the authentication process.
	 */
	public AuthorizationSession(String realm, URI entryPoint) {
		this.realm = realm;
		this.entryPoint = entryPoint;
	}

	@Override
	public IuAuthorizationGrant grant() throws UnsupportedOperationException {
		if (entryPoint == null)
			throw new UnsupportedOperationException();
		else
			return grant(entryPoint);
	}

	@Override
	public synchronized IuAuthorizationGrant grant(URI resourceUri) {
		if (!OAuthSpi.isRoot(OAuthSpi.getClient(realm).getResourceUri(), resourceUri))
			throw new IllegalArgumentException("Invalid resource URI for this client");

		var grant = grants.get(resourceUri);
		if (grant == null)
			grants.put(resourceUri, grant = new AuthorizationCodeGrant(realm, resourceUri));
		return grant;
	}

	@Override
	public URI authorize(String code, String state) throws IuAuthenticationException {
		synchronized (grants) {
			for (final var grant : grants.values()) {
				final var uri = grant.authorize(code, state);
				if (uri != null)
					return uri;
			}
		}

		final Map<String, String> challengeAttributes = new LinkedHashMap<>();
		challengeAttributes.put("realm", realm);
		challengeAttributes.put("error", "invalid_request");
		challengeAttributes.put("error_description", "invalid state");

		final var challenge = new IuAuthenticationException(HttpUtils.createChallenge("Bearer", challengeAttributes));
		challenge.setLocation(entryPoint);
		throw challenge;
	}

}
