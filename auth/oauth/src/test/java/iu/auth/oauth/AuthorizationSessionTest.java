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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.client.IuHttp;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class AuthorizationSessionTest extends IuOAuthTestCase {

	@Test
	public void testEntryPoint() throws URISyntaxException, IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		final var client = mock(IuAuthorizationClient.class);
		final var resourceUri = new URI("foo:/bar");
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		IuAuthorizationClient.initialize(client);

		final var entryPointUri = new URI("foo:/bar/baz");
		final var session = IuAuthorizationSession.create(realm, entryPointUri);
		final var grant = session.grant();
		assertSame(grant, session.grant());

		final var authException = assertThrows(IuAuthenticationException.class, () -> session.authorize("foo", "bar"));
		assertEquals("Bearer realm=\"" + realm + "\" error=\"invalid_request\" error_description=\"invalid state\"",
				authException.getMessage());
		assertEquals(entryPointUri, authException.getLocation());
	}

	@Test
	public void testResourceUri() throws URISyntaxException {
		final var realm = IdGenerator.generateId();
		final var client = mock(IuAuthorizationClient.class);
		final var resourceUri = new URI("foo:/bar");
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		IuAuthorizationClient.initialize(client);

		final var session = IuAuthorizationSession.create(realm, null);
		assertThrows(UnsupportedOperationException.class, session::grant);

		assertThrows(IllegalArgumentException.class, () -> session.grant(new URI("foo:/baz")));
		final var successUri = new URI("foo:/bar/baz");
		final var grant = session.grant(successUri);
		assertSame(grant, session.grant(successUri));

		final var authException = assertThrows(IuAuthenticationException.class, () -> session.authorize("foo", "bar"));
		assertEquals("Bearer realm=\"" + realm + "\" error=\"invalid_request\" error_description=\"invalid state\"",
				authException.getMessage());
		assertNull(authException.getLocation());
	}

	@Test
	public void testAuthorize() throws URISyntaxException, IuAuthenticationException {
		final var realm = IdGenerator.generateId();
		MockPrincipal.registerVerifier(realm);
		final var resourceUri = new URI("foo:/bar");
		final var redirectUri = new URI("foo:/baz");
		final var authEndpointUri = new URI("foo:/authorize");
		final var tokenEndpointUri = new URI("foo:/token");
		final var client = mock(IuAuthorizationClient.class);
		final var clientCredentials = mock(IuApiCredentials.class);
		final var clientId = IdGenerator.generateId();
		final var principal = new MockPrincipal(realm);
		when(clientCredentials.getName()).thenReturn(clientId);
		when(client.getRealm()).thenReturn(realm);
		when(client.getResourceUri()).thenReturn(resourceUri);
		when(client.getRedirectUri()).thenReturn(redirectUri);
		when(client.getAuthorizationEndpoint()).thenReturn(authEndpointUri);
		when(client.getTokenEndpoint()).thenReturn(tokenEndpointUri);
		when(client.getAuthorizationCodeAttributes()).thenReturn(Map.of("foo", "bar"));
		when(client.getScope()).thenReturn(List.of("foo", "bar"));
		when(client.getCredentials()).thenReturn(clientCredentials);
		when(client.verify(any())).thenReturn(principal);
		when(client.verify(any(), any())).thenReturn(principal);
		IuAuthorizationClient.initialize(client);

		final var entryPointUri = new URI("foo:/bar/baz");
		final var session = IuAuthorizationSession.create(realm, entryPointUri);
		final var grant = session.grant();

		IuTestLogger.allow("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE);
		final var authException = assertThrows(IuAuthenticationException.class, () -> grant.authorize(entryPointUri));
		final var matcher = Pattern
				.compile("Bearer realm=\"" + realm + "\" scope=\"foo bar\" state=\"([0-9A-Za-z_\\-]{32})\" foo=\"bar\"")
				.matcher(authException.getMessage());
		assertTrue(matcher.matches(), authException::getMessage);
		final var state = matcher.group(1);
		final var code = IdGenerator.generateId();

		try (final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttp = mockStatic(IuHttp.class)) {
			final var hrb = mock(HttpRequest.Builder.class);
			final var payload = "grant_type=authorization_code&code=" + code
					+ "&scope=foo+bar&redirect_uri=foo%3A%2Fbaz";
			final var bp = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(payload)).thenReturn(bp);
			when(hrb.POST(bp)).thenReturn(hrb);

			final var accessToken = IdGenerator.generateId();
			final var refreshToken = IdGenerator.generateId();
			final var tokenResponse = Json.createObjectBuilder().add("token_type", "Bearer")
					.add("access_token", accessToken).add("refresh_token", refreshToken).add("expires_in", 1).build();
			mockHttp.when(() -> IuHttp.send(eq(IuAuthenticationException.class), eq(tokenEndpointUri), argThat(a -> {
				IuException.unchecked(() -> a.accept(hrb));
				return true;
			}), eq(AbstractGrant.JSON_OBJECT_NOCACHE))).thenReturn(tokenResponse);

			assertEquals(entryPointUri, session.authorize(code, state));
		}

	}

}
