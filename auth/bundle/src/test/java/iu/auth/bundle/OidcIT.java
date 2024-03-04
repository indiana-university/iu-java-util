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
package iu.auth.bundle;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oidc.IuOpenIdClient;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class OidcIT {

	@SuppressWarnings("unchecked")
	@Test
	public void testClient() throws URISyntaxException, IOException, InterruptedException {
		final var issuer = IdGenerator.generateId();
		final var jwksUri = new URI("test://localhost/" + IdGenerator.generateId());
		final var userinfoEndpoint = new URI("test://localhost/" + IdGenerator.generateId());

		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockHttpClient = mockStatic(HttpClient.class)) {
			final var configUri = new URI("test://localhost/" + IdGenerator.generateId());
			final var rb = mock(HttpRequest.Builder.class);
			final var r = mock(HttpRequest.class);
			final var h = mock(HttpHeaders.class);
			when(r.headers()).thenReturn(h);
			when(r.uri()).thenReturn(configUri);
			when(rb.build()).thenReturn(r);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(configUri)).thenReturn(rb);

			final Map<String, List<String>> map = new LinkedHashMap<>();
			final var rh = mock(HttpHeaders.class);
			final var resp = mock(HttpResponse.class);
			when(rh.map()).thenReturn(map);
			when(resp.headers()).thenReturn(rh);
			when(resp.request()).thenReturn(r);
			when(resp.statusCode()).thenReturn(200);
			when(resp.body()).thenReturn(new ByteArrayInputStream(Json.createObjectBuilder() //
					.add("issuer", issuer) //
					.add("userinfo_endpoint", userinfoEndpoint.toString()) //
					.add("jwks_uri", jwksUri.toString()) //
					.build().toString().getBytes("UTF-8")));

			final var client = mock(HttpClient.class);
			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(client);
			when(client.send(r, BodyHandlers.ofInputStream())).thenReturn(resp);

			final var idClient = mock(IuOpenIdClient.class);

			IuTestLogger.allow("iu.auth.util.HttpUtils", Level.FINE);
			IuTestLogger.allow("iu.auth.oidc.OpenIdProvider", Level.INFO);
			assertNotNull(IuOpenIdProvider.from(configUri, idClient));
		}
	}

}
