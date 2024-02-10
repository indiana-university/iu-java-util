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
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationCodeGrant;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;
import iu.auth.util.HttpUtils;

/**
 * OAuth authorization session implementation.
 */
public class AuthorizationSession implements IuAuthorizationSession {

	private final Collection<IuAuthorizationCodeGrant> grants = new ArrayDeque<>();

	private final IuAuthorizationClient client;
	private final URI entryPoint;

	/**
	 * Default constructor.
	 * 
	 * @param client     {@link IuAuthorizationClient}
	 * @param entryPoint {@link URI} to redirect the user agent to in order restart
	 *                   the authentication process.
	 */
	public AuthorizationSession(String realm, IuAuthorizationClient client) {
		this.realm = realm;
		this.client = client;
	}

	@Override
	public IuAuthorizationGrant getClientCredentialsGrant(String scope) {
		ClientCredentialsGrant grant;
		synchronized (clientGrants) {
			grant = clientGrants.get(scope);
			if (grant == null)
				grant = new ClientCredentialsGrant(client, scope);
			clientGrants.put(scope, grant);
		}

		return grant;
	}

	@Override
	public IuAuthorizationCodeGrant createAuthorizationCodeGrant(String scope) {
		final var grant = new AuthorizationCodeGrant(client, scope);
		synchronized (codeGrants) {
			codeGrants.put(grant.getState(), grant);
		}
		return grant;
	}

	@Override
	public IuAuthorizationCodeGrant getAuthorizationCodeGrant(String state) {
		final IuAuthorizationCodeGrant grant;
		synchronized (codeGrants) {
			grant = codeGrants.remove(state);
		}
//		IuAuthenticationChallengeException - if the state value cannot be tiedto a valid existing grant
		// IuAuthenticationRedirectException - if the state value is tied to anexpired
		// grant

		if (grant == null)
			throw new IuAuthenticationException(HttpUtils.createChallenge("Bearer", Map.of("realm", realm)));
		else
			return grant;
	}

}
