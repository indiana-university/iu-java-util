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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedStatic.Verification;

import edu.iu.IdGenerator;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.UnsafeConsumer;
import edu.iu.client.HttpException;
import edu.iu.client.IuHttp;
import edu.iu.client.IuHttpTestCase;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings("javadoc")
public class VaultTest extends IuHttpTestCase {

	private MockedStatic<IuRuntimeEnvironment> mockRuntime;
	private Handler handler;

	@BeforeEach
	public void setup() {
		mockRuntime = mockStatic(IuRuntimeEnvironment.class);
		Vault.isConfigured();
		handler = mock(Handler.class);
		final var log = LogManager.getLogManager().getLogger(Vault.class.getName());
		log.setLevel(Level.CONFIG);
		log.setUseParentHandlers(false);
		log.addHandler(handler);
	}

	@AfterEach
	public void teardown() {
		mockRuntime.close();
		LogManager.getLogManager().getLogger(Vault.class.getName()).removeHandler(handler);
	}

	@Test
	public void testOfEmptyProperties() {
		assertNull(Vault.of(null, IuJsonAdapter::of));
	}

	@Test
	public void testOfPropertiesMissingEndpoint() {
		final var props = new Properties();
		final var e = assertThrows(NullPointerException.class, () -> Vault.of(props, IuJsonAdapter::of));
		assertEquals("Missing iu.vault.endpoint", e.getMessage());
	}

	@Test
	public void testOfPropertiesNoCache() {
		final var key = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var secret = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.secrets", secret);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			final Verification readVault = () -> IuHttp.send(
					eq(URI.create(endpoint + "/" + URLEncoder.encode(secret, StandardCharsets.UTF_8))),
					withToken(token), eq(IuHttp.READ_JSON_OBJECT));

			mockHttp.when(readVault).thenReturn(IuJson.object() //
					.add("data", IuJson.object() //
							.add("data", IuJson.object().add(key, value))) //
					.build());

			assertEquals(value, vault.get(key));
			assertEquals(value, vault.get(key));
			mockHttp.verify(readVault, times(2));
		}
	}

	@Test
	public void testOfPropertiesWithCache() {
		final var key = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var secret = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.secrets", secret);
		props.setProperty("iu.vault.cacheTtl", "PT15S");

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			final Verification readVault = () -> IuHttp.send(
					eq(URI.create(endpoint + "/" + URLEncoder.encode(secret, StandardCharsets.UTF_8))),
					withToken(token), eq(IuHttp.READ_JSON_OBJECT));

			mockHttp.when(readVault).thenReturn(IuJson.object() //
					.add("data", IuJson.object() //
							.add("data", IuJson.object().add(key, value))) //
					.build());

			assertEquals(value, vault.get(key));
			assertEquals(value, vault.get(key));
			mockHttp.verify(readVault);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReadSecretNotFound() {
		final var secret = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.secrets", secret);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			final var r = mock(HttpResponse.class);
			when(r.statusCode()).thenReturn(404);
			final var e = mock(HttpException.class);
			when(e.getResponse()).thenReturn(r);
			mockHttp.when(() -> IuHttp.send(any(URI.class), any(), any())).thenThrow(e);

			assertEquals(IuJson.object().add("data", IuJson.object()).build(), vault.readSecret(secret));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReadSecretNotFoundCubbyhole() {
		final var secret = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.secrets", secret);
		props.setProperty("iu.vault.cubbyhole", "true");

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			final var r = mock(HttpResponse.class);
			when(r.statusCode()).thenReturn(404);
			final var e = mock(HttpException.class);
			when(e.getResponse()).thenReturn(r);
			mockHttp.when(() -> IuHttp.send(any(URI.class), any(), any())).thenThrow(e);

			assertEquals(IuJson.object().build(), vault.readSecret(secret));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReadSecretServerError() {
		final var secret = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.secrets", secret);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			final var r = mock(HttpResponse.class);
			when(r.statusCode()).thenReturn(500);
			final var e = mock(HttpException.class);
			when(e.getResponse()).thenReturn(r);
			mockHttp.when(() -> IuHttp.send(any(URI.class), any(), any())).thenThrow(e);

			assertSame(e, assertThrows(IllegalStateException.class, () -> vault.readSecret(secret)).getCause());
		}
	}

	@Test
	public void testOfPropertiesWithNoAuthConfig() {
		final var endpoint = URI.create("test:" + IdGenerator.generateId());
		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		final var e = assertThrows(NullPointerException.class, () -> Vault.of(props, IuJsonAdapter::of));
		assertEquals("Missing iu.vault.loginEndpoint or iu.vault.token", e.getMessage());
	}

	@Test
	public void testOfPropertiesWithNoRoleId() {
		final var endpoint = URI.create("test:" + IdGenerator.generateId());
		final var loginEndpoint = URI.create("test:" + IdGenerator.generateId());
		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.loginEndpoint", loginEndpoint.toString());
		final var e = assertThrows(NullPointerException.class, () -> Vault.of(props, IuJsonAdapter::of));
		assertEquals("Missing iu.vault.roleId", e.getMessage());
	}

	@Test
	public void testOfPropertiesWithNoSecretId() {
		final var endpoint = URI.create("test:" + IdGenerator.generateId());
		final var loginEndpoint = URI.create("test:" + IdGenerator.generateId());
		final var roleId = IdGenerator.generateId();
		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.loginEndpoint", loginEndpoint.toString());
		props.setProperty("iu.vault.roleId", roleId);
		final var e = assertThrows(NullPointerException.class, () -> Vault.of(props, IuJsonAdapter::of));
		assertEquals("Missing iu.vault.secretId", e.getMessage());
	}

	@Test
	public void testIsConfiguredNoEndpoint() {
		assertFalse(Vault.isConfigured());
	}

	@Test
	public void testIsConfiguredWithEndpoint() {
		final var endpoint = URI.create("test:" + IdGenerator.generateId());
		mockRuntime.when(() -> IuRuntimeEnvironment.envOptional(eq("iu.vault.endpoint"), any())).thenReturn(endpoint);
		assertTrue(Vault.isConfigured());
	}

	@Test
	public void testApprole() {
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());
		final var loginEndpoint = URI.create("test:/" + IdGenerator.generateId());
		final var key = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var secret = IdGenerator.generateId();
		final var roleId = IdGenerator.generateId();
		final var secretId = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.secrets", secret);
		props.setProperty("iu.vault.loginEndpoint", loginEndpoint.toString());
		props.setProperty("iu.vault.roleId", roleId);
		props.setProperty("iu.vault.secretId", secretId);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class); //
				final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final var loginPayload = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(IuJson.object() //
					.add("role_id", roleId) //
					.add("secret_id", secretId) //
					.build().toString())).thenReturn(loginPayload);

			final Verification approle = () -> IuHttp.send(eq(loginEndpoint), argThat(a -> {
				final var rb = mock(HttpRequest.Builder.class);
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("Content-Type", "application/json;charset=utf-8");
				verify(rb).POST(loginPayload);
				return true;
			}), eq(IuHttp.READ_JSON_OBJECT));
			mockHttp.when(approle).thenReturn(IuJson.object() //
					.add("auth", IuJson.object() //
							.add("lease_duration", 2) //
							.add("client_token", token) //
					).build());
			final Verification read = () -> IuHttp.send(
					eq(URI.create(endpoint + "/" + URLEncoder.encode(secret, StandardCharsets.UTF_8))),
					withToken(token), eq(IuHttp.READ_JSON_OBJECT));
			mockHttp.when(read).thenReturn(IuJson.object() //
					.add("data", IuJson.object() //
							.add("data", IuJson.object().add(key, value))) //
					.build());

			assertEquals(value, vault.get(key));
			assertEquals(value, vault.get(key));
			mockHttp.verify(approle);
			mockHttp.verify(read, times(2));

			assertDoesNotThrow(() -> Thread.sleep(2000L));
			assertEquals(value, vault.get(key));
			mockHttp.verify(approle, times(2));
			mockHttp.verify(read, times(3));
		}
	}

	@Test
	public void testGetWithoutSecretsConfigured() {
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		assertThrows(UnsupportedOperationException.class, () -> vault.get(IdGenerator.generateId()));
	}

	@Test
	public void testGetWithInvalidKey() {
		final var key = IdGenerator.generateId();
		final var secret = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.secrets", secret);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			final Verification readVault = () -> IuHttp.send(
					eq(URI.create(endpoint + "/" + URLEncoder.encode(secret, StandardCharsets.UTF_8))),
					withToken(token), eq(IuHttp.READ_JSON_OBJECT));

			mockHttp.when(readVault).thenReturn(IuJson.object() //
					.add("data", IuJson.object() //
							.add("data", IuJson.object())) //
					.build());

			final var e = assertThrows(IllegalArgumentException.class, () -> vault.get(key));
			assertEquals(key + " not found in Vault using " + endpoint + "/[" + secret + "]", e.getMessage());
		}
	}

	@Test
	public void testGetSecretThenSetWithoutCache() {
		assertGetSecretThenSet(null, false);
	}

	@Test
	public void testGetSecretThenSetWithCache() {
		assertGetSecretThenSet("PT15S", true);
	}

	@Test
	public void testDataUriInvalidSecret() {
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		assertThrows(IllegalArgumentException.class, () -> vault.dataUri("foo\fbar").toString());
	}

	@Test
	public void testDataUriKV2() {
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		assertEquals(endpoint + "/foo%2Fbar", vault.dataUri("foo/bar").toString());
	}

	@Test
	public void testDataUriCubbyhole() {
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.cubbyhole", "true");

		final var vault = Vault.of(props, IuJsonAdapter::of);
		assertEquals(endpoint + "/foo/bar", vault.dataUri("foo/bar").toString());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCubbyholeNotFoundThenSet() {
		final var key = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var secret = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.cubbyhole", "true");

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class); //
				final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final Verification readVault = () -> IuHttp.send(eq(vault.dataUri(secret)), argThat(a -> {
				class Box {
					boolean post;
				}
				final var box = new Box();
				final var rb = mock(HttpRequest.Builder.class);
				when(rb.POST(any())).then(b -> {
					box.post = true;
					return rb;
				});
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("X-Vault-Token", token);
				return !box.post;
			}), eq(IuHttp.READ_JSON_OBJECT));

			mockHttp.when(readVault).thenReturn( //
					IuJson.object() //
							.add("data", IuJson.object()) //
							.build(), // equivalent to 404 error handler
					IuJson.object() //
							.add("data", IuJson.object() //
									.add(key, value)) //
							.build());

			final var postPayloadJson = IuJson.object().add(key, value).build().toString();
			final var postPayload = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(postPayloadJson)).thenReturn(postPayload);

			mockHttp.when(() -> IuHttp.validate(any(), any())).thenCallRealMethod();
			mockHttp.when(() -> IuHttp.expectStatus(anyInt())).thenCallRealMethod();
			final Verification postVault = () -> IuHttp.send(eq(vault.dataUri(secret)), argThat(a -> {
				class Box {
					boolean post;
				}
				final var box = new Box();
				final var rb = mock(HttpRequest.Builder.class);
				when(rb.POST(any())).then(b -> {
					box.post = true;
					return rb;
				});
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("X-Vault-Token", token);
				if (box.post)
					verify(rb).POST(postPayload);

				return box.post;
			}), argThat(a -> {
				final var r = mock(HttpResponse.class);
				when(r.statusCode()).thenReturn(204);
				assertDoesNotThrow(() -> a.apply(r));
				return true;
			}));

			mockHttp.when(postVault).thenReturn(IuJson.object() //
					.build());

			final var vs = vault.getSecret(secret);
			assertNull(vs.get(key, String.class));
			assertNull(vs.getMetadata());
			mockHttp.verify(readVault);

			vs.set(key, value, String.class);
			verify(handler).publish(argThat(a -> {
				assertEquals(Level.CONFIG, a.getLevel());
				assertEquals("vault:set:" + endpoint + "/" + secret + ":[" + key + "]", a.getMessage());
				return true;
			}));

			mockHttp.verify(postVault);
			mockHttp.verify(readVault, times(2));
			assertEquals(value, vs.get(key, String.class));
		}
	}

	@Test
	public void testGetManaged() {
		final var key = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var secretName = IdGenerator.generateId();
		final var secret = "managed/" + secretName;
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		props.setProperty("iu.vault.secrets", secret);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class); //
				final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final Verification readVault = () -> IuHttp.send(eq(vault.dataUri(secret)), argThat(a -> {
				class Box {
					boolean post;
				}
				final var box = new Box();
				final var rb = mock(HttpRequest.Builder.class);
				when(rb.POST(any())).then(b -> {
					box.post = true;
					return rb;
				});
				assertDoesNotThrow(() -> a.accept(rb));
				verify(rb).header("X-Vault-Token", token);
				return !box.post;
			}), eq(IuHttp.READ_JSON_OBJECT));

			mockHttp.when(readVault).thenReturn( //
					IuJson.object() //
							.add("data", IuJson.object() //
									.add("data", IuJson.object() //
											.add(key, value))) //
							.build());

			final var vs = vault.getSecret(secret);
			assertEquals(value, vs.get(secretName + "/" + key, String.class));
			assertThrows(UnsupportedOperationException.class,
					() -> vs.set(secretName + "/" + key, IdGenerator.generateId(), String.class));
			assertArrayEquals(new String[] { secretName + "/" + key }, vault.list());

			mockHttp.verify(readVault, times(2));
		}
	}

	@Test
	public void testListUnsupported() {
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());
		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);

		final var vault = Vault.of(props, IuJsonAdapter::of);
		assertThrows(UnsupportedOperationException.class, vault::list);
	}

	@SuppressWarnings("unchecked")
	private void assertGetSecretThenSet(String ttl, boolean cubbyhole) {
		final var key = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var updatedValue = IdGenerator.generateId();
		final var anotherKey = IdGenerator.generateId();
		final var anotherValue = IdGenerator.generateId();
		final var version = ThreadLocalRandom.current().nextInt(10, 30);
		final var secret = IdGenerator.generateId();
		final var token = IdGenerator.generateId();
		final var endpoint = URI.create("test:/" + IdGenerator.generateId());

		final var props = new Properties();
		props.setProperty("iu.vault.endpoint", endpoint.toString());
		props.setProperty("iu.vault.token", token);
		if (ttl != null)
			props.setProperty("iu.vault.cacheTtl", ttl);
		if (cubbyhole)
			props.setProperty("iu.vault.cubbyhole", "true");

		final var vault = Vault.of(props, IuJsonAdapter::of);
		try (final var mockHttp = mockStatic(IuHttp.class); //
				final var mockBodyPublishers = mockStatic(BodyPublishers.class)) {
			final Verification readVault = () -> IuHttp.send(
					eq(URI.create(endpoint + "/" + URLEncoder.encode(secret, StandardCharsets.UTF_8))), argThat(a -> {
						class Box {
							boolean post;
						}
						final var box = new Box();
						final var rb = mock(HttpRequest.Builder.class);
						when(rb.POST(any())).then(b -> {
							box.post = true;
							return rb;
						});
						assertDoesNotThrow(() -> a.accept(rb));
						verify(rb).header("X-Vault-Token", token);
						return !box.post;
					}), eq(IuHttp.READ_JSON_OBJECT));

			if (cubbyhole)
				mockHttp.when(readVault).thenReturn( //
						IuJson.object() //
								.add("data", IuJson.object() //
										.add(key, value) //
										.add(anotherKey, anotherValue)) //
								.build(),
						IuJson.object() //
								.add("data", IuJson.object() //
										.add(key, updatedValue) //
										.add(anotherKey, anotherValue)) //
								.build());
			else
				mockHttp.when(readVault).thenReturn( //
						IuJson.object() //
								.add("data", IuJson.object() //
										.add("metadata", IuJson.object() //
												.add("version", version)) //
										.add("data", IuJson.object() //
												.add(key, value) //
												.add(anotherKey, anotherValue) //
										)) //
								.build(),
						IuJson.object() //
								.add("data", IuJson.object() //
										.add("metadata", IuJson.object() //
												.add("version", version + 1)) //
										.add("data", IuJson.object() //
												.add(key, updatedValue) //
												.add(anotherKey, anotherValue) //
										)) //
								.build());

			final var postPayloadBuilder = IuJson.object();
			if (cubbyhole)
				postPayloadBuilder //
						.add(key, updatedValue) //
						.add(anotherKey, anotherValue);
			else
				postPayloadBuilder //
						.add("options", IuJson.object() //
								.add("cas", version))
						.add("data", IuJson.object() //
								.add(key, updatedValue) //
								.add(anotherKey, anotherValue));

			final var postPayloadJson = postPayloadBuilder.build().toString();

			final var postPayload = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(postPayloadJson)).thenReturn(postPayload);

			mockHttp.when(() -> IuHttp.validate(any(), any())).thenCallRealMethod();
			mockHttp.when(() -> IuHttp.expectStatus(anyInt())).thenCallRealMethod();
			final Verification postVault = () -> IuHttp.send(
					eq(URI.create(endpoint + "/" + URLEncoder.encode(secret, StandardCharsets.UTF_8))), argThat(a -> {
						class Box {
							boolean post;
						}
						final var box = new Box();
						final var rb = mock(HttpRequest.Builder.class);
						when(rb.POST(any())).then(b -> {
							box.post = true;
							return rb;
						});
						assertDoesNotThrow(() -> a.accept(rb));
						verify(rb).header("X-Vault-Token", token);
						if (box.post)
							verify(rb).POST(postPayload);

						return box.post;
					}), cubbyhole ? argThat(a -> {
						final var r = mock(HttpResponse.class);
						when(r.statusCode()).thenReturn(204);
						assertDoesNotThrow(() -> a.apply(r));
						return true;
					}) : eq(IuHttp.READ_JSON_OBJECT));

			mockHttp.when(postVault).thenReturn(cubbyhole ? null : IuJson.object().build());

			final var vs = vault.getSecret(secret);
			assertEquals(value, vs.get(key, String.class));
			if (cubbyhole)
				assertNull(vs.getMetadata());
			else
				assertEquals(version, vs.getMetadata().getVersion());
			mockHttp.verify(readVault);

			vs.set(key, updatedValue, String.class);
			verify(handler).publish(argThat(a -> {
				assertEquals(Level.CONFIG, a.getLevel());
				assertEquals("vault:set:" + endpoint + "/" + secret + ":[" + key + "]", a.getMessage());
				return true;
			}));

			mockHttp.verify(postVault);
			mockHttp.verify(readVault, times(2));
			assertEquals(updatedValue, vs.get(key, String.class));
			if (!cubbyhole)
				assertEquals(version + 1, vs.getMetadata().getVersion());
		}
	}

	private UnsafeConsumer<HttpRequest.Builder> withToken(String token) {
		return argThat(a -> {
			final var rb = mock(HttpRequest.Builder.class);
			assertDoesNotThrow(() -> a.accept(rb));
			verify(rb).header("X-Vault-Token", token);
			return true;
		});
	}

}
