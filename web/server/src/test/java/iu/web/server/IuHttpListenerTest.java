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

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuHttpListenerTest {

	@Test
	public void testStartStopGetIndex() {
//		final var server = mock(HttpServer.class);
//		final var address = mock(InetSocketAddress.class);
//		final int threads = ThreadLocalRandom.current().nextInt();
//		final int backlog = ThreadLocalRandom.current().nextInt();
//		final int stopDelay = ThreadLocalRandom.current().nextInt();
//		try (final var mockHttpServer = mockStatic(HttpServer.class);
//				final var paths = mockStatic(Paths.class, CALLS_REAL_METHODS)) {
//			mockHttpServer.when(() -> HttpServer.create(address, backlog)).thenReturn(server);
//
//			final var requestUri = "http://localhost:8080/";
//			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
//					"started IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + ']');
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO, "path: /");
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO,
//					"file handler handling request URI: " + requestUri);
//
//			final var externalUri = URI.create(IdGenerator.generateId());
//			final var application = IdGenerator.generateId();
//			final var environment = IdGenerator.generateId();
//			final var module = IdGenerator.generateId();
//			final var runtime = IdGenerator.generateId();
//			final var component = IdGenerator.generateId();
//
//			final var authenticator = mock(Authenticator.class);
//			final var context = mock(IuWebContext.class);
//			when(context.getApplication()).thenReturn(application);
//			when(context.getEnvironment()).thenReturn(environment);
//			when(context.getModule()).thenReturn(module);
//			when(context.getRuntime()).thenReturn(runtime);
//			when(context.getComponent()).thenReturn(component);
//			
//			final var handler = mock(HttpHandler.class);
//			final var path = "/" + IdGenerator.generateId();
//			when(context.getPath()).thenReturn(path);
//			when(context.getHandler()).thenReturn(handler);
//			final var contexts = IuIterable.iter(context);
//			final var archivePathProperty = IuTest.getProperty("teststatic.archive");
//			final var archivePath = Path.of(archivePathProperty);
//			paths.when(() -> Paths.get("/opt/starch/resources")).thenReturn(archivePath);
//			final var listener = assertDoesNotThrow(() -> IuHttpListener.create(address, backlog, stopDelay));
//			verify(server).createContext(eq("/"), argThat((HttpHandler a) -> {
//				final var exchange = mock(HttpExchange.class);
//				when(exchange.getRequestMethod()).thenReturn("GET");
//
//				when(exchange.getRequestURI()).thenReturn(URI.create(requestUri));
//
//			final var rootContext = mock(HttpContext.class);
//			when(server.createContext("/")).thenReturn(rootContext);
//			final var appContext = mock(HttpContext.class);
//			when(server.createContext(path)).thenReturn(appContext);
//
//			final var listener = assertDoesNotThrow(() -> IuHttpListener.create(externalUri, address, authenticator,
//					contexts, threads, backlog, stopDelay));
//			mockIuLogContext.verify(() -> IuLogContext.initializeContext(null, false, externalUri + path, application,
//					environment, module, runtime, component));
//			verify(server).createContext(path);
//			verify(appContext).setHandler(handler);
//			
//			verify(server).setExecutor(null);
//			verify(server).start();
//
//			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
//					"stopped IuHttpListener [stopDelay=" + stopDelay + ", server=null]; " + server);
//
//			assertDoesNotThrow(listener::close);
//			verify(server).stop(stopDelay);
//
//			assertDoesNotThrow(listener::close);
//			verifyNoMoreInteractions(server);
//		}
	}

//	@Test
//	public void testPost() {
//		final var server = mock(HttpServer.class);
//		final var address = mock(InetSocketAddress.class);
//		final int backlog = ThreadLocalRandom.current().nextInt();
//		final int stopDelay = ThreadLocalRandom.current().nextInt();
//		try (final var mockHttpServer = mockStatic(HttpServer.class);
//				final var paths = mockStatic(Paths.class, CALLS_REAL_METHODS)) {
//			mockHttpServer.when(() -> HttpServer.create(address, backlog)).thenReturn(server);
//
//			final var requestUri = "http://localhost:8080/";
//			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
//					"started IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + ']');
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO, "path: /");
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.WARNING, "files context method not allowed: POST");
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO,
//					"files context fallback. request URI: " + requestUri);
//
//			final var archivePathProperty = IuTest.getProperty("teststatic.archive");
//			final var archivePath = Path.of(archivePathProperty);
//			paths.when(() -> Paths.get("/opt/starch/resources")).thenReturn(archivePath);
//			final var listener = assertDoesNotThrow(() -> IuHttpListener.create(address, backlog, stopDelay));
//			verify(server).createContext(eq("/"), argThat((HttpHandler a) -> {
//				final var exchange = mock(HttpExchange.class);
//				when(exchange.getRequestMethod()).thenReturn("POST");
//
//				when(exchange.getRequestURI()).thenReturn(URI.create(requestUri));
//
//				final var headers = mock(Headers.class);
//				when(exchange.getResponseHeaders()).thenReturn(headers);
//				final var body = mock(OutputStream.class);
//				when(exchange.getResponseBody()).thenReturn(body);
//
//				assertDoesNotThrow(() -> a.handle(exchange));
//
//				return true;
//			}));
//		}
//	}
//
//	@Test
//	public void testNotFound() {
//		final var server = mock(HttpServer.class);
//		final var address = mock(InetSocketAddress.class);
//		final int backlog = ThreadLocalRandom.current().nextInt();
//		final int stopDelay = ThreadLocalRandom.current().nextInt();
//		try (final var mockHttpServer = mockStatic(HttpServer.class);
//				final var paths = mockStatic(Paths.class, CALLS_REAL_METHODS)) {
//			mockHttpServer.when(() -> HttpServer.create(address, backlog)).thenReturn(server);
//
//			final var baseAddress = "http://localhost:8080/";
//			final var reqPath = "notExists";
//			final var requestUri = baseAddress + reqPath;
//			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
//					"started IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + ']');
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO, "path: /" + reqPath);
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO, "path: /");
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO,
//					"file handler handling request URI: " + requestUri);
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO,
//					"file handler handling request URI: " + baseAddress);
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.WARNING, "file not found: /" + reqPath);
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.WARNING, "file not found: /");
//
//			final var archivePathProperty = IuTest.getProperty("teststatic.archive");
//			final var archivePath = Path.of(archivePathProperty);
//			paths.when(() -> Paths.get("/opt/starch/resources")).thenReturn(archivePath);
//			final var listener = assertDoesNotThrow(() -> IuHttpListener.create(address, backlog, stopDelay));
//			verify(server).createContext(eq("/"), argThat((HttpHandler a) -> {
//				final var exchange = mock(HttpExchange.class);
//				when(exchange.getRequestMethod()).thenReturn("GET");
//
//				when(exchange.getRequestURI()).thenReturn(URI.create(requestUri));
//
//				final var headers = mock(Headers.class);
//				when(exchange.getResponseHeaders()).thenReturn(headers);
//				final var body = mock(OutputStream.class);
//				when(exchange.getResponseBody()).thenReturn(body);
//
//				assertDoesNotThrow(() -> a.handle(exchange));
//
//				try (final var mockFiles = mockStatic(Files.class)) {
//					mockFiles.when(() -> Files.isRegularFile(any())).thenReturn(false);
//					final var deletedTempExchange = mock(HttpExchange.class);
//					when(deletedTempExchange.getRequestMethod()).thenReturn("GET");
//					when(deletedTempExchange.getRequestURI()).thenReturn(URI.create(baseAddress));
//
//					when(deletedTempExchange.getResponseHeaders()).thenReturn(headers);
//					when(deletedTempExchange.getResponseBody()).thenReturn(body);
//
//					assertDoesNotThrow(() -> a.handle(deletedTempExchange));
//				}
//
//				return true;
//			}));
//		}
//	}
//
//	@Test
//	public void testGetJsMap() {
//		final var server = mock(HttpServer.class);
//		final var address = mock(InetSocketAddress.class);
//		final int backlog = ThreadLocalRandom.current().nextInt();
//		final int stopDelay = ThreadLocalRandom.current().nextInt();
//		try (final var mockHttpServer = mockStatic(HttpServer.class);
//				final var paths = mockStatic(Paths.class, CALLS_REAL_METHODS)) {
//			mockHttpServer.when(() -> HttpServer.create(address, backlog)).thenReturn(server);
//
//			final var reqPath = "assets/main.js.map";
//			final var requestUri = "http://localhost:8080/" + reqPath;
//			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
//					"started IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + ']');
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO, "path: /" + reqPath);
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO,
//					"file handler handling request URI: " + requestUri);
//
//			final var archivePathProperty = IuTest.getProperty("teststatic.archive");
//			final var archivePath = Path.of(archivePathProperty);
//			paths.when(() -> Paths.get("/opt/starch/resources")).thenReturn(archivePath);
//			final var listener = assertDoesNotThrow(() -> IuHttpListener.create(address, backlog, stopDelay));
//			verify(server).createContext(eq("/"), argThat((HttpHandler a) -> {
//				final var exchange = mock(HttpExchange.class);
//				when(exchange.getRequestMethod()).thenReturn("GET");
//
//				when(exchange.getRequestURI()).thenReturn(URI.create(requestUri));
//
//				final var headers = mock(Headers.class);
//				when(exchange.getResponseHeaders()).thenReturn(headers);
//				final var body = mock(OutputStream.class);
//				when(exchange.getResponseBody()).thenReturn(body);
//
//				assertDoesNotThrow(() -> a.handle(exchange));
//				verify(headers).add("content-type", "application/json");
//				assertDoesNotThrow(() -> verify(exchange).sendResponseHeaders(200, 0));
//				// TODO: verify body?
////				assertDoesNotThrow(() -> verify(body).write(IuText.utf8("<html><body><p>TODO: implement "
////						+ StringEscapeUtils.escapeHtml4(exchange.getRequestURI().toString()) + "</p></body></html>")));
//
//				return true;
//			}));
//		}
//	}
//
//	@Test
//	public void testGetDeepIndex() {
//		final var server = mock(HttpServer.class);
//		final var address = mock(InetSocketAddress.class);
//		final int backlog = ThreadLocalRandom.current().nextInt();
//		final int stopDelay = ThreadLocalRandom.current().nextInt();
//		try (final var mockHttpServer = mockStatic(HttpServer.class);
//				final var paths = mockStatic(Paths.class, CALLS_REAL_METHODS)) {
//			mockHttpServer.when(() -> HttpServer.create(address, backlog)).thenReturn(server);
//
//			final var reqPath = "src/other/";
//			final var requestUri = "http://localhost:8080/" + reqPath;
//			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
//					"started IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + ']');
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO, "path: /" + reqPath);
//			IuTestLogger.allow(IuHttpListener.class.getName(), Level.INFO,
//					"file handler handling request URI: " + requestUri);
//
//			final var archivePathProperty = IuTest.getProperty("teststatic.archive");
//			final var archivePath = Path.of(archivePathProperty);
//			paths.when(() -> Paths.get("/opt/starch/resources")).thenReturn(archivePath);
//			final var listener = assertDoesNotThrow(() -> IuHttpListener.create(address, backlog, stopDelay));
//			verify(server).createContext(eq("/"), argThat((HttpHandler a) -> {
//				final var exchange = mock(HttpExchange.class);
//				when(exchange.getRequestMethod()).thenReturn("GET");
//
//				when(exchange.getRequestURI()).thenReturn(URI.create(requestUri));
//
//				final var headers = mock(Headers.class);
//				when(exchange.getResponseHeaders()).thenReturn(headers);
//				final var body = mock(OutputStream.class);
//				when(exchange.getResponseBody()).thenReturn(body);
//
//				assertDoesNotThrow(() -> a.handle(exchange));
//				verify(headers).add("content-type", "text/html");
//				assertDoesNotThrow(() -> verify(exchange).sendResponseHeaders(200, 0));
//				assertDoesNotThrow(() -> verify(body).write(IuText.utf8("<html><body><p>TODO: implement "
//						+ StringEscapeUtils.escapeHtml4(exchange.getRequestURI().toString()) + "</p></body></html>")));
//
//				return true;
//			}));
//
//		}
//	}
//
//	@Test
//	public void testBootstrapStaticFileServerWithNonFileURL() {
//		try (final var paths = mockStatic(Paths.class, CALLS_REAL_METHODS)) {
//			final var archivePathProperty = IuTest.getProperty("teststatic.archive");
//			final var archivePath = Path.of(archivePathProperty);
//			paths.when(() -> Paths.get("/opt/starch/resources")).thenReturn(archivePath);
//
//			final var url = IuException.unchecked(() -> URI.create("http://localhost:8080/").toURL());
//			assertThrows(IllegalArgumentException.class, () -> IuHttpListener.bootstrapStaticFileServer(url));
//		}
//	}

}
