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
package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oidc.IuOpenIdClient;
import edu.iu.test.IuTestLogger;
import iu.auth.util.HttpUtils;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class SpiTest {

	@Test
	public void testOidcSpi() throws URISyntaxException {
		final var issuer = IdGenerator.generateId();
		final var spi = new OpenIdConnectSpi();
		final var uri = mock(URI.class);
		final var jwksUri = new URI("test:" + IdGenerator.generateId());
		final var userinfoUri = new URI("test:" + IdGenerator.generateId());
		final var tokenEndpointUri = new URI("test:" + IdGenerator.generateId());
		final var authorizationEndpointUri = new URI("test:" + IdGenerator.generateId());
		final var client = mock(IuOpenIdClient.class);
		final var configBuilder = Json.createObjectBuilder();
		configBuilder.add("issuer", issuer);
		configBuilder.add("jwks_uri", jwksUri.toString());
		configBuilder.add("userinfo_endpoint", userinfoUri.toString());
		configBuilder.add("token_endpoint", tokenEndpointUri.toString());
		configBuilder.add("authorization_endpoint", authorizationEndpointUri.toString());
		try (final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			mockHttpUtils.when(() -> HttpUtils.read(uri)).thenReturn(configBuilder.build());

			IuTestLogger.expect("iu.auth.oidc.OpenIdProvider", Level.INFO, "OIDC Provider configuration.*");
			final var provider = spi.getOpenIdProvider(uri, client);
			assertEquals(issuer, provider.getIssuer());
			assertEquals(userinfoUri, provider.getUserInfoEndpoint());

			IuTestLogger.expect("iu.auth.oidc.OpenIdProvider", Level.INFO, "OIDC Provider configuration.*");
			assertNotSame(provider, spi.getOpenIdProvider(uri, client));

			assertInstanceOf(OidcAuthorizationClient.class, provider.createAuthorizationClient());
		}
	}

}
