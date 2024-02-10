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
package edu.iu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class VaultPropertiesTest {

	@BeforeEach
	public void setup() throws Exception {
		final var f = VaultProperties.class.getDeclaredField("SECRETS");
		f.setAccessible(true);
		((Map<?, ?>) f.get(null)).clear();
	}
	
	@Test
	public void testEnvIsOptional() {
		assertNull(VaultProperties.envOptional(IdGenerator.generateId(), IdGenerator.generateId()));
	}

	@Test
	public void testIsConfigured() {
		try {
			System.setProperty("vault.secrets", "");
			assertFalse(VaultProperties.isConfigured());
			System.setProperty("vault.secrets", "a");
			assertTrue(VaultProperties.isConfigured());
		} finally {
			System.getProperties().remove("vault.secrets");
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetPropertiesUsingToken() throws IOException, InterruptedException, URISyntaxException {
		final var token = IdGenerator.generateId();
		System.setProperty("vault.token", token);
		System.setProperty("vault.endpoint", "test:vault.endpoint");
		System.setProperty("vault.secrets", "a/b,a/c");
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockHttpClient = mockStatic(HttpClient.class)) {

			final var mockRequest = mock(HttpRequest.class);
			final var mockHttpRequestBuilder = mock(HttpRequest.Builder.class, a -> {
				if (a.getMethod().getName().equals("build"))
					return mockRequest;
				else
					return a.getMock();
			});
			mockHttpRequest.when(() -> HttpRequest.newBuilder()).thenReturn(mockHttpRequestBuilder);

			final var client = mock(HttpClient.class);
			final var responseb = mock(HttpResponse.class);
			when(responseb.statusCode()).thenReturn(200);
			when(responseb.body())
					.thenReturn(new ByteArrayInputStream("{\"data\":{\"data\":{\"foo\":\"bar\"}}}".getBytes()));
			final var responsec = mock(HttpResponse.class);
			when(responsec.statusCode()).thenReturn(200);
			when(responsec.body())
					.thenReturn(new ByteArrayInputStream("{\"data\":{\"data\":{\"bar\":\"baz\"}}}".getBytes()));
			when(client.send(mockRequest, BodyHandlers.ofInputStream())).thenReturn(responseb, responsec);
			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(client);

			assertEquals("bar", VaultProperties.getProperty("foo"));
			assertEquals("baz", VaultProperties.getProperty("bar"));
			assertThrows(IllegalArgumentException.class, () -> VaultProperties.getProperty("baz"));

			verify(mockHttpRequestBuilder, times(2)).GET();
			verify(mockHttpRequestBuilder).uri(new URI("test:vault.endpoint/data/a/b"));
			verify(mockHttpRequestBuilder).uri(new URI("test:vault.endpoint/data/a/c"));
			verify(mockHttpRequestBuilder, times(2)).header("Authorization", "Bearer " + token);
		} finally {
			System.getProperties().remove("vault.token");
			System.getProperties().remove("vault.endpoint");
			System.getProperties().remove("vault.secrets");
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetPropertiesUsingApprole() throws IOException, InterruptedException, URISyntaxException {
		final var roleId = IdGenerator.generateId();
		final var secretId = IdGenerator.generateId();
		System.setProperty("vault.token", "");
		System.setProperty("vault.endpoint", "test:vault.endpoint");
		System.setProperty("vault.loginEndpoint", "test:vault.loginEndpoint");
		System.setProperty("vault.roleId", roleId);
		System.setProperty("vault.secretId", secretId);
		System.setProperty("vault.secrets", "a/b");
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockBodyPublishers = mockStatic(BodyPublishers.class);
				final var mockHttpClient = mockStatic(HttpClient.class)) {

			final var mockLoginRequestBody = mock(BodyPublisher.class);
			mockBodyPublishers.when(() -> BodyPublishers.ofString(argThat(a -> {
				assertEquals("{\"role_id\":\"" + roleId + "\",\"secret_id\":\"" + secretId + "\"}", a);
				return true;
			}))).thenReturn(mockLoginRequestBody);
			final var mockLoginRequest = mock(HttpRequest.class);
			final var mockLoginHttpRequestBuilder = mock(HttpRequest.Builder.class, a -> {
				if (a.getMethod().getName().equals("build"))
					return mockLoginRequest;
				else
					return a.getMock();
			});
			final var loginClient = mock(HttpClient.class);
			final var loginResponse = mock(HttpResponse.class);
			final var token = IdGenerator.generateId();
			when(loginResponse.body()).thenReturn(
					new ByteArrayInputStream(("{\"auth\":{\"client_token\":\"" + token + "\"}}").getBytes()));
			when(loginClient.send(mockLoginRequest, BodyHandlers.ofInputStream())).thenReturn(loginResponse);

			final var mockRequest = mock(HttpRequest.class);
			final var mockHttpRequestBuilder = mock(HttpRequest.Builder.class, a -> {
				if (a.getMethod().getName().equals("build"))
					return mockRequest;
				else
					return a.getMock();
			});
			final var client = mock(HttpClient.class);
			final var response = mock(HttpResponse.class);
			when(response.statusCode()).thenReturn(200);
			when(response.body()).thenReturn(
					new ByteArrayInputStream("{\"data\":{\"data\":{\"foo\":\"bar\",\"bar\":\"baz\"}}}".getBytes()));
			when(client.send(mockRequest, BodyHandlers.ofInputStream())).thenReturn(response);

			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(loginClient, client);
			mockHttpRequest.when(() -> HttpRequest.newBuilder()).thenReturn(mockHttpRequestBuilder,
					mockLoginHttpRequestBuilder);

			assertEquals("bar", VaultProperties.getProperty("foo"));
			assertEquals("baz", VaultProperties.getProperty("bar"));
			assertThrows(IllegalArgumentException.class, () -> VaultProperties.getProperty("baz"));

			verify(mockLoginHttpRequestBuilder).uri(new URI("test:vault.loginEndpoint"));
			verify(mockLoginHttpRequestBuilder).POST(mockLoginRequestBody);
			verify(mockLoginHttpRequestBuilder).header("Content-Type", "application/json; charset=utf-8");

			verify(mockHttpRequestBuilder).GET();
			verify(mockHttpRequestBuilder).uri(new URI("test:vault.endpoint/data/a/b"));
			verify(mockHttpRequestBuilder).header("Authorization", "Bearer " + token);
		} finally {
			System.getProperties().remove("vault.endpoint");
			System.getProperties().remove("vault.loginEndpoint");
			System.getProperties().remove("vault.roleId");
			System.getProperties().remove("vault.secretId");
			System.getProperties().remove("vault.secrets");
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetPropertiesFailure() throws IOException, InterruptedException, URISyntaxException {
		final var token = IdGenerator.generateId();
		System.setProperty("vault.token", token);
		System.setProperty("vault.endpoint", "test:vault.endpoint");
		System.setProperty("vault.secrets", "a/b");
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockHttpClient = mockStatic(HttpClient.class)) {

			final var mockRequest = mock(HttpRequest.class);
			final var mockHttpRequestBuilder = mock(HttpRequest.Builder.class, a -> {
				if (a.getMethod().getName().equals("build"))
					return mockRequest;
				else
					return a.getMock();
			});
			mockHttpRequest.when(() -> HttpRequest.newBuilder()).thenReturn(mockHttpRequestBuilder);

			final var client = mock(HttpClient.class);
			final var response = mock(HttpResponse.class);
			when(response.statusCode()).thenReturn(401);
			when(response.body()).thenReturn(new ByteArrayInputStream("{\"error\":\"unauthorized\"}".getBytes()));
			when(client.send(mockRequest, BodyHandlers.ofInputStream())).thenReturn(response);
			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(client);

			assertEquals(
					"Unexpected response from Vault; status=401; request=" + mockRequest
							+ "; body {\"error\":\"unauthorized\"}",
					assertThrows(IllegalStateException.class, () -> VaultProperties.getProperty("baz")).getMessage());

			verify(mockHttpRequestBuilder).GET();
			verify(mockHttpRequestBuilder).uri(new URI("test:vault.endpoint/data/a/b"));
			verify(mockHttpRequestBuilder).header("Authorization", "Bearer " + token);
		} finally {
			System.getProperties().remove("vault.token");
			System.getProperties().remove("vault.endpoint");
			System.getProperties().remove("vault.secrets");
		}
	}

}
