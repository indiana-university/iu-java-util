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
package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.client.IuJson;
import iu.auth.config.AuthConfig;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

@SuppressWarnings("javadoc")
public class IuAuthorizationClientTest {

	@Test
	public void testAuthMethodFrom() {
		for (final var authMethod : AuthMethod.values())
			assertSame(authMethod, AuthMethod.from(authMethod.parameterValue));
	}

	@Test
	public void testGrantTypeFrom() {
		for (final var grantType : GrantType.values())
			assertSame(grantType, GrantType.from(grantType.parameterValue));
	}

	@Test
	public void testOf() {
		final var authId = IdGenerator.generateId();
		final var client = mock(IuAuthorizationClient.class);
		try (final var mockAuthConfig = mockStatic(AuthConfig.class)) {
			mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizationClient.class, "client/" + authId))
					.thenReturn(client);
			assertSame(client, IuAuthorizationClient.of(authId));
		}
	}

	@Test
	public void testJsonString() {
		final var authId = IdGenerator.generateId();
		final var client = mock(IuAuthorizationClient.class);
		try (final var mockAuthConfig = mockStatic(AuthConfig.class)) {
			mockAuthConfig.when(() -> AuthConfig.load(IuAuthorizationClient.class, "client/" + authId))
					.thenReturn(client);
			assertSame(client, IuAuthorizationClient.JSON.fromJson(IuJson.string(authId)));
		}
	}

	@Test
	public void testJsonObject() {
		final var o = mock(JsonObject.class);
		when(o.asJsonObject()).thenReturn(o);
		try (final var mockJson = mockStatic(IuJson.class)) {
			IuAuthorizationClient.JSON.fromJson(o);
			mockJson.verify(() -> IuJson.wrap(eq(o), eq(IuAuthorizationClient.class), any()));
		}
	}

	@Test
	public void testAuthMethodJson() {
		for (final var a : AuthMethod.values()) {
			final var j = AuthMethod.JSON.toJson(a);
			assertEquals(a.parameterValue, ((JsonString) j).getString());
			assertEquals(a, AuthMethod.JSON.fromJson(j));
		}
	}

	@Test
	public void testGrantTypeJson() {
		for (final var a : GrantType.values()) {
			final var j = GrantType.JSON.toJson(a);
			assertEquals(a.parameterValue, ((JsonString) j).getString());
			assertEquals(a, GrantType.JSON.fromJson(j));
		}
	}

}