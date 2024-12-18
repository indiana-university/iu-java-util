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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.UnsafeSupplier;
import edu.iu.test.IuTestLogger;
import iu.logging.internal.ProcessLogger;

@SuppressWarnings("javadoc")
public class BootstrapTest extends IuLoggingTestCase {

	private String endpoint;
	private String application;
	private String environment;

	@Test
	public void testinitialize() throws IOException {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try (final var loader = new URLClassLoader(new URL[0])) {
			current.setContextClassLoader(loader);

			IuTestLogger.expect("", Level.CONFIG,
					"IU Logging Bootstrap initialized IuLogHandler \\[logEvents=\\d+, maxEvents=100000, eventTtl=PT24H, purge=iu-java-logging-purge/\\d+, closed=false\\] DefaultLogContext \\[nodeId="
							+ NODE_ID + ", endpoint=" + endpoint + ", application=" + application + ", environment="
							+ environment + "\\]; context: " + loader);
			Bootstrap.initializeContext(endpoint, application, environment);
			final var defaultContext = Bootstrap.getDefaultContext();

			assertEquals(application, defaultContext.getApplication());
			assertEquals(endpoint, defaultContext.getEndpoint());
			assertEquals(environment, defaultContext.getEnvironment());
		} finally {
			Thread.currentThread().setContextClassLoader(restore);
		}
	}

	@Test
	public void testReadNoProperties() {
		System.setProperty("iu.config", IdGenerator.generateId());
		final var logManager = mock(LogManager.class);
		try (final var mockLogManager = mockStatic(LogManager.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);

			Bootstrap.configure(false);
			mockLogManager.verify(() -> LogManager.getLogManager());
			mockLogManager.verifyNoMoreInteractions();

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

			Bootstrap.configure(true);
			mockLogManager.verify(() -> LogManager.getLogManager());
			mockLogManager.verifyNoMoreInteractions();

			verifyNoInteractions(logManager);

		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testReadWithProperties() {
		final var logManager = mock(LogManager.class);
		final var logger = mock(Logger.class);
		final var in = mock(InputStream.class);

		final var config = IdGenerator.generateId();
		System.setProperty("iu.config", config);
		try (final var mockLogManager = mockStatic(LogManager.class); //
				final var mockLogger = mockStatic(Logger.class); //
				final var mockFiles = mockStatic(Files.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
			mockLogger.when(() -> Logger.getLogger("")).thenReturn(logger);

			final var loggingProperties = Path.of(config, "logging.properties");
			mockFiles.when(() -> Files.isReadable(loggingProperties)).thenReturn(true);
			mockFiles.when(() -> Files.newInputStream(loggingProperties)).thenReturn(in);

			Bootstrap.configure(false);
			mockLogManager.verify(() -> LogManager.getLogManager());
			mockLogManager.verifyNoMoreInteractions();
			assertDoesNotThrow(() -> verify(logManager).readConfiguration(in));
			verifyNoMoreInteractions(logManager);

			mockLogger.verify(() -> Logger.getLogger(""));
			verify(logger).config("Logging configuration updated from " + loggingProperties);
			verifyNoMoreInteractions(logger);

		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testUpdateWithProperties() {
		final var logManager = mock(LogManager.class);
		final var logger = mock(Logger.class);
		final var in = mock(InputStream.class);

		final var config = IdGenerator.generateId();
		System.setProperty("iu.config", config);
		try (final var mockLogManager = mockStatic(LogManager.class); //
				final var mockLogger = mockStatic(Logger.class); //
				final var mockFiles = mockStatic(Files.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
			mockLogger.when(() -> Logger.getLogger("")).thenReturn(logger);

			final var loggingProperties = Path.of(config, "logging.properties");
			mockFiles.when(() -> Files.isReadable(loggingProperties)).thenReturn(true);
			mockFiles.when(() -> Files.newInputStream(loggingProperties)).thenReturn(in);

			Bootstrap.configure(true);
			mockLogManager.verify(() -> LogManager.getLogManager());
			mockLogManager.verifyNoMoreInteractions();
			assertDoesNotThrow(() -> verify(logManager).updateConfiguration(in, null));
			verifyNoMoreInteractions(logManager);

			mockLogger.verify(() -> Logger.getLogger(""));
			verify(logger).config("Logging configuration updated from " + loggingProperties);
			verifyNoMoreInteractions(logger);

		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testDefaultContextinitialize() throws IOException {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try (final var loader = new URLClassLoader(new URL[0])) {
			current.setContextClassLoader(loader);

			IuTestLogger.expect("", Level.CONFIG,
					"IU Logging Bootstrap initialized IuLogHandler \\[logEvents=\\d+, maxEvents=100000, eventTtl=PT24H, purge=iu-java-logging-purge/\\d+, closed=false\\] DefaultLogContext \\[nodeId="
							+ NODE_ID + ", endpoint=" + endpoint + ", application=" + application + ", environment="
							+ environment + "\\]; context: " + loader);
			Bootstrap.initializeContext(endpoint, application, environment);

			final var context = Bootstrap.getDefaultContext();
			assertEquals(NODE_ID, context.getNodeId());
			assertEquals(endpoint, context.getEndpoint());
			assertEquals(application, context.getApplication());
			assertEquals(environment, context.getEnvironment());
		} finally {
			current.setContextClassLoader(restore);
		}
	}

	@Test
	public void testDefaultContextNotinit() throws IOException {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try (final var loader = new URLClassLoader(new URL[0])) {
			current.setContextClassLoader(loader);

			final var context = Bootstrap.getDefaultContext();
			assertEquals(NODE_ID, context.getNodeId());
			assertEquals(DEFAULT_ENDPOINT, context.getEndpoint());
			assertEquals(DEFAULT_APPLICATION, context.getApplication());
			assertEquals(DEFAULT_ENVIRONMENT, context.getEnvironment());
		} finally {
			current.setContextClassLoader(restore);
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

}
