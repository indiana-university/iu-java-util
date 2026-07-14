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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.IuOidcTokenResponse;
import iu.oidc.client.config.IuOidcClient;
import iu.oidc.client.config.IuOidcClientReference;
import iu.oidc.client.config.IuOidcProvider;

@SuppressWarnings("javadoc")
public class OnBehalfOfGrantTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	@Test
	public void testGrantType() {
		final var clientId = IdGenerator.generateId();
		final var assertionJwk = WebKey.builder(WebKey.Type.ED25519).algorithm(Algorithm.EDDSA).ephemeral().build();
		final var client = mock(IuOidcClient.class, CALLS_REAL_METHODS);
		when(client.getClientId()).thenReturn(clientId);
		when(client.getAssertionJwk()).thenReturn(assertionJwk);

		final var accessToken = IdGenerator.generateId();
		final var uri = URI.create(IdGenerator.generateId());

		final var tokenEndpoint = URI.create(IdGenerator.generateId());
		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getTokenEndpoint()).thenReturn(tokenEndpoint);
		final var provider = mock(IuOidcProvider.class, CALLS_REAL_METHODS);
		when(provider.getMetadata()).thenReturn(metadata);

		final var config = mock(IuOidcClientReference.class);
		when(config.getClient()).thenReturn(client);
		when(config.getProvider()).thenReturn(provider);

		final var refreshToken = IdGenerator.generateId();
		final var response = mock(IuOidcTokenResponse.class);
		when(response.getRefreshToken()).thenReturn(refreshToken);

		final var grant = new OnBehalfOfGrant(config, uri, accessToken);
		final Map<String, Iterable<String>> params = new LinkedHashMap<>();
		final var rb = mock(HttpRequest.Builder.class);
		grant.tokenAuth(rb, params);
		assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", params.get("grant_type").iterator().next());
		assertEquals(accessToken, params.get("assertion").iterator().next());
		assertEquals(clientId, params.get("client_id").iterator().next());
		assertEquals("on_behalf_of", params.get("requested_token_use").iterator().next());
		final var assertion = WebToken.verify(params.get("client_assertion").iterator().next(), assertionJwk);
		assertion.validateClaims(URI.create(clientId), tokenEndpoint, Duration.ofMinutes(15L));
		assertEquals(clientId, assertion.getSubject());
	}

}
