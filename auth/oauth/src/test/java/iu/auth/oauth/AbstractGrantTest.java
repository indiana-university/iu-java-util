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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.client.HttpException;

@SuppressWarnings("javadoc")
public class AbstractGrantTest extends IuOAuthTestCase {

	@SuppressWarnings("unchecked")
	@Test
	public void testNoCachePassesUnauthorized() throws HttpException {
		final var reqh = HttpHeaders.of(Map.of(), (a, b) -> true);
		final var req = mock(HttpRequest.class);
		when(req.headers()).thenReturn(reqh);

		final var resph = HttpHeaders.of(Map.of(), (a, b) -> true);
		final var resp = mock(HttpResponse.class);
		when(resp.headers()).thenReturn(resph);
		when(resp.statusCode()).thenReturn(200);
		when(resp.request()).thenReturn(req);
		when(resp.body()).thenReturn(new ByteArrayInputStream("{\"foo\":\"bar\"}".getBytes()));
		assertEquals("bar", AbstractGrant.JSON_OBJECT_NOCACHE.apply(resp).getString("foo"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNoCacheRejectsAuthorizeWithoutCacheControl() throws HttpException {
		final var reqh = HttpHeaders.of(Map.of("Authorization", List.of("foo")), (a, b) -> true);
		final var req = mock(HttpRequest.class);
		when(req.headers()).thenReturn(reqh);

		final var resph = HttpHeaders.of(Map.of(), (a, b) -> true);
		final var resp = mock(HttpResponse.class);
		when(resp.headers()).thenReturn(resph);
		when(resp.statusCode()).thenReturn(200);
		when(resp.request()).thenReturn(req);
		when(resp.body()).thenReturn(new ByteArrayInputStream("{\"foo\":\"bar\"}".getBytes()));
		assertThrows(IllegalStateException.class, () -> AbstractGrant.JSON_OBJECT_NOCACHE.apply(resp));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNoCacheRejectsAuthorizeWithoutPragma() throws HttpException {
		final var reqh = HttpHeaders.of(Map.of("Authorization", List.of("foo")), (a, b) -> true);
		final var req = mock(HttpRequest.class);
		when(req.headers()).thenReturn(reqh);

		final var resph = HttpHeaders.of(Map.of("Cache-Control", List.of("no-store")), (a, b) -> true);
		final var resp = mock(HttpResponse.class);
		when(resp.headers()).thenReturn(resph);
		when(resp.statusCode()).thenReturn(200);
		when(resp.request()).thenReturn(req);
		when(resp.body()).thenReturn(new ByteArrayInputStream("{\"foo\":\"bar\"}".getBytes()));
		assertThrows(IllegalStateException.class, () -> AbstractGrant.JSON_OBJECT_NOCACHE.apply(resp));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNoCacheAcceptsAuthorized() throws HttpException {
		final var reqh = HttpHeaders.of(Map.of("Authorization", List.of("foo")), (a, b) -> true);
		final var req = mock(HttpRequest.class);
		when(req.headers()).thenReturn(reqh);

		final var resph = HttpHeaders.of(Map.of("Cache-Control", List.of("no-store"), "Pragma", List.of("no-cache")),
				(a, b) -> true);
		final var resp = mock(HttpResponse.class);
		when(resp.headers()).thenReturn(resph);
		when(resp.statusCode()).thenReturn(200);
		when(resp.request()).thenReturn(req);
		when(resp.body()).thenReturn(new ByteArrayInputStream("{\"foo\":\"bar\"}".getBytes()));
		assertEquals("bar", AbstractGrant.JSON_OBJECT_NOCACHE.apply(resp).getString("foo"));
	}

}
