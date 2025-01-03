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
package iu.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.UnsafeSupplier;
import edu.iu.test.IuTestLogger;
import iu.logging.internal.IuLogHandler;
import iu.logging.internal.ProcessLogger;

@SuppressWarnings("javadoc")
public class BootstrapTest extends IuLoggingTestCase {

	private String nodeId;
	private String endpoint;
	private String application;
	private String environment;
	private String module;
	private String runtime;
	private String component;

	@BeforeEach
	public void setup() {
		nodeId = IdGenerator.generateId().replace("-", "_");
		endpoint = IdGenerator.generateId().replace("-", "_");
		application = IdGenerator.generateId().replace("-", "_");
		environment = IdGenerator.generateId().replace("-", "_");
		module = IdGenerator.generateId().replace("-", "_");
		runtime = IdGenerator.generateId().replace("-", "_");
		component = IdGenerator.generateId().replace("-", "_");
	}

	@Test
	public void testInitialize() throws IOException {
		IuTestLogger.expect("", Level.CONFIG,
				"IuLogContext initialized IuLogHandler \\[logEvents=\\d+, maxEvents=100000, eventTtl=PT24H, purge=iu-java-logging-purge/\\d+, closed=false\\] LogEnvironment \\[nodeId="
						+ NODE_ID + ", development=false, endpoint=" + DEFAULT_ENDPOINT + ", application="
						+ DEFAULT_APPLICATION + ", environment=" + DEFAULT_ENVIRONMENT + ", module=" + DEFAULT_MODULE
						+ ", runtime=" + DEFAULT_RUNTIME + ", component=" + DEFAULT_COMPONENT + "\\]");

		Bootstrap.initialize();

		assertThrows(IllegalStateException.class, () -> Bootstrap.initializeContext(nodeId, false, endpoint,
				application, environment, module, runtime, component));

		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try {
			current.setContextClassLoader(ClassLoader.getPlatformClassLoader());
			assertThrows(IllegalStateException.class, () -> Bootstrap.initializeContext(nodeId, false, endpoint,
					application, environment, module, runtime, component));
		} finally {
			current.setContextClassLoader(restore);
		}
		assertThrows(IllegalStateException.class, () -> Bootstrap.initializeContext(nodeId, false, endpoint,
				application, environment, module, runtime, component));

		final var env = Bootstrap.getEnvironment();
		assertFalse(env.isDevelopment());
		assertEquals(DEFAULT_ENDPOINT, env.getEndpoint());
		assertEquals(DEFAULT_APPLICATION, env.getApplication());
		assertEquals(DEFAULT_ENVIRONMENT, env.getEnvironment());
		assertEquals(DEFAULT_MODULE, env.getModule());
		assertEquals(DEFAULT_RUNTIME, env.getRuntime());
		assertEquals(DEFAULT_COMPONENT, env.getComponent());
	}

	@Test
	public void testInitializeContext() throws IOException {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try (final var loader = new URLClassLoader(new URL[0])) {
			current.setContextClassLoader(loader);

			IuTestLogger.expect("", Level.CONFIG,
					"IuLogContext initialized IuLogHandler \\[logEvents=\\d+, maxEvents=100000, eventTtl=PT24H, purge=iu-java-logging-purge/\\d+, closed=false\\] LogEnvironment \\[nodeId="
							+ nodeId + ", development=true, endpoint=" + endpoint + ", application=" + application
							+ ", environment=" + environment + ", module=" + module + ", runtime=" + runtime
							+ ", component=" + component + ", defaults=LogEnvironment \\[nodeId=" + NODE_ID
							+ ", development=false, endpoint=" + DEFAULT_ENDPOINT + ", application="
							+ DEFAULT_APPLICATION + ", environment=" + DEFAULT_ENVIRONMENT + ", module="
							+ DEFAULT_MODULE + ", runtime=" + DEFAULT_RUNTIME + ", component=" + DEFAULT_COMPONENT
							+ "\\]\\]; context: " + loader);
			Bootstrap.initializeContext(nodeId, true, endpoint, application, environment, module, runtime, component);
			assertThrows(IllegalStateException.class, () -> Bootstrap.initializeContext(nodeId, false, endpoint,
					application, environment, module, runtime, component));

			final var env = Bootstrap.getEnvironment();
			assertTrue(env.isDevelopment());
			assertEquals(endpoint, env.getEndpoint());
			assertEquals(application, env.getApplication());
			assertEquals(environment, env.getEnvironment());
			assertEquals(module, env.getModule());
			assertEquals(runtime, env.getRuntime());
			assertEquals(component, env.getComponent());
		} finally {
			Thread.currentThread().setContextClassLoader(restore);
		}
	}

	@Test
	public void testNoConfig() {
		System.setProperty("iu.config", "");
		try {
			assertThrows(NullPointerException.class, () -> Bootstrap.configure(false));
			assertFalse(Bootstrap.configure(true));
		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testReadNoProperties() {
		System.setProperty("iu.config", IdGenerator.generateId());
		final var logManager = mock(LogManager.class);
		try (final var mockLogManager = mockStatic(LogManager.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);

			final var error = assertThrows(IllegalStateException.class, () -> Bootstrap.configure(false));
			assertEquals("Missing " + System.getProperty("iu.config") + File.separator + "logging.properties",
					error.getMessage());

			mockLogManager.verifyNoInteractions();
			verifyNoInteractions(logManager);

		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testUpdateNoProperties() {
		System.setProperty("iu.config", IdGenerator.generateId());
		final var logManager = mock(LogManager.class);
		try (final var mockLogManager = mockStatic(LogManager.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);

			assertDoesNotThrow(() -> Bootstrap.configure(true));

			mockLogManager.verifyNoInteractions();
			verifyNoInteractions(logManager);

		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testReadWithProperties() {
		final var logManager = mock(LogManager.class);
		final var in = mock(InputStream.class);

		final var config = IdGenerator.generateId();
		System.setProperty("iu.config", config);
		try (final var mockLogManager = mockStatic(LogManager.class); //
				final var mockLogger = mockStatic(Logger.class); //
				final var mockFiles = mockStatic(Files.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);

			final var loggingProperties = Path.of(config, "logging.properties");
			mockFiles.when(() -> Files.isReadable(loggingProperties)).thenReturn(true);
			mockFiles.when(() -> Files.newInputStream(loggingProperties)).thenReturn(in);

			IuTestLogger.allow(Bootstrap.class.getName(), Level.CONFIG);
			Bootstrap.configure(false);
			mockLogManager.verify(() -> LogManager.getLogManager());
			mockLogManager.verifyNoMoreInteractions();
			assertDoesNotThrow(() -> verify(logManager).readConfiguration(in));
			verifyNoMoreInteractions(logManager);

		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testUpdateWithProperties() {
		final var logManager = mock(LogManager.class);
		final var in = mock(InputStream.class);

		final var config = IdGenerator.generateId();
		System.setProperty("iu.config", config);
		try (final var mockLogManager = mockStatic(LogManager.class); //
				final var mockLogger = mockStatic(Logger.class); //
				final var mockFiles = mockStatic(Files.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);

			final var loggingProperties = Path.of(config, "logging.properties");
			mockFiles.when(() -> Files.isReadable(loggingProperties)).thenReturn(true);
			mockFiles.when(() -> Files.newInputStream(loggingProperties)).thenReturn(in);

			IuTestLogger.allow(Bootstrap.class.getName(), Level.CONFIG);
			Bootstrap.configure(true);
			mockLogManager.verify(() -> LogManager.getLogManager());
			mockLogManager.verifyNoMoreInteractions();
			assertDoesNotThrow(() -> verify(logManager).updateConfiguration(in, null));
			verifyNoMoreInteractions(logManager);

		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testActiveContext() {
		final var context = mock(LogContext.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class)) {
			mockProcessLogger.when(() -> ProcessLogger.getActiveContext()).thenReturn(context);
			assertSame(context, Bootstrap.getActiveContext(LogContext.class));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFollow() {
		final var context = mock(LogContext.class);
		final var supplier = mock(UnsafeSupplier.class);
		final var header = IdGenerator.generateId();
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class)) {
			assertDoesNotThrow(() -> Bootstrap.follow(context, header, supplier));
			mockProcessLogger.verify(() -> ProcessLogger.follow(context, header, supplier));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testTrace() {
		final var supplier = mock(Supplier.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class)) {
			assertDoesNotThrow(() -> Bootstrap.trace(supplier));
			mockProcessLogger.verify(() -> ProcessLogger.trace(supplier));
		}
	}

	interface Event {
		String getMessage();
	}

	@Test
	public void testSubscribe() throws InterruptedException {
		final var message = IdGenerator.generateId();
		IuTestLogger.expect("", Level.INFO, message);
		Logger.getLogger("").info(message);
		try (final var s = Bootstrap.subscribe(Event.class)) {
			final var t = new Thread() {
				Event last;

				@Override
				public void run() {
					s.forEach(a -> {
						last = a;
					});
				}
			};
			t.start();
			t.join(1000L);
			assertNotNull(t.last, "no log events");
			assertEquals(message, t.last.getMessage());
		}
	}

	@Test
	public void testDestroyNothing() {
		final var logManager = mock(LogManager.class);
		try (final var mockLogManager = mockStatic(LogManager.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
			assertDoesNotThrow(Bootstrap::destroy);
		}
	}

	@Test
	public void testDestroy() {
		final var h = mock(IuLogHandler.class);
		final var h2 = mock(Handler.class);
		final var log = mock(Logger.class);
		when(log.getHandlers()).thenReturn(new Handler[] { h, h2 });

		final var logManager = mock(LogManager.class);
		when(logManager.getLogger("")).thenReturn(log);
		try (final var mockLogManager = mockStatic(LogManager.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
			assertDoesNotThrow(Bootstrap::destroy);
			verify(log).removeHandler(h);
			verify(log, never()).removeHandler(h2);
		}
	}

}
