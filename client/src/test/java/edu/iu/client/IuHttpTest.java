package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class IuHttpTest {

	private static final URI TEST_URI = URI.create("test:" + IdGenerator.generateId());
	private static HttpClient http;

	@BeforeAll
	public static void setupClass() throws ClassNotFoundException {
		try {
			System.setProperty("iu.http.allowedUri", TEST_URI.toString());

			http = mock(HttpClient.class);
			try (final var mockHttpClient = mockStatic(HttpClient.class)) {
				mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(http);
				Class.forName(IuHttp.class.getName());
			}

		} finally {
			System.getProperties().remove("iu-client.allowedUri");
		}
	}

	private Handler logHandler;
	private Level restoreLevel;
	private boolean restoreUseParentHandlers;

	@BeforeEach
	public void setup() {
		final var log = LogManager.getLogManager().getLogger(IuHttp.class.getName());
		restoreLevel = log.getLevel();
		restoreUseParentHandlers = log.getUseParentHandlers();
		log.setUseParentHandlers(false);
		log.setLevel(Level.FINE);
		logHandler = mock(Handler.class);
		log.addHandler(logHandler);
	}

	@AfterEach
	public void tearDown() {
		final var log = LogManager.getLogManager().getLogger(IuHttp.class.getName());
		log.setLevel(restoreLevel);
		log.setUseParentHandlers(restoreUseParentHandlers);
		log.removeHandler(logHandler);
		logHandler = null;
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOk() {
		final var response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(200, 201);
		assertDoesNotThrow(() -> IuHttp.OK.accept(response));
		assertThrows(HttpException.class, () -> IuHttp.OK.accept(response));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCheckHeaders() {
		final var response = mock(HttpResponse.class);
		when(response.headers()).thenReturn(HttpHeaders.of(Map.of("foo", List.of("bar", "baz")), (a, b) -> true));
		assertDoesNotThrow(() -> IuHttp.checkHeaders((n, v) -> n.equals("foo")).accept(response));
		assertThrows(HttpException.class, () -> IuHttp.checkHeaders((n, v) -> v.equals("bar")).accept(response));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testValidate() {
		final var response = mock(HttpResponse.class);
		final var validator = mock(HttpResponseValidator.class);
		final var handler = mock(HttpResponseHandler.class);
		assertDoesNotThrow(() -> IuHttp.validate(handler, validator).apply(response));
		assertDoesNotThrow(() -> verify(validator).accept(response));
		assertDoesNotThrow(() -> verify(handler).apply(response));
	}

	@Test
	public void testGetCallsSend() throws Exception {
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.get(TEST_URI)).thenCallRealMethod();
			IuHttp.get(TEST_URI);
			mockHttp.verify(() -> IuHttp.send(TEST_URI, null));
		}
	}

	@Test
	public void testDenyByDefault() {
		assertThrows(IllegalArgumentException.class, () -> IuHttp.get(mock(URI.class)));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPublicGet() throws Exception {
		try (final var mockRequest = mockStatic(HttpRequest.class)) {
			final var request = mock(HttpRequest.class);
			when(request.method()).thenReturn("GET");
			when(request.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
			when(request.uri()).thenReturn(TEST_URI);

			final var mockBuilder = mock(HttpRequest.Builder.class);
			when(mockBuilder.build()).thenReturn(request);
			mockRequest.when(() -> HttpRequest.newBuilder(TEST_URI)).thenReturn(mockBuilder);

			final var response = mock(HttpResponse.class);
			when(response.statusCode()).thenReturn(200);
			when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
			when(http.send(eq(request), any(BodyHandler.class))).thenReturn(response);

			final var body = new Object();
			final var handler = mock(HttpResponseHandler.class);
			when(handler.apply(response)).thenReturn(body);
			assertSame(body, IuHttp.get(TEST_URI, handler));
			verify(logHandler).publish(argThat(r -> {
				assertEquals(Level.FINE, r.getLevel());
				assertEquals("GET " + TEST_URI + " 200 OK", r.getMessage());
				return true;
			}));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testModifiedRequest() throws Exception {
		try (final var mockRequest = mockStatic(HttpRequest.class)) {
			final var request = mock(HttpRequest.class);
			when(request.method()).thenReturn("POST");
			final var requestHeaderName = IdGenerator.generateId();
			final var requestHeaderValue = IdGenerator.generateId();
			when(request.headers())
					.thenReturn(HttpHeaders.of(Map.of(requestHeaderName, List.of(requestHeaderValue)), (a, b) -> true));
			when(request.uri()).thenReturn(TEST_URI);

			final var mockBuilder = mock(HttpRequest.Builder.class);
			when(mockBuilder.build()).thenReturn(request);
			mockRequest.when(() -> HttpRequest.newBuilder(TEST_URI)).thenReturn(mockBuilder);

			final var mockConsumer = mock(Consumer.class);
			final var response = mock(HttpResponse.class);
			when(response.statusCode()).thenReturn(202);

			final var responseHeaderName = IdGenerator.generateId();
			final var responseHeaderValue = IdGenerator.generateId();
			when(response.headers()).thenReturn(
					HttpHeaders.of(Map.of(responseHeaderName, List.of(responseHeaderValue)), (a, b) -> true));

			when(http.send(eq(request), any(BodyHandler.class))).thenReturn(response);

			final var body = new Object();
			final var handler = mock(HttpResponseHandler.class);
			when(handler.apply(response)).thenReturn(body);
			assertSame(body, IuHttp.send(TEST_URI, mockConsumer, handler));

			verify(mockConsumer).accept(mockBuilder);
			verify(logHandler).publish(argThat(r -> {
				assertEquals(Level.FINE, r.getLevel());
				assertEquals(
						"POST " + TEST_URI + " [" + requestHeaderName + "] 202 ACCEPTED [" + responseHeaderName + "]",
						r.getMessage());
				return true;
			}));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testConnectionError() throws Exception {
		try (final var mockRequest = mockStatic(HttpRequest.class)) {
			final var request = mock(HttpRequest.class);
			when(request.method()).thenReturn("GET");
			when(request.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
			when(request.uri()).thenReturn(TEST_URI);

			final var mockBuilder = mock(HttpRequest.Builder.class);
			when(mockBuilder.build()).thenReturn(request);
			mockRequest.when(() -> HttpRequest.newBuilder(TEST_URI)).thenReturn(mockBuilder);

			final var e = new IOException();
			when(http.send(eq(request), any(BodyHandler.class))).thenThrow(e);

			final var t = assertThrows(IllegalStateException.class, () -> IuHttp.get(TEST_URI));
			assertEquals("HTTP connection failed GET " + TEST_URI, t.getMessage());

			verify(logHandler).publish(argThat(r -> {
				assertEquals(Level.INFO, r.getLevel());
				assertEquals(t.getMessage(), r.getMessage());
				assertSame(e, r.getThrown());
				return true;
			}));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testErrorResponse() throws Exception {
		try (final var mockRequest = mockStatic(HttpRequest.class)) {
			final var request = mock(HttpRequest.class);
			when(request.method()).thenReturn("GET");
			when(request.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
			when(request.uri()).thenReturn(TEST_URI);

			final var mockBuilder = mock(HttpRequest.Builder.class);
			when(mockBuilder.build()).thenReturn(request);
			mockRequest.when(() -> HttpRequest.newBuilder(TEST_URI)).thenReturn(mockBuilder);

			final var response = mock(HttpResponse.class);
			when(response.statusCode()).thenReturn(404);
			when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
			when(http.send(eq(request), any(BodyHandler.class))).thenReturn(response);

			final var t = assertThrows(HttpException.class, () -> IuHttp.get(TEST_URI));
			assertEquals("GET " + TEST_URI + " 404 NOT FOUND", t.getMessage());
			assertSame(response, t.getResponse());

			verify(logHandler).publish(argThat(r -> {
				assertEquals(Level.INFO, r.getLevel());
				assertEquals(t.getMessage(), r.getMessage());
				assertSame(t, r.getThrown());
				return true;
			}));
		}
	}

}
