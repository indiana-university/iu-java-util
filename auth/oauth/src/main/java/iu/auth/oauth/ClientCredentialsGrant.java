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

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.IuWebUtils;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationFailedException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationResponse;
import iu.auth.util.HttpUtils;

/**
 * Represents an OAuth client credentials grant..
 */
class ClientCredentialsGrant implements IuAuthorizationGrant {

	private final static Logger LOG = Logger.getLogger(ClientCredentialsGrant.class.getName());

	private final IuAuthorizationClient client;
	private final String scope;

	private AuthorizationResponse response;

	/**
	 * Constructor.
	 * 
	 * @param client confidential client
	 * @param scope  authorization scope
	 */
	ClientCredentialsGrant(IuAuthorizationClient client, String scope) {
		this.client = client;
		this.scope = scope;
	}

	@Override
	public String getClientId() {
		return client.getCredentials().getName();
	}

	@Override
	public String getScope() {
		return scope;
	}

	@Override
	public IuAuthorizationResponse authorize() throws IuAuthorizationFailedException {
		final String message;
		if (response == null)
			message = "Authentication required, initiating client credentials flow for " + client.getRealm();
		else if (response.isExpired())
			message = "Authenticated session has expired, initiating client credentials flow for " + client.getRealm();
		else
			return response;

		LOG.fine(message);

		final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
		tokenRequestParams.put("grant_type", List.of("client_credentials"));
		tokenRequestParams.put("scope", List.of(scope));

		final var clientAttributes = client.getClientCredentialsAttributes();
		if (clientAttributes != null)
			for (final var clientAttributeEntry : clientAttributes.entrySet()) {
				final var name = clientAttributeEntry.getKey();
				if (tokenRequestParams.containsKey(name))
					throw new IllegalArgumentException(
							"Illegal attempt to override standard client credentials attribute " + name);
				else
					tokenRequestParams.put(name, List.of(clientAttributeEntry.getValue()));
			}

		final var tokenRequestBuilder = HttpRequest.newBuilder(client.getTokenEndpoint());
		tokenRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
		tokenRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		client.getCredentials().applyTo(tokenRequestBuilder);

		final var tokenResponse = HttpUtils.read(tokenRequestBuilder.build()).asJsonObject();
		return response = new AuthorizationResponse(this, client, tokenResponse);
	}

}
