package iu.web.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.IdGenerator;
import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuNotFoundException;
import edu.iu.IuOutOfServiceException;
import edu.iu.IuText;
import edu.iu.test.IuTestLogger;
import edu.iu.web.IuWebContext;

@SuppressWarnings("javadoc")
public class WebServerUtilsTest {

	// copied from com.sun.net.httpserver.Headers
	private String normalize(String key) {
		Objects.requireNonNull(key);
		int len = key.length();
		if (len == 0) {
			return key;
		}
		char[] b = key.toCharArray();
		if (b[0] >= 'a' && b[0] <= 'z') {
			b[0] = (char) (b[0] - ('a' - 'A'));
		} else if (b[0] == '\r' || b[0] == '\n')
			throw new IllegalArgumentException("illegal character in key");

		for (int i = 1; i < len; i++) {
			if (b[i] >= 'A' && b[i] <= 'Z') {
				b[i] = (char) (b[i] + ('a' - 'A'));
			} else if (b[i] == '\r' || b[i] == '\n')
				throw new IllegalArgumentException("illegal character in key");
		}
		return new String(b);
	}

	@Test
	public void testDescribeRequest() {
		final var contextPath = IdGenerator.generateId();

		final var context = mock(HttpContext.class);
		when(context.getPath()).thenReturn(contextPath);

		final var headerName = IdGenerator.generateId();
		final var headerValue1 = IdGenerator.generateId();
		final var headerValue2 = IdGenerator.generateId();
		final var headers = new Headers();
		headers.put(headerName, List.of(headerValue1, headerValue2));

		final var protocol = IdGenerator.generateId();
		final var method = IdGenerator.generateId();
		final var remoteAddress = mock(InetSocketAddress.class);
		final var localAddress = mock(InetSocketAddress.class);
		final var requestUri = URI.create(IdGenerator.generateId());

		final var exchange = mock(HttpExchange.class);
		when(exchange.getProtocol()).thenReturn(protocol);
		when(exchange.getRequestMethod()).thenReturn(method);
		when(exchange.getRemoteAddress()).thenReturn(remoteAddress);
		when(exchange.getLocalAddress()).thenReturn(localAddress);
		when(exchange.getRequestURI()).thenReturn(requestUri);
		when(exchange.getHttpContext()).thenReturn(context);
		when(exchange.getRequestHeaders()).thenReturn(headers);

		assertEquals(
				"HTTP request\n  Protocol: " + protocol + "\n  Method: " + method + "\n  Remote Address: "
						+ remoteAddress + "\n  Local Address: " + localAddress + "\n  Context Path: " + contextPath
						+ "\n  Request URI: " + requestUri + "\n  Headers:\n    " + normalize(headerName) + " = "
						+ headerValue1 + "\n    " + normalize(headerName) + " = " + headerValue2,
				WebServerUtils.describeRequest(exchange));
	}

	@Test
	public void testDescribeResponse() {
		final var exchange = mock(HttpExchange.class);
		when(exchange.getResponseCode()).thenReturn(200);
		final var headerName = IdGenerator.generateId();
		final var headerValue1 = IdGenerator.generateId();
		final var headerValue2 = IdGenerator.generateId();
		final var headers = new Headers();
		headers.put(headerName, List.of(headerValue1, headerValue2));

		when(exchange.getResponseHeaders()).thenReturn(headers);

		assertEquals("HTTP 200 OK\n  Headers:\n    " + normalize(headerName) + " = " + headerValue1 + "\n    "
				+ normalize(headerName) + " = " + headerValue2, WebServerUtils.describeResponse(exchange));
	}

	@Test
	public void testGetStatus() {
		assertEquals(400, WebServerUtils.getStatus(new IuBadRequestException()));
		assertEquals(403, WebServerUtils.getStatus(new IuAuthorizationFailedException()));
		assertEquals(404, WebServerUtils.getStatus(new IuNotFoundException()));
		assertEquals(500, WebServerUtils.getStatus(new IllegalStateException()));
		assertEquals(503, WebServerUtils.getStatus(new IuOutOfServiceException()));
	}

	@Test
	public void testGetLevel() {
		assertEquals(Level.FINE, WebServerUtils.getLevel(200));
		assertEquals(Level.FINE, WebServerUtils.getLevel(301));
		assertEquals(Level.FINE, WebServerUtils.getLevel(302));
		assertEquals(Level.WARNING, WebServerUtils.getLevel(400));
		assertEquals(Level.INFO, WebServerUtils.getLevel(403));
		assertEquals(Level.INFO, WebServerUtils.getLevel(404));
		assertEquals(Level.SEVERE, WebServerUtils.getLevel(500));
		assertEquals(Level.CONFIG, WebServerUtils.getLevel(503));
	}

	@Test
	public void testHandleError() throws IOException {
		final var requestDescr = IdGenerator.generateId();
		final var responseDescr = IdGenerator.generateId();
		final var nodeId = IdGenerator.generateId();
		final var requestNum = IdGenerator.generateId();
		final var serializedErrorDetails = IdGenerator.generateId();
		final var error = new Throwable();

		final var responseHeaders = mock(Headers.class);
		final var responseBody = new ByteArrayOutputStream();
		final var exchange = mock(HttpExchange.class);
		when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
		when(exchange.getResponseBody()).thenReturn(responseBody);

		final var webContext = mock(IuWebContext.class);
		try (final var mockWebServerUtils = mockStatic(WebServerUtils.class, CALLS_REAL_METHODS);
				final var mockErrorDetails = mockConstruction(ErrorDetails.class, (a, ctx) -> {
					final var args = ctx.arguments();
					assertEquals(nodeId, args.get(0), args::toString);
					assertEquals(requestNum, args.get(1), args::toString);
					assertEquals(webContext, args.get(2), args::toString);
					assertEquals(500, args.get(3), args::toString);
					when(a.toString()).thenReturn(serializedErrorDetails);
				})) {
			mockWebServerUtils.when(() -> WebServerUtils.describeRequest(exchange)).thenReturn(requestDescr);
			mockWebServerUtils.when(() -> WebServerUtils.describeResponse(exchange)).thenReturn(responseDescr);
			IuTestLogger.expect(WebServerUtils.class.getName(), Level.SEVERE, responseDescr + "\n" + requestDescr,
					Throwable.class, a -> a == error);
			assertDoesNotThrow(() -> WebServerUtils.handleError(nodeId, requestNum, error, exchange, webContext));
			try {
				assertEquals(0, error.getSuppressed().length);
			} catch (AssertionFailedError e) {
				e.addSuppressed(error);
				throw e;
			}

			verify(responseHeaders).put("Content-Type", List.of("application/json; charset=utf-8"));
			verify(exchange).sendResponseHeaders(500, serializedErrorDetails.length());
			assertEquals(serializedErrorDetails, IuText.utf8(responseBody.toByteArray()));
			verify(exchange).close();
		}
	}

	@Test
	public void testReplaceAuthority() {
		final var was = IdGenerator.generateId();
		final var path = "/" + IdGenerator.generateId();
		final var u = URI.create("test://" + was + path);
		assertEquals(u, WebServerUtils.replaceAuthority(u, null));
		
		final var is = IdGenerator.generateId();
		assertEquals(URI.create("test://" + is + path), WebServerUtils.replaceAuthority(u, is));
		
		final var u2 = URI.create("test://" + was);
		assertEquals(URI.create("test://" + is), WebServerUtils.replaceAuthority(u2, is));
	}

}
