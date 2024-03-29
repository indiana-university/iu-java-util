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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import iu.auth.util.HttpUtils;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class OpenIdProviderTest {

	@Test
	public void testHydrate() throws Exception {
		final var issuer = IdGenerator.generateId();
		final var configUri = new URI("test:" + IdGenerator.generateId());
		final var userinfoEndpoint = new URI("test:" + IdGenerator.generateId());
		try (final var mockHttpUtils = mockStatic(HttpUtils.class)) {
			mockHttpUtils.when(() -> HttpUtils.read(configUri)).thenReturn(Json.createObjectBuilder()
					.add("issuer", issuer).add("userinfo_endpoint", userinfoEndpoint.toString()).build());

			IuTestLogger.expect("iu.auth.oidc.OpenIdProvider", Level.INFO, "OIDC Provider configuration:.*");
			final var provider = new OpenIdProvider(configUri, null);
			assertThrows(IllegalStateException.class, provider::createAuthorizationClient);

			final var accessToken = IdGenerator.generateId();
			final var principal = IdGenerator.generateId();

			try (final var mockHttpRequest = mockStatic(HttpRequest.class)) {
				final var uireqb = mock(HttpRequest.Builder.class);
				when(uireqb.header(any(), any())).thenReturn(uireqb);
				final var uireq = mock(HttpRequest.class);
				mockHttpUtils.when(() -> HttpUtils.read(uireq)).thenReturn(Json.createObjectBuilder()
						.add("principal", principal).add("sub", principal).add("foo", "bar").add("abc", 123).build());
				when(uireqb.build()).thenReturn(uireq);
				mockHttpRequest.when(() -> HttpRequest.newBuilder(eq(userinfoEndpoint))).thenReturn(uireqb);
				final var subject = provider.hydrate(accessToken);
				assertEquals(principal, subject.getPrincipals().iterator().next().getName());
			}
		}
	}

}
