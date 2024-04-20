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
package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;

@SuppressWarnings("javadoc")
public class IuVaultTest {

	private boolean with(Properties props) throws Exception {
		if (this.getClass().getClassLoader() != ClassLoader.getSystemClassLoader())
			return true;

		final var methodName = new Throwable().getStackTrace()[1].getMethodName();
		final var classes = IuException.unchecked(() -> Path.of("target", "classes").toRealPath());
		try (final var c = new URLClassLoader(new URL[] { //
				IuException.unchecked(() -> classes.toUri().toURL()), //
				IuException.unchecked(() -> Path.of("target", "test-classes").toRealPath().toUri().toURL()), //
		}) {
			{
				registerAsParallelCapable();
			}

			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				if (!name.startsWith("edu.iu.client.") && !name.startsWith("iu.client."))
					return super.loadClass(name);
				else
					return findClass(name);
			}
		}) {
			final var con = ModuleLayer.defineModules(Configuration.resolve(ModuleFinder.of(classes),
					List.of(ModuleLayer.boot().configuration()), ModuleFinder.of(), Set.of("iu.util.client")),
					List.of(ModuleLayer.boot()), a -> c);
			final var mod = con.layer().findModule("iu.util.client").get();
			con.addReads(mod, ClassLoader.getSystemClassLoader().getUnnamedModule());

			final var restore = new Properties();
			for (final var name : props.stringPropertyNames()) {
				final var toRestore = System.getProperty(name);
				if (toRestore != null)
					restore.setProperty(name, toRestore);
				System.setProperty(name, props.getProperty(name));
			}
			try {
				final var alternateTestClass = IuException.unchecked(() -> c.loadClass(getClass().getName()));
				IuException.checkedInvocation(() -> alternateTestClass.getMethod(methodName)
						.invoke(alternateTestClass.getConstructor().newInstance()));
			} finally {
				for (final var name : props.stringPropertyNames()) {
					final var toRestore = restore.getProperty(name);
					if (toRestore != null)
						System.setProperty(name, toRestore);
					else
						System.clearProperty(name);
				}
			}
		}
		return false;
	}

	@BeforeEach
	public void setup() throws Exception {
		final var runtime = IuVault.class.getDeclaredField("RUNTIME");
		runtime.setAccessible(true);

		final var secrets = IuVault.class.getDeclaredField("secrets");
		secrets.setAccessible(true);

		final var vault = runtime.get(null);
		if (vault != null)
			((Map<?, ?>) secrets.get(vault)).clear();
	}

	@Test
	public void testIsConfigured() throws Exception {
		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", "vault://kv");
		props.setProperty("iu.vault.secrets", "a");
		props.setProperty("iu.vault.token", IdGenerator.generateId());
		if (with(props))
			assertTrue(IuVault.isConfigured());

		if (!IuVault.isConfigured())
			assertNull(IuVault.RUNTIME);
	}

	@Test
	public void testIsNot() throws Exception {
		final var props = new Properties();
		props.setProperty("iu.vault.secrets", "");
		if (with(props))
			assertFalse(IuVault.isConfigured());

		if (IuVault.isConfigured())
			assertNotNull(IuVault.RUNTIME);
	}

	@Test
	public void testGetPropertiesUsingToken() throws Exception {
		final var props = new Properties();
		props.setProperty("iu.http.allowedUri", "vault://kv");
		props.setProperty("iu.vault.token", IdGenerator.generateId());
		props.setProperty("iu.vault.endpoint", "vault://kv");
		props.setProperty("iu.vault.secrets", "a/b,a/c,a/d,a/e");
		if (!with(props))
			return;

		final var token = Objects.requireNonNull(System.getProperty("iu.vault.token"));
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			final var mockRequestBuilder = mock(HttpRequest.Builder.class);
			mockHttp.when(() -> IuHttp.send(eq(URI.create("vault://kv/data/a/b")), watch(mockRequestBuilder), any()))
					.thenReturn(IuJson.parse("{\"data\":{\"data\":{\"foo\":\"bar\"}}}"));
			mockHttp.when(() -> IuHttp.send(eq(URI.create("vault://kv/data/a/c")), watch(mockRequestBuilder), any()))
					.thenReturn(IuJson.parse("{\"data\":{\"data\":{\"bar\":\"baz\"}}}"));
			mockHttp.when(() -> IuHttp.send(eq(URI.create("vault://kv/data/a/d")), watch(mockRequestBuilder), any()))
					.thenReturn(IuJson.parse("{\"data\":{\"data\":{\"num\":42}}}"));
			mockHttp.when(() -> IuHttp.send(eq(URI.create("vault://kv/data/a/e")), watch(mockRequestBuilder), any()))
					.thenReturn(IuJson.parse("{\"data\":{\"data\":{\"bool\":true}}}"));

			assertEquals("bar", IuVault.RUNTIME.get("foo"));
			assertEquals("baz", IuVault.RUNTIME.get("bar"));
			assertEquals(42, IuVault.RUNTIME.get("num", IuJsonAdapter.<Number>basic()).intValue());
			assertTrue(IuVault.RUNTIME.get("bool", IuJsonAdapter.<Boolean>basic()));
			assertThrows(IllegalArgumentException.class, () -> IuVault.RUNTIME.get("baz"));
			verify(mockRequestBuilder, times(4)).header("Authorization", "Bearer " + token);
		}
	}

	@Test
	public void testGetPropertiesUsingApprole() throws Exception {
		final var props = new Properties();
		props.setProperty("iu.http.allowedUri", "vault://kv,vault://login");
		props.setProperty("iu.vault.token", "");
		props.setProperty("iu.vault.endpoint", "vault://kv");
		props.setProperty("iu.vault.loginEndpoint", "vault://login");
		props.setProperty("iu.vault.roleId", IdGenerator.generateId());
		props.setProperty("iu.vault.secretId", IdGenerator.generateId());
		props.setProperty("iu.vault.secrets", "a/b");
		if (!with(props))
			return;

		final var token = IdGenerator.generateId();
		final var roleId = Objects.requireNonNull(System.getProperty("iu.vault.roleId"));
		final var secretId = Objects.requireNonNull(System.getProperty("iu.vault.secretId"));
		try (final var mockHttp = mockStatic(IuHttp.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final var loginRequestPayload = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(a -> {
				assertEquals("{\"role_id\":\"" + roleId + "\",\"secret_id\":\"" + secretId + "\"}", a);
				return true;
			}))).thenReturn(loginRequestPayload);

			final var loginBuilder = mock(HttpRequest.Builder.class);
			mockHttp.when(() -> IuHttp.send(eq(URI.create("vault://login")), watch(loginBuilder), any()))
					.thenReturn(IuJson.parse("{\"auth\":{\"client_token\":\"" + token + "\"}}"));

			final var dataBuilder = mock(HttpRequest.Builder.class);
			mockHttp.when(() -> IuHttp.send(eq(URI.create("vault://kv/data/a/b")), watch(dataBuilder), any()))
					.thenReturn(IuJson
							.parse("{\"data\":{\"data\":{\"foo\":\"bar\",\"bar\":\"baz\",\"num\":42,\"bool\":true}}}"));

			assertEquals("bar", IuVault.RUNTIME.get("foo"));
			assertEquals("baz", IuVault.RUNTIME.get("bar"));
			assertEquals(42, IuVault.RUNTIME.get("num", IuJsonAdapter.<Number>basic()).intValue());
			assertTrue(IuVault.RUNTIME.get("bool", IuJsonAdapter.<Boolean>basic()));
			assertThrows(IllegalArgumentException.class, () -> IuVault.RUNTIME.get("baz"));

			verify(loginBuilder).POST(loginRequestPayload);
			verify(loginBuilder).header("Content-Type", "application/json; charset=utf-8");

			verify(dataBuilder).header("Authorization", "Bearer " + token);
		}
	}

	private Consumer<HttpRequest.Builder> watch(HttpRequest.Builder requestBuilder) {
		return argThat(c -> {
			c.accept(requestBuilder);
			return true;
		});
	}

}
