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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.Client.AuthMethod;
import edu.iu.auth.config.Client.Credentials;
import edu.iu.auth.config.Client.GrantType;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVault;
import edu.iu.test.IuTest;
import jakarta.json.JsonObject;

@SuppressWarnings("javadoc")
public class AuthConfigTest {

	private static final class Config implements IuAuthConfig {
		private final String realm;

		private Config(String realm) {
			this.realm = realm;
		}

		@Override
		public String getRealm() {
			return realm;
		}

		@Override
		public String getAuthScheme() {
			return null;
		}

		@Override
		public URI getAuthenticationEndpoint() {
			return null;
		}

	}

	interface LoadableConfig {
	}

	interface UnloadableConfig {
	}

	@BeforeEach
	public void setup() throws Exception {
		Field f;

		f = AuthConfig.class.getDeclaredField("sealed");
		f.setAccessible(true);
		f.set(null, false);

		f = AuthConfig.class.getDeclaredField("CONFIG");
		f.setAccessible(true);
		((Map<?, ?>) f.get(null)).clear();

		f = AuthConfig.class.getDeclaredField("VAULT");
		f.setAccessible(true);
		((Map<?, ?>) f.get(null)).clear();
	}

	@Test
	public void testSealed() {
		final var realm = IdGenerator.generateId();
		assertThrows(IllegalStateException.class, () -> AuthConfig.get(realm));
		assertThrows(IllegalStateException.class, () -> AuthConfig.get(Config.class));

		final var config = new Config(realm);
		assertDoesNotThrow(() -> AuthConfig.register(config));
		assertThrows(IllegalArgumentException.class, () -> AuthConfig.register(config));

		AuthConfig.seal();
		assertSame(config, AuthConfig.get(realm));
		assertSame(config, AuthConfig.get(Config.class).iterator().next());
		assertThrows(IllegalStateException.class, () -> AuthConfig.register(config));
	}

	@Test
	public void testVault() {
		final var key = IdGenerator.generateId();
		assertThrows(IllegalStateException.class, () -> AuthConfig.load(LoadableConfig.class, key));

		final var vault = mock(IuVault.class);
		assertDoesNotThrow(() -> AuthConfig.addVault(LoadableConfig.class, vault));
		assertThrows(IllegalArgumentException.class, () -> AuthConfig.addVault(LoadableConfig.class, vault));

		AuthConfig.seal();
		assertThrows(IllegalStateException.class, () -> AuthConfig.addVault(UnloadableConfig.class, vault));
		when(vault.get(key)).thenReturn("{}");
		assertInstanceOf(LoadableConfig.class, AuthConfig.load(LoadableConfig.class, key));
		verify(vault).get(key);
	}

	@Test
	public void testAdaptJsonDefault() {
		try (final var mockJsonAdapter = mockStatic(IuJsonAdapter.class)) {
			AuthConfig.adaptJson(String.class);
			mockJsonAdapter.verify(() -> IuJsonAdapter.of(eq((Type) String.class), any()));
		}
	}

	@Test
	public void testAdaptJsonRealm() {
		final var authId = IdGenerator.generateId();
		final var realm = mock(Realm.class);
		try (final var mockRealm = mockStatic(Realm.class)) {
			mockRealm.when(() -> Realm.of(authId)).thenReturn(realm);
			assertSame(realm, AuthConfig.adaptJson(Realm.class).fromJson(IuJson.string(authId)));
		}
	}

	@Test
	public void testAdaptJsonCredentials() {
		final var cred = mock(JsonObject.class);
		when(cred.asJsonObject()).thenReturn(cred);
		final var credentials = mock(Credentials.class);
		try (final var mockJson = mockStatic(IuJson.class)) {
			mockJson.when(() -> IuJson.wrap(eq(cred), eq(Credentials.class), any())).thenReturn(credentials);
			assertSame(credentials, AuthConfig.adaptJson(Credentials.class).fromJson(cred));
		}
	}

	@Test
	public void testAdaptJsonAuthMethod() {
		final var authMethod = IuTest.rand(AuthMethod.class);
		assertSame(authMethod,
				AuthConfig.adaptJson(AuthMethod.class).fromJson(IuJson.string(authMethod.parameterValue)));
	}

	@Test
	public void testAdaptJsonGrantType() {
		final var grantType = IuTest.rand(GrantType.class);
		assertSame(grantType, AuthConfig.adaptJson(GrantType.class).fromJson(IuJson.string(grantType.parameterValue)));
	}
}
