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
package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest.Builder;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.OAuthAuthorizationClient;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebToken;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OAuthAccessTokenGrantTest {

	@Test
	void testConstruction() {
		final var client = mock(OAuthAuthorizationClient.class);
		final var grant = new OAuthAccessTokenGrant(() -> client) {
			@Override
			protected void verifyToken(WebToken jwt) {
			}

			@Override
			protected void tokenAuth(Builder requestBuilder) {
			}
		};
		assertSame(client, grant.getClient());
	}

	@Test
	void testValidateJwt() {
		final var token = IdGenerator.generateId();
		final var jwksUri = URI.create(IdGenerator.generateId());
		final var key = mock(WebKey.class);
		try (final var mockWebToken = mockStatic(WebToken.class); //
				final var mockWebKey = mockStatic(WebKey.class)) {
			final var client = mock(OAuthAuthorizationClient.class);
			when(client.getJwksUri()).thenReturn(jwksUri);
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).thenReturn(List.of(key));
			final var grant = new OAuthAccessTokenGrant(() -> client) {
				@Override
				protected void verifyToken(WebToken jwt) {
				}

				@Override
				protected void tokenAuth(Builder requestBuilder) {
				}
			};
			assertDoesNotThrow(() -> grant.validateJwt(token));
			mockWebToken.verify(() -> WebToken.verify(token, key));
		}
	}

	@Test
	public void testGetAccessToken() {
		final var token = IdGenerator.generateId();
		final var tokenUri = URI.create(IdGenerator.generateId());
		try (final var mockWebToken = mockStatic(WebToken.class); //
				final var mockWebKey = mockStatic(WebKey.class); //
				final var mockIuHttp = mockStatic(IuHttp.class)) {
			final var client = mock(OAuthAuthorizationClient.class);
			when(client.getTokenUri()).thenReturn(tokenUri);
			mockIuHttp.when(() -> IuHttp.send(eq(tokenUri), argThat(a -> {
				final var rb = mock(Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				return true;
			}), eq(IuHttp.READ_JSON_OBJECT)))
					.thenReturn(IuJson.object().add("access_token", token).add("expires_in", 1).build());

			final var grant = new OAuthAccessTokenGrant(() -> client) {
				WebToken verified;
				Builder tokenAuthCalled;

				@Override
				protected void verifyToken(WebToken jwt) {
					verified = jwt;
				}

				@Override
				protected void tokenAuth(Builder requestBuilder) {
					tokenAuthCalled = requestBuilder;
				}
			};
			assertEquals(token, grant.getAccessToken());
			assertNull(grant.verified);
			assertNotNull(grant.tokenAuthCalled);

			// cached
			grant.tokenAuthCalled = null;
			assertEquals(token, grant.getAccessToken());
			assertNull(grant.verified);
			assertNull(grant.tokenAuthCalled);

			// not cached
			final var key = mock(WebKey.class);
			final var jwksUri = URI.create(IdGenerator.generateId());
			final var webToken = mock(WebToken.class);
			mockWebToken.when(() -> WebToken.verify(token, key)).thenReturn(webToken);
			when(client.getJwksUri()).thenReturn(jwksUri);
			mockWebKey.when(() -> WebKey.readJwks(jwksUri)).thenReturn(List.of(key));
			assertDoesNotThrow(() -> Thread.sleep(1000L));
			assertEquals(token, grant.getAccessToken());
			assertEquals(webToken, grant.verified);
			assertNotNull(grant.tokenAuthCalled);
		}
	}

}