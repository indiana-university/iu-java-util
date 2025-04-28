/*
 * Copyright © 2025 Indiana University
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
package iu.web.server;

import java.net.URI;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;

import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuException;
import edu.iu.IuNotFoundException;
import edu.iu.IuOutOfServiceException;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.web.IuWebContext;

/**
 * Utility methods for web server implementation components.
 */
class WebServerUtils {

	private static final Logger LOG = Logger.getLogger(WebServerUtils.class.getName());

	private WebServerUtils() {
	}

	/**
	 * Describes the request attributes of a {@link HttpExchange}.
	 * 
	 * @param exchange {@link HttpExchange}
	 * @return description
	 */
	static String describeRequest(HttpExchange exchange) {
		StringBuilder sb = new StringBuilder("HTTP request");
		sb.append("\n  Protocol: ");
		sb.append(exchange.getProtocol());
		sb.append("\n  Method: ");
		sb.append(exchange.getRequestMethod());

		sb.append("\n  Remote Address: ");
		sb.append(exchange.getRemoteAddress());
		sb.append("\n  Local Address: ");
		sb.append(exchange.getLocalAddress());

		final var context = exchange.getHttpContext();
		sb.append("\n  Context Path: ");
		sb.append(context.getPath());
		sb.append("\n  Request URI: ");
		sb.append(exchange.getRequestURI());

		final var headers = exchange.getRequestHeaders();
		sb.append("\n  Headers:");
		for (final var headerEntry : headers.entrySet())
			for (final var headerValue : headerEntry.getValue()) {
				sb.append("\n    ");
				sb.append(headerEntry.getKey());
				sb.append(" = ");
				sb.append(headerValue);
			}

		return sb.toString();
	}

	/**
	 * Replaces the authority section in the request URI with external-facing host
	 * address of an upstream proxy.
	 * 
	 * @param originalUri Original request URI
	 * @param host        replacement URI host authority
	 * @return modified {@link URI}
	 */
	static URI replaceAuthority(URI originalUri, String host) {
		if (host == null)
			return originalUri;

		final var newUriBuilder = new StringBuilder(originalUri.toString());

		final var startOfAuthority = newUriBuilder.indexOf("//") + 2;
		final var endOfAuthority = newUriBuilder.indexOf("/", startOfAuthority);
		if (endOfAuthority > startOfAuthority)
			newUriBuilder.delete(startOfAuthority, endOfAuthority);
		else
			newUriBuilder.setLength(startOfAuthority);

		newUriBuilder.insert(startOfAuthority, host);

		final var requestUri = URI.create(newUriBuilder.toString());
		return requestUri;
	}

	/**
	 * Describes the response attributes of a {@link HttpExchange}.
	 * 
	 * @param exchange {@link HttpExchange}
	 * @return description
	 */
	static String describeResponse(HttpExchange exchange) {
		StringBuilder sb = new StringBuilder("HTTP ");
		sb.append(IuWebUtils.describeStatus(exchange.getResponseCode()));
		final var headers = exchange.getResponseHeaders();
		sb.append("\n  Headers:");
		for (final var headerEntry : headers.entrySet())
			for (final var headerValue : headerEntry.getValue()) {
				sb.append("\n    ");
				sb.append(headerEntry.getKey());
				sb.append(" = ");
				sb.append(headerValue);
			}
		return sb.toString();
	}

	/**
	 * Gets the HTTP status code associated with the {@link Throwable} class.
	 * 
	 * @param error
	 * @return HTTP status code
	 * @see #handleError(String, String, Throwable, HttpExchange, IuWebContext)
	 */
	static int getStatus(Throwable error) {
		if (error instanceof IuBadRequestException)
			return 400;
		else if (error instanceof IuAuthorizationFailedException)
			return 403;
		else if (error instanceof IuNotFoundException)
			return 404;
		else if (error instanceof IuOutOfServiceException)
			return 503;
		else
			return 500;
	}

	/**
	 * Gets the appropriate log level for reporting an HTTP response.
	 * 
	 * @param status HTTP status code
	 * @return {@link Level}
	 * @see #handleError(String, String, Throwable, HttpExchange, IuWebContext)
	 */
	static Level getLevel(int status) {
		if (status < 400)
			return Level.FINE;
		else if (status == 403 //
				|| status == 404)
			return Level.INFO;
		else if (status < 500)
			return Level.WARNING;
		else if (status == 503)
			return Level.CONFIG;
		else
			return Level.SEVERE;
	}

	/**
	 * Handles an error by logging request and response details at the appropriate
	 * level, then returning a basic status message via JSON object.
	 * 
	 * <table>
	 * <tr>
	 * <th>{@link IuBadRequestException}</th>
	 * <td>{@link Level#WARNING}</td>
	 * <td>400 BAD REQUEST</td>
	 * </tr>
	 * <tr>
	 * <th>{@link IuAuthorizationFailedException}</th>
	 * <td>{@link Level#INFO}</td>
	 * <td>403 FORBIDDEN</td>
	 * </tr>
	 * <tr>
	 * <th>{@link IuNotFoundException}</th>
	 * <td>{@link Level#INFO}</td>
	 * <td>404 NOT FOUND</td>
	 * </tr>
	 * <tr>
	 * <th>{@link IuOutOfServiceException}</th>
	 * <td>{@link Level#CONFIG}</td>
	 * <td>503 SERVICE UNAVAILABLE</td>
	 * </tr>
	 * <tr>
	 * <th>{@link Throwable}</th>
	 * <td>{@link Level#SEVERE}</td>
	 * <td>500 INTERNAL SERVER ERROR</td>
	 * </tr>
	 * </table>
	 * 
	 * @param nodeId        node identifier
	 * @param requestNumber request number
	 * @param error         any {@link Throwable}
	 * @param exchange      {@link HttpExchange}
	 * @param webContext    {@link IuWebContext}; may be null if not yet known
	 */
	static void handleError(String nodeId, String requestNumber, Throwable error, HttpExchange exchange,
			IuWebContext webContext) {
		final int status = getStatus(error);
		final Level level = getLevel(status);

		IuException.suppress(error, () -> {
			final var response = IuText.utf8(new ErrorDetails(nodeId, requestNumber, webContext, status).toString());
			exchange.getResponseHeaders().put("Content-Type",
					Collections.singletonList("application/json; charset=utf-8"));
			exchange.sendResponseHeaders(status, response.length);
			exchange.getResponseBody().write(response);
			exchange.getResponseBody().flush();
			exchange.close();
		});

		LOG.log(level, error, () -> describeResponse(exchange) + "\n" + describeRequest(exchange));
	}

}
