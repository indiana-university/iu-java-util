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
package iu.oidc.client;

import java.net.URI;
import java.net.http.HttpRequest.Builder;
import java.util.Map;

import edu.iu.IuIterable;
import iu.oidc.client.config.IuOidcClientReference;

/**
 * Authenticates to an OAuth 2.0 Token endpoint, verifies and holds a JWT access
 * token until expired.
 */
public class OnBehalfOfGrant extends OidcTokenGrant {

	private final URI resource;
	private final String accessToken;

	/**
	 * Constructor.
	 * 
	 * @param config      {@link IuOidcClientReference}
	 * @param resource    API root resource {@link URI}
	 * @param accessToken Access token issued to the client application
	 */
	public OnBehalfOfGrant(IuOidcClientReference config, URI resource, String accessToken) {
		super(config);
		this.resource = resource;
		this.accessToken = accessToken;
	}

	@Override
	protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) {
		params.put("grant_type", IuIterable.iter("urn:ietf:params:oauth:grant-type:jwt-bearer"));
		params.put("resource", IuIterable.iter(resource.toString()));
		addClientAuth(requestBuilder, params);
		params.put("assertion", IuIterable.iter(accessToken));
		params.put("requested_token_use", IuIterable.iter("on_behalf_of"));
	}

}
