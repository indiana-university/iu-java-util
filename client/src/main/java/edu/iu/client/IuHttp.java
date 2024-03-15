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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.IuWebUtils;

/**
 * Provides common base-level whitelisting, logging, and exception handling
 * utilities for {@link HttpRequest} and {@link HttpResponse}.
 * 
 * <p>
 * All requests are handling via a cached {@link HttpClient} instance configured
 * with {@link HttpClient#newHttpClient default settings}.
 * </p>
 */
public class IuHttp {

	private static final Logger LOG = Logger.getLogger(IuHttp.class.getName());

	private static final Collection<URI> ALLOWED_URI;
	private static final HttpClient HTTP = HttpClient.newHttpClient();

	static {
		final var module = IuHttp.class.getModule();
		if (module.isOpen(IuHttp.class.getPackageName()))
			throw new IllegalStateException("Must be in a named module and not open");

		final Queue<URI> allowedUri = new ArrayDeque<>();
		for (final var uri : IuRuntimeEnvironment.env("iu-client.allowedUri").split(","))
			allowedUri.add(URI.create(uri));

		ALLOWED_URI = Collections.unmodifiableCollection(allowedUri);
	}

	/**
	 * Sends an HTTP GET request to a public URI.
	 * 
	 * @param uri public URI
	 * 
	 * @return {@link HttpResponse}
	 * @throws HttpException If the response has error status code.
	 */
	public static HttpResponse<InputStream> get(URI uri) throws HttpException {
		return send(uri, null);
	}

	/**
	 * Sends an HTTP request.
	 * 
	 * @param uri             request URI
	 * @param requestConsumer receives the {@link HttpRequest.Builder} before
	 *                        sending to the server.
	 * 
	 * @return {@link HttpResponse}
	 * @throws HttpException If the response has error status code.
	 */
	public static HttpResponse<InputStream> send(URI uri, Consumer<HttpRequest.Builder> requestConsumer)
			throws HttpException {
		if (!ALLOWED_URI.stream().anyMatch(allowedUri -> IuWebUtils.isRootOf(allowedUri, uri)))
			throw new IllegalArgumentException();

		return IuException.unchecked(() -> {
			final var requestBuilder = HttpRequest.newBuilder(uri);
			if (requestConsumer != null)
				requestConsumer.accept(requestBuilder);
			final var request = requestBuilder.build();

			final var sb = new StringBuilder();
			sb.append(request.method());
			sb.append(' ').append(request.uri());
			final var requestHeaders = request.headers();
			if (requestHeaders != null) {
				final var map = requestHeaders.map();
				if (!map.isEmpty())
					sb.append(' ').append(map.keySet());
			}

			final HttpResponse<InputStream> response;
			try {
				response = HTTP.send(request, BodyHandlers.ofInputStream());
			} catch (Throwable e) {
				final var m = "HTTP connection failed " + sb;
				LOG.log(Level.INFO, e, () -> m);
				throw new IllegalStateException(m, e);
			}

			final var status = response.statusCode();
			sb.append(" ").append(IuWebUtils.describeStatus(status));

			final var responseHeaders = response.headers();
			if (responseHeaders != null)
				sb.append(' ').append(responseHeaders);

			if (response.statusCode() >= 400)
				throw new HttpException(response, sb.toString());
			else
				LOG.fine(sb::toString);

			return response;
		});
	}

	private IuHttp() {
	}

}
