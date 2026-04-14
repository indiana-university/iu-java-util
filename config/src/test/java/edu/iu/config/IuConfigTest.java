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
package edu.iu.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVault;
import edu.iu.client.IuVaultKeyedValue;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import iu.crypt.CryptJsonAdapters;

@SuppressWarnings("javadoc")
public class IuConfigTest {

	interface LoadableConfig {
		String getValue();
	}

	interface LoadableRef {
		LoadableConfig getConfig();
	}

	interface UnloadableConfig {
	}

	interface VerifiableConfig {
	}

	@BeforeEach
	public void setup() throws Exception {
		teardown();
	}

	@AfterEach
	public void teardown() throws Exception {
		Field f;

		f = IuConfig.class.getDeclaredField("sealed");
		f.setAccessible(true);
		f.set(null, false);

		f = IuConfig.class.getDeclaredField("STORAGE");
		f.setAccessible(true);
		((Map<?, ?>) f.get(null)).clear();

		Method m = IuConfig.class.getDeclaredMethod("registerDefaults");
		m.setAccessible(true);
		m.invoke(null);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testVault() {
		final var key = IdGenerator.generateId();
		final var invalidKey = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> IuConfig.load(LoadableConfig.class, key));

		final var vault = mock(IuVault.class);
		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableConfig.class, vault));
		assertThrows(IllegalArgumentException.class,
				() -> IuConfig.registerInterface("loadable", LoadableConfig.class, vault));

		final var vkv = mock(IuVaultKeyedValue.class);
		when(vkv.getValue()).thenReturn("{}");
		when(vault.get("loadable/" + key)).thenReturn(vkv);
		when(vault.get("loadable/" + invalidKey)).thenThrow(IllegalArgumentException.class);
		assertInstanceOf(LoadableConfig.class, IuConfig.load(LoadableConfig.class, key));
		verify(vault).get("loadable/" + key);
		assertInstanceOf(LoadableConfig.class, IuConfig.adaptJson(LoadableConfig.class).fromJson(IuJson.string(key)));
		verify(vault, times(2)).get("loadable/" + key);
		assertThrows(IllegalArgumentException.class, () -> IuConfig.load(LoadableConfig.class, invalidKey));

		IuConfig.seal();
		assertThrows(IllegalStateException.class,
				() -> IuConfig.registerInterface("unloadable", UnloadableConfig.class, vault));
		assertInstanceOf(LoadableConfig.class, IuConfig.load(LoadableConfig.class, key));
		verify(vault, times(3)).get("loadable/" + key);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSealed() {
		IuConfig.seal();
		final var adapter = mock(IuJsonAdapter.class);
		assertThrows(IllegalStateException.class, () -> IuConfig.registerAdapter(UnloadableConfig.class, adapter));
	}

	@Test
	public void testAdaptJsonDefault() {
		try (final var mockJsonAdapter = mockStatic(IuJsonAdapter.class)) {
			IuConfig.adaptJson(String.class);
			mockJsonAdapter.verify(() -> IuJsonAdapter.of(eq((Type) String.class), any()));
		}
		assertThrows(IllegalArgumentException.class, () -> IuConfig.registerAdapter(WebKey.class, null));
	}

	@Test
	public void testAdaptGenericType() {
		final var type = mock(Type.class);
		try (final var mockJson = mockStatic(IuJsonAdapter.class)) {
			IuConfig.adaptJson(type);
			mockJson.verify(() -> IuJsonAdapter.of(eq(type), any()));
		}
	}

	@Test
	public void testAdaptWebKey() {
		assertSame(CryptJsonAdapters.WEBKEY, IuConfig.adaptJson(WebKey.class));
	}

	@Test
	public void testAdaptAlgorithm() {
		assertSame(CryptJsonAdapters.ALG, IuConfig.adaptJson(Algorithm.class));
	}

	@Test
	public void testAdaptEncryption() {
		assertSame(CryptJsonAdapters.ENC, IuConfig.adaptJson(Encryption.class));
	}

	@Test
	public void testAdaptCrl() {
		assertSame(CryptJsonAdapters.CRL, IuConfig.adaptJson(X509CRL.class));
	}

	@Test
	public void testAdaptCert() {
		assertSame(CryptJsonAdapters.CERT, IuConfig.adaptJson(X509Certificate.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testLoadable() {
		final var vault = mock(IuVault.class);

		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableConfig.class, vault));
		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableRef.class, vault));

		final var key = IdGenerator.generateId();
		final var vkv = mock(IuVaultKeyedValue.class);
		final var value = IdGenerator.generateId();
		when(vkv.getValue())
				.thenReturn(IuJson.object().add("config", IuJson.object().add("value", value)).build().toString());
		when(vault.get("loadable/" + key)).thenReturn(vkv);

		assertEquals(value, IuConfig.load(LoadableRef.class, key).getConfig().getValue());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testLoadableNoVault() {
		final var vault = mock(IuVault.class);

		assertDoesNotThrow(() -> IuConfig.registerInterface(LoadableConfig.class));
		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableRef.class, vault));

		final var key = IdGenerator.generateId();
		final var vkv = mock(IuVaultKeyedValue.class);
		final var value = IdGenerator.generateId();
		when(vkv.getValue())
				.thenReturn(IuJson.object().add("config", IuJson.object().add("value", value)).build().toString());
		when(vault.get("loadable/" + key)).thenReturn(vkv);

		assertEquals(value, IuConfig.load(LoadableRef.class, key).getConfig().getValue());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testLoadWithVerifier() {
		final var key = IdGenerator.generateId();
		final var vault = mock(IuVault.class);
		final var vkv = mock(IuVaultKeyedValue.class);
		when(vkv.getValue()).thenReturn("{}");
		when(vault.get("verifiable/" + key)).thenReturn(vkv);

		final var verifier = mock(java.util.function.Consumer.class);
		assertDoesNotThrow(() -> IuConfig.registerInterface("verifiable", VerifiableConfig.class, verifier, vault));

		VerifiableConfig config = IuConfig.load(VerifiableConfig.class, key);
		assertInstanceOf(VerifiableConfig.class, config);
		verify(verifier, times(1)).accept(config);
	}

}