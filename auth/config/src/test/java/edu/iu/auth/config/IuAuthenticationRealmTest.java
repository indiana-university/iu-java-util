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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import iu.auth.config.AuthConfig;

@SuppressWarnings("javadoc")
public class IuAuthenticationRealmTest {

	@Test
	public void testOf() {
		final var authId = IdGenerator.generateId();
		final var realm = mock(TokenEndpoint.class);
		try (final var mockAuthConfig = mockStatic(AuthConfig.class)) {
			mockAuthConfig
					.when(() -> AuthConfig.load(eq(IuAuthenticationRealm.class), eq("realm/" + authId), argThat(a -> {
						assertSame(TokenEndpoint.class, a.apply(IuJson.object().add("type", "token_endpoint").build()));
						return true;
					}))).thenReturn(realm);
			assertSame(realm, IuAuthenticationRealm.of(authId));
		}
	}

	@Test
	public void testJson() {
		final var value = IuJson.object().add("type", "pki").build();
		try (final var mockJson = mockStatic(IuJson.class)) {
			IuAuthenticationRealm.JSON.fromJson(value);
			mockJson.verify(() -> IuJson.wrap(eq(value), eq(IuPrivateKeyPrincipal.class), any()));
		}
	}

	@Test
	public void testJsonMissingType() {
		final var value = IuJson.object().build();
		final var e = assertThrows(IllegalArgumentException.class, () -> IuAuthenticationRealm.JSON.fromJson(value));
		assertEquals("Authentication realm definition missing type", e.getMessage(), () -> {
			e.printStackTrace();
			return e.getMessage();
		});
	}

}
