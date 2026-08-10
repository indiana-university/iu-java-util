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
package edu.iu.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuListener;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.UnsafeConsumer;
import iu.client.IuHttpClientEvent;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Provides common base-level whitelisting, logging, and exception handling
 * utilities for {@link HttpRequest} and {@link HttpResponse}.
 * 
 * <p>
 * All requests are handled via a cached {@link HttpClient} instance configured
 * with default settings. {@code iu.http.proxy} and {@code iu.https.proxy} may
 * specify proxy server URLs, including port, for HTTP and HTTPS requests,
 * respectively. If {@code iu.http.no.proxy} is set, requests whose domain is
 * equal to or a subdomain of an entry in its comma-separated value bypass the
 * proxy.
 * </p>
 */
public class IuHttp {

	private static final Logger LOG = Logger.getLogger(IuHttp.class.getName());

	static {
		IuObject.assertNotOpen(IuHttp.class);
	}

	private static final Collection<URI> ALLOWED_URI = IuRuntimeEnvironment.env("iu.http.allowedUri",
			a -> Stream.of(a.split(",")).map(URI::create).collect(Collectors.toUnmodifiableList()));

	private static final Collection<URI> ALLOWED_INSECURE_URI = IuRuntimeEnvironment.envOptional(
			"iu.http.allowedInsecureUri",
			a -> Stream.of(a.split(",")).map(URI::create).collect(Collectors.toUnmodifiableList()));

	private static final Collection<String> NO_PROXY = IuRuntimeEnvironment.envOptional("iu.http.no.proxy",
			a -> Stream.of(a.split(",")).map(String::strip).map(s -> s.toLowerCase(Locale.ROOT))
					.collect(Collectors.toUnmodifiableList()));

	private static final HttpClient HTTP = buildHttpClient(HttpClient.newBuilder(),
			IuRuntimeEnvironment.envOptional("iu.http.proxy", URI::create),
			IuRuntimeEnvironment.envOptional("iu.https.proxy", URI::create), NO_PROXY);

	/**
	 * Builds the shared HTTP client, optionally configuring scheme-specific proxies
	 * with domain exclusions.
	 *
	 * @param builder       HTTP client builder
	 * @param httpProxyUrl  HTTP proxy server URL
	 * @param httpsProxyUrl HTTPS proxy server URL
	 * @param noProxy       domains that bypass both proxies
	 * @return configured HTTP client
	 */
	static HttpClient buildHttpClient(HttpClient.Builder builder, URI httpProxyUrl, URI httpsProxyUrl,
			Collection<String> noProxy) {
		final Map<String, Proxy> proxies = new HashMap<>();
		if (httpProxyUrl != null)
			proxies.put("http", proxy(httpProxyUrl));
		if (httpsProxyUrl != null)
			proxies.put("https", proxy(httpsProxyUrl));

		if (!proxies.isEmpty())
			builder.proxy(new ProxySelector() {
				@Override
				public List<Proxy> select(URI uri) {
					final var scheme = uri.getScheme();
					final var proxy = proxies.get(scheme == null ? null : scheme.toLowerCase(Locale.ROOT));
					return List.of(proxy == null || isNoProxy(noProxy, uri) ? Proxy.NO_PROXY : proxy);
				}

				@Override
				public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
				}
			});
		return builder.build();
	}

	private static Proxy proxy(URI proxyUrl) {
		if (!proxyUrl.isAbsolute() || proxyUrl.getHost() == null || proxyUrl.getPort() < 0)
			throw new IllegalArgumentException("Proxy URL must include scheme, host, and port: " + proxyUrl);
		return new Proxy(Proxy.Type.HTTP,
				InetSocketAddress.createUnresolved(proxyUrl.getHost(), proxyUrl.getPort()));
	}
	
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
	 * Validates 200 OK then returns the response as UTF-8 text.
	 */
	public static final HttpResponseHandler<String> READ_UTF8 = validate(
			a -> IuText.utf8(IuException.unchecked(() -> IuStream.read(a))), IuHttp.OK);

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
	 * @throws HttpException If the request could not be completed: either the
	 *                      connection failed, leaving
	 *                      {@link HttpException#getResponse()} null, or the response
	 *                      has an error status code.
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
	 * @throws HttpException If the request could not be completed: either the
	 *                      connection failed, leaving
	 *                      {@link HttpException#getResponse()} null, or the response
	 *                      has an error status code.
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
	 * @throws HttpException If the request could not be completed: either the
	 *                      connection failed, leaving
	 *                      {@link HttpException#getResponse()} null, or the response
	 *                      has an error status code.
	 */
	public static HttpResponse<InputStream> send(URI uri, UnsafeConsumer<HttpRequest.Builder> requestConsumer)
			throws HttpException {
		return send(HttpException.class, uri, requestConsumer);
	}

	/**
	 * Determines whether or not an allow list contains a root of a URI.
	 * 
	 * @param allowList allowed root URIs
	 * @param uri       {@link URI} to check
	 * @return true if the allow list is non-null and contains at least one entry
	 *         that is a root of the supplied URI
	 */
	static boolean isAllowed(Collection<URI> allowList, URI uri) {
		if (allowList == null)
			return false;
		else
			return allowList.stream().anyMatch(allowedUri -> IuWebUtils.isRootOf(allowedUri, uri));
	}

	/**
	 * Determines whether a URI's host matches a domain exclusion.
	 *
	 * @param noProxy excluded domains
	 * @param uri     URI to check
	 * @return true if the URI host equals or is a subdomain of an excluded domain
	 */
	static boolean isNoProxy(Collection<String> noProxy, URI uri) {
		if (noProxy == null)
			return false;
		final var host = uri.getHost();
		if (host == null)
			return false;
		final var normalizedHost = host.toLowerCase(Locale.ROOT);
		return noProxy.stream()
				.anyMatch(domain -> normalizedHost.equals(domain) || normalizedHost.endsWith('.' + domain));
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
	 * @throws HttpException If the request could not be completed: either the
	 *                      connection failed, leaving
	 *                      {@link HttpException#getResponse()} null, or the response
	 *                      has an error status code.
	 * @throws E             from requestConsumer
	 */
	public static <E extends Exception> HttpResponse<InputStream> send(Class<E> exceptionClass, URI uri,
			UnsafeConsumer<HttpRequest.Builder> requestConsumer) throws HttpException, E {
		if (!"https".equals(uri.getScheme())) {
			if (!isAllowed(ALLOWED_INSECURE_URI, uri))
				throw new IllegalArgumentException(
						"Insecure URI not allowed, must be relative to " + ALLOWED_INSECURE_URI);
			else
				LOG.info(() -> "Allowing insecure URI " + uri);
		} else if (!isAllowed(ALLOWED_URI, uri))
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
				sb.append(' ').append(requestHeaderMap.keySet());

			final var event = new IuHttpClientEvent(request.uri());
			IuListener.observe(event);
			
			final HttpResponse<InputStream> response;
			try {
				response = HTTP.send(request, BodyHandlers.ofInputStream());
			} catch (Throwable e) {
				final var m = "HTTP connection failed " + sb;
				LOG.log(Level.INFO, e, () -> m);

				IuListener.observe(event.received(0));

				if (e instanceof InterruptedException)
					Thread.currentThread().interrupt();

				throw new HttpException(m, e);
			}

			final var status = response.statusCode();
			IuListener.observe(event.received(status));
			
			sb.append(" ").append(IuWebUtils.describeStatus(status));

			final var responseHeaders = response.headers();
			final var responseHeaderMap = responseHeaders.map();
			if (!responseHeaderMap.isEmpty())
				sb.append(' ').append(responseHeaderMap.keySet());

			if (status >= 400) {
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
	 * @throws HttpException If the request could not be completed: either the
	 *                      connection failed, leaving
	 *                      {@link HttpException#getResponse()} null, or the response
	 *                      has an error status code.
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
	 * @throws HttpException If the request could not be completed: either the
	 *                      connection failed, leaving
	 *                      {@link HttpException#getResponse()} null, or the response
	 *                      has an error status code.
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
