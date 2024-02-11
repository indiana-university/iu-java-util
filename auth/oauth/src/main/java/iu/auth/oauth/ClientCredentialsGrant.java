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
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.IuBadRequestException;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import iu.auth.util.HttpUtils;

/**
 * Represents an OAuth client credentials grant.
 */
final class ClientCredentialsGrant extends AbstractGrant {
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(ClientCredentialsGrant.class.getName());

	/**
	 * Constructor.
	 * 
	 * @param realm authentication realm
	 * @param scope authorization scope
	 */
	ClientCredentialsGrant(String realm) {
		super(realm);
	}

	@Override
	public final IuApiCredentials authorize(URI resourceUri) throws IuAuthenticationException {
		final var client = OAuthSpi.getClient(realm);
		if (!OAuthSpi.isRoot(client.getResourceUri(), resourceUri))
			throw new IuBadRequestException("Invalid resource URI for this client");

		final var activatedCredentials = activate();
		if (activatedCredentials != null)
			return activatedCredentials;

		if (isExpired())
			LOG.fine("Authorized session has expired, initiating client credentials flow for " + client.getRealm());
		else
			LOG.fine(() -> "Authorization required, initiating client credentials flow for " + client.getRealm());

		final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
		tokenRequestParams.put("grant_type", List.of("client_credentials"));

		final var scope = client.getScope();
		if (scope != null)
			tokenRequestParams.put("scope", List.of(validatedScope));

		final var clientAttributes = client.getClientCredentialsAttributes();
		if (clientAttributes != null)
			for (final var clientAttributeEntry : clientAttributes.entrySet()) {
				final var name = clientAttributeEntry.getKey();
				if (name.equals("grant_type") || name.equals("scope"))
					throw new IllegalArgumentException(
							"Illegal attempt to override standard client credentials attribute " + name);
				else
					tokenRequestParams.put(name, List.of(clientAttributeEntry.getValue()));
			}

		final var tokenRequestBuilder = HttpRequest.newBuilder(client.getTokenEndpoint());
		tokenRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
		tokenRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
		client.getCredentials().applyTo(tokenRequestBuilder);

		return verify(new TokenResponse(client.getScope(), clientAttributes,
				HttpUtils.read(tokenRequestBuilder.build()).asJsonObject()));
	}

}
