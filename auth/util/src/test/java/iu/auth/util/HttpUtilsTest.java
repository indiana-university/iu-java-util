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
package iu.auth.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IuStream;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class HttpUtilsTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testRead() throws IOException, InterruptedException {
		final var uri = mock(URI.class);
		when(uri.getScheme()).thenReturn("https");
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockHttpClient = mockStatic(HttpClient.class)) {
			final var requestBuilder = mock(HttpRequest.Builder.class);
			final var reqHeaders = mock(HttpHeaders.class);
			final var request = mock(HttpRequest.class);
			when(request.headers()).thenReturn(reqHeaders);
			when(request.uri()).thenReturn(uri);
			when(requestBuilder.build()).thenReturn(request);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(uri)).thenReturn(requestBuilder);

			final var response = mock(HttpResponse.class);
			final var headers = mock(HttpHeaders.class);
			when(response.request()).thenReturn(request);
			when(response.statusCode()).thenReturn(200);
			when(response.headers()).thenReturn(headers);
			when(response.body()).thenReturn(new ByteArrayInputStream("{\"foo\":\"bar\"}".getBytes()));

			final var client = mock(HttpClient.class);
			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(client);
			when(client.send(request, BodyHandlers.ofInputStream())).thenReturn(response);

			IuTestLogger.expect("iu.auth.util.HttpUtils", Level.FINE, "Read from null; status=200 headers=\\{\\}");
			assertEquals(Json.createObjectBuilder().add("foo", "bar").build(), HttpUtils.read(uri));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReadError() throws IOException, InterruptedException {
		final var uri = mock(URI.class);
		when(uri.getScheme()).thenReturn("https");
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockHttpClient = mockStatic(HttpClient.class);
				final var mockIuStream = mockStatic(IuStream.class)) {
			final var requestBuilder = mock(HttpRequest.Builder.class);
			final var reqHeaders = mock(HttpHeaders.class);
			final var request = mock(HttpRequest.class);
			when(request.headers()).thenReturn(reqHeaders);
			when(request.uri()).thenReturn(uri);
			when(requestBuilder.build()).thenReturn(request);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(uri)).thenReturn(requestBuilder);

			final var response = mock(HttpResponse.class);
			final var headers = mock(HttpHeaders.class);
			final var in = mock(InputStream.class);
			when(response.request()).thenReturn(request);
			mockIuStream.when(() -> IuStream.read(any(Reader.class))).thenThrow(IOException.class);
			when(response.statusCode()).thenReturn(200);
			when(response.headers()).thenReturn(headers);
			when(response.body()).thenReturn(in);

			final var client = mock(HttpClient.class);
			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(client);
			when(client.send(request, BodyHandlers.ofInputStream())).thenReturn(response);

			final var e = assertThrows(IllegalStateException.class, () -> HttpUtils.read(uri));
			assertInstanceOf(IOException.class, e.getCause());
			assertEquals("Failed to read from null; status=200 headers={} content=null", e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReadErrorStatus() throws IOException, InterruptedException {
		final var uri = mock(URI.class);
		when(uri.getScheme()).thenReturn("https");
		try (final var mockHttpRequest = mockStatic(HttpRequest.class);
				final var mockHttpClient = mockStatic(HttpClient.class)) {
			final var requestBuilder = mock(HttpRequest.Builder.class);
			final var reqHeaders = mock(HttpHeaders.class);
			final var request = mock(HttpRequest.class);
			when(request.headers()).thenReturn(reqHeaders);
			when(request.uri()).thenReturn(uri);
			when(requestBuilder.build()).thenReturn(request);
			mockHttpRequest.when(() -> HttpRequest.newBuilder(uri)).thenReturn(requestBuilder);

			final var response = mock(HttpResponse.class);
			final var headers = mock(HttpHeaders.class);
			when(response.statusCode()).thenReturn(401);
			when(response.headers()).thenReturn(headers);
			when(response.request()).thenReturn(request);
			when(response.body()).thenReturn(new ByteArrayInputStream("{\"foo\":\"bar\"}".getBytes()));

			final var client = mock(HttpClient.class);
			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(client);
			when(client.send(request, BodyHandlers.ofInputStream())).thenReturn(response);

			final var e = assertThrows(IllegalStateException.class, () -> HttpUtils.read(uri));
			assertEquals("Failed to read from null; status=401 headers={} content={\"foo\":\"bar\"}", e.getMessage());
		}
	}

	@Test
	public void testChallenge() {
		assertEquals("Bearer", HttpUtils.createChallenge("Bearer", null));
		assertEquals("Bearer", HttpUtils.createChallenge("Bearer", Map.of()));
		assertEquals("Bearer realm=\"example\"", HttpUtils.createChallenge("Bearer", Map.of("realm", "example")));
		final Map<String, String> a = new LinkedHashMap<>();
		a.put("realm", "example");
		a.put("foo", "bar \"baz\"");
		assertEquals("Bearer realm=\"example\" foo=\"bar \\\"baz\\\"\"", HttpUtils.createChallenge("Bearer", a));
	}

	@Test
	public void testInsecureUri() {
		assertThrows(IllegalArgumentException.class,
				() -> HttpUtils.read(HttpRequest.newBuilder(new URI("http://foo.bar")).build()));
		assertThrows(IllegalArgumentException.class,
				() -> HttpUtils.read(HttpRequest.newBuilder(new URI("foo:bar")).build()));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAllowsLocalhost() throws URISyntaxException, IOException, InterruptedException {
		try (final var mockHttpUtils = mockStatic(HttpUtils.class);
				final var mockHttpClient = mockStatic(HttpClient.class)) {
			final var mockClient = mock(HttpClient.class);
			final var response = mock(HttpResponse.class);
			when(mockClient.send(argThat((HttpRequest r) -> {
				when(response.request()).thenReturn(r);
				return true;
			}), any())).thenReturn(response);

			mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(mockClient);
			mockHttpUtils.when(() -> HttpUtils.read(any(HttpRequest.class))).thenCallRealMethod();
			final var u = new URI("http://localhost/");
			mockHttpUtils
					.when(() -> HttpUtils.read(argThat((HttpResponse<InputStream> a) -> a.request().uri().equals(u))))
					.thenReturn("{}");
			HttpUtils.read(HttpRequest.newBuilder(u).build());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testResponseMustIncludeCacheIfAuthorized() {
		final var request = mock(HttpRequest.class);
		final var reqHeaders = mock(HttpHeaders.class);
		when(reqHeaders.firstValue("Authorization")).thenReturn(Optional.of("foo"));
		when(request.headers()).thenReturn(reqHeaders);
		final var response = mock(HttpResponse.class);
		final var headers = mock(HttpHeaders.class);
		when(headers.map()).thenReturn(Map.of());
		when(response.headers()).thenReturn(headers);
		when(response.request()).thenReturn(request);
		assertThrows(NoSuchElementException.class, () -> HttpUtils.read(response));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testResponseMustNotCacheIfAuthorized() {
		final var request = mock(HttpRequest.class);
		final var reqHeaders = mock(HttpHeaders.class);
		when(reqHeaders.firstValue("Authorization")).thenReturn(Optional.of("foo"));
		when(request.headers()).thenReturn(reqHeaders);
		final var response = mock(HttpResponse.class);
		final var headers = mock(HttpHeaders.class);
		when(headers.firstValue("Cache-Control")).thenReturn(Optional.of("bar"));
		when(response.headers()).thenReturn(headers);
		when(response.request()).thenReturn(request);
		assertThrows(IllegalStateException.class, () -> HttpUtils.read(response));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReadsNoCacheAuthorizedResponse() {
		IuTestLogger.allow("iu.auth.util.HttpUtils", Level.FINE);
		final var request = mock(HttpRequest.class);
		final var reqHeaders = mock(HttpHeaders.class);
		when(reqHeaders.firstValue("Authorization")).thenReturn(Optional.of("foo"));
		when(request.headers()).thenReturn(reqHeaders);
		final var response = mock(HttpResponse.class);
		final var headers = mock(HttpHeaders.class);
		when(headers.firstValue("Cache-Control")).thenReturn(Optional.of("no-store"));
		when(response.headers()).thenReturn(headers);
		when(response.request()).thenReturn(request);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(new ByteArrayInputStream("bar".getBytes()));
		assertEquals("bar", HttpUtils.read(response));
	}

}
