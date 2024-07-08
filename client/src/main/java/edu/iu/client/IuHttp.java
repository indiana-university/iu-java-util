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
import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.IuWebUtils;
import edu.iu.UnsafeConsumer;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Provides common base-level whitelisting, logging, and exception handling
 * utilities for {@link HttpRequest} and {@link HttpResponse}.
 * 
 * <p>
 * All requests are handled via a cached {@link HttpClient} instance configured
 * with {@link HttpClient#newHttpClient default settings}.
 * </p>
 */
public class IuHttp {

	private static final Logger LOG = Logger.getLogger(IuHttp.class.getName());

	static {
		IuObject.assertNotOpen(IuHttp.class);
	}

	private static final Collection<URI> ALLOWED_URI = IuRuntimeEnvironment.env("iu.http.allowedUri",
			a -> Stream.of(a.split(",")).map(URI::create).collect(Collectors.toUnmodifiableList()));

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	/**
	 * Validates a 200 OK response.
	 */
	public static final HttpResponseValidator OK = expectStatus(200);

	/**
	 * Validates a 204 NO CONTENT response and returns null.
	 */
	public static final HttpResponseHandler<?> NO_CONTENT = validate(a -> null, IuHttp.expectStatus(204));

	/**
	 * Validates 200 OK then parses the response as a JSON object.
	 */
	public static final HttpResponseHandler<JsonValue> READ_JSON = validate(IuJson::parse, IuHttp.OK);

	/**
	 * Validates 200 OK then parses the response as a JSON object.
	 */
	public static final HttpResponseHandler<JsonObject> READ_JSON_OBJECT = validate(a -> IuJson.parse(a).asJsonObject(),
			IuHttp.OK);

	/**
	 * Creates an HTTP response handler.
	 * 
	 * @param <T>                value type
	 * @param bodyDeserializer   function that deserializes the response body
	 * @param responseValidators one or more verification checks to apply to the
	 *                           response before passing to the handler
	 * @return decorated response handler
	 */
	public static <T> HttpResponseHandler<T> validate(Function<InputStream, T> bodyDeserializer,
			HttpResponseValidator... responseValidators) {
		return response -> {
			for (final var responseValidator : responseValidators)
				responseValidator.accept(response);
			return bodyDeserializer.apply(response.body());
		};
	}

	/**
	 * Gets a {@link HttpResponseValidator} that verifies an expected status code.
	 * 
	 * @param expectedStatusCode status code
	 * @return {@link HttpResponseValidator}
	 */
	public static HttpResponseValidator expectStatus(int expectedStatusCode) {
		return response -> {
			final var statusCode = response.statusCode();
			if (statusCode != expectedStatusCode)
				throw new HttpException(response, "Expected " + IuWebUtils.describeStatus(expectedStatusCode)
						+ ", found " + IuWebUtils.describeStatus(statusCode));
		};
	}

	/**
	 * Gets a {@link HttpResponseValidator} that tests response headers.
	 * 
	 * @param headerValidator test response headers
	 * @return {@link HttpResponseValidator}
	 */
	public static HttpResponseValidator checkHeaders(BiPredicate<String, String> headerValidator) {
		return response -> {
			for (final var headerEntry : response.headers().map().entrySet()) {
				final var name = headerEntry.getKey();
				for (final var value : headerEntry.getValue())
					if (!headerValidator.test(name, value))
						throw new HttpException(response, "Invalid header " + name);
			}
		};
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
	 * Sends an HTTP GET request to a public URI.
	 * 
	 * @param <T>             response type
	 * 
	 * @param uri             public URI
	 * @param responseHandler function that converts HTTP response data to the
	 *                        response type.
	 * 
	 * @return response value
	 * @throws HttpException If the response has error status code.
	 */
	public static <T> T get(URI uri, HttpResponseHandler<T> responseHandler) throws HttpException {
		return responseHandler.apply(send(uri, null));
	}

	/**
	 * Sends a synchronous HTTP request.
	 * 
	 * @param uri             request URI
	 * @param requestConsumer receives the {@link HttpRequest.Builder} before
	 *                        sending to the server.
	 * 
	 * @return {@link HttpResponse}
	 * @throws HttpException If the response has error status code.
	 */
	public static HttpResponse<InputStream> send(URI uri, UnsafeConsumer<HttpRequest.Builder> requestConsumer)
			throws HttpException {
		return send(HttpException.class, uri, requestConsumer);
	}

	/**
	 * Sends a synchronous HTTP request.
	 * 
	 * @param <E>             additional exception type
	 * 
	 * @param uri             request URI
	 * @param requestConsumer receives the {@link HttpRequest.Builder} before
	 *                        sending to the server.
	 * @param exceptionClass  additional checked exception type to allow thrown from
	 *                        requestConsumer
	 * 
	 * @return {@link HttpResponse}
	 * @throws HttpException If the response has error status code.
	 * @throws E             from requestConsumer
	 */
	public static <E extends Exception> HttpResponse<InputStream> send(Class<E> exceptionClass, URI uri,
			UnsafeConsumer<HttpRequest.Builder> requestConsumer) throws HttpException, E {
		if (!"https".equals(uri.getScheme()) //
				&& !"localhost".equals(uri.getHost()))
			throw new IllegalArgumentException("insecure URI");

		if (!ALLOWED_URI.stream().anyMatch(allowedUri -> IuWebUtils.isRootOf(allowedUri, uri)))
			throw new IllegalArgumentException("URI not allowed, must be relative to " + ALLOWED_URI);

		return IuException.checked(HttpException.class, exceptionClass, () -> {
			final var requestBuilder = HttpRequest.newBuilder(uri);
			if (requestConsumer != null)
				requestConsumer.accept(requestBuilder);
			final var request = requestBuilder.build();

			final var sb = new StringBuilder();
			sb.append(request.method());
			sb.append(' ').append(request.uri());
			final var requestHeaders = request.headers();
			final var requestHeaderMap = requestHeaders.map();
			if (!requestHeaderMap.isEmpty())
				// TODO: apply security filter
				sb.append(' ').append(requestHeaderMap.keySet());

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
			final var responseHeaderMap = responseHeaders.map();
			if (!responseHeaderMap.isEmpty())
				// TODO: apply security filter
				sb.append(' ').append(responseHeaderMap.keySet());

			if (response.statusCode() >= 400) {
				final var m = sb.toString();
				final var e = new HttpException(response, m);
				LOG.log(Level.INFO, m, e);
				throw e;
			} else
				LOG.fine(sb::toString);

			return response;
		});
	}

	/**
	 * Sends a synchronous HTTP request expecting 200 OK and accepting all response
	 * headers.
	 * 
	 * @param <T>             response type
	 * 
	 * @param uri             request URI
	 * @param requestConsumer receives the {@link HttpRequest.Builder} before
	 *                        sending to the server.
	 * @param responseHandler function that converts HTTP response data to the
	 *                        response type.
	 * 
	 * @return response value
	 * @throws HttpException If the response has error status code.
	 */
	public static <T> T send(URI uri, UnsafeConsumer<HttpRequest.Builder> requestConsumer,
			HttpResponseHandler<T> responseHandler) throws HttpException {
		return send(HttpException.class, uri, requestConsumer, responseHandler);
	}

	/**
	 * Sends a synchronous HTTP request expecting 200 OK and accepting all response
	 * headers.
	 * 
	 * @param <T>             response type
	 * @param <E>             additional exception type
	 * 
	 * @param uri             request URI
	 * @param requestConsumer receives the {@link HttpRequest.Builder} before
	 *                        sending to the server.
	 * @param exceptionClass  additional checked exception class to allow from
	 *                        requestConsumer
	 * @param responseHandler function that converts HTTP response data to the
	 *                        response type.
	 * 
	 * @return response value
	 * @throws HttpException If the response has error status code.
	 * @throws E             from requestConsumer
	 */
	public static <T, E extends Exception> T send(Class<E> exceptionClass, URI uri,
			UnsafeConsumer<HttpRequest.Builder> requestConsumer, HttpResponseHandler<T> responseHandler)
			throws HttpException, E {
		return responseHandler.apply(send(exceptionClass, uri, requestConsumer));
	}

	private IuHttp() {
	}
}
