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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuStream;
import jakarta.json.Json;
import jakarta.json.JsonValue;

/**
 * Provides minimal utilities for internal HTTP-based interactions specific to
 * authorization server interactions.
 */
public class HttpUtils {

	private static final Logger LOG = Logger.getLogger(HttpUtils.class.getName());

	/**
	 * Reads a JSON value from a public URI.
	 * 
	 * @param uri {@link URI}
	 * @return {@link JsonValue}
	 */
	public static JsonValue read(URI uri) {
		return read(HttpRequest.newBuilder(uri).build());
	}

	/**
	 * Reads a JSON value from an HTTP request.
	 * 
	 * @param request {@link HttpRequest}
	 * @return {@link JsonValue}
	 */
	public static JsonValue read(HttpRequest request) {
		final var uri = request.uri();
		final var scheme = uri.getScheme();

		if (!"https".equals(scheme) //
				&& !"localhost".equals(uri.getHost()))
			throw new IllegalArgumentException("insecure URI");

		return IuException.unchecked(() -> Json
				.createReader(
						new StringReader(read(HttpClient.newHttpClient().send(request, BodyHandlers.ofInputStream()))))
				.readValue());
	}

	/**
	 * Creates an authentication challenge sending to a client via the
	 * <strong>WWW-Authenticate</strong> header.
	 * 
	 * @param scheme     authentication scheme to request
	 * @param attributes challenge attributes for informing the client of how to
	 *                   authenticate
	 * @return authentication challenge
	 */
	public static String createChallenge(String scheme, Map<String, String> attributes) {
		final var sb = new StringBuilder();
		sb.append(scheme);
		if (attributes != null && !attributes.isEmpty()) {
			sb.append(' ');
			var first = true;
			for (final var attributeEntry : attributes.entrySet()) {
				if (first)
					first = false;
				else
					sb.append(" ");
				sb.append(attributeEntry.getKey()).append("=\"")
						.append(attributeEntry.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
			}
		}
		return sb.toString();
	}

	/**
	 * Reads UTF-8 string content from an {@link HttpResponse} backed by
	 * {@link InputStream}.
	 * 
	 * @param response HTTP response
	 * @return UTF-8 string content
	 */
	public static String read(HttpResponse<InputStream> response) {
		final var status = response.statusCode();
		final var headers = response.headers();
		if (response.request().headers().firstValue("Authorization").isPresent() //
				&& !response.headers().firstValue("Cache-Control").get().equals("no-store"))
			throw new IllegalStateException("Must include Cache-Control = no-store response header");

		String content = null;
		Throwable error = null;
		try (final var in = response.body(); //
				final var r = new InputStreamReader(in, "UTF-8")) {
			content = IuStream.read(r);
		} catch (Throwable e) {
			error = e;
		}

		if (status != 200 || error != null)
			throw new IllegalStateException("Failed to read from " + response.uri() + "; status=" + status + " headers="
					+ headers.map() + " content=" + content, error);

		LOG.fine(() -> "Read from " + response.uri() + "; status=" + status + " headers=" + headers.map());

		return content;
	}

	private HttpUtils() {
	}

}
