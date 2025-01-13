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
package iu.logging.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuStream;
import iu.logging.Bootstrap;
import iu.logging.IuLoggingTestCase;
import iu.logging.LogEnvironment;
import iu.logging.internal.IuLogHandler.FilePublisherKey;

@SuppressWarnings("javadoc")
public class IuLogHandlerTest extends IuLoggingTestCase {

	@Test
	public void testPublishAndSubscribe() {
		final var msg = IdGenerator.generateId();
		final var env = mock(LogEnvironment.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			mockProcessLogger.when(() -> ProcessLogger.trace(any())).then(a -> {
				assertEquals(msg, ((Supplier<?>) a.getArgument(0)).get());
				return null;
			});
			logHandler.publish(new LogRecord(Level.FINE, null));
			logHandler.publish(new LogRecord(Level.INFO, msg));
			try (final var sub = logHandler.subscribe()) {
				assertEquals(msg, sub.stream().findFirst().get().getMessage());
			}
			assertDoesNotThrow(logHandler::flush);
			assertDoesNotThrow(logHandler::close);
			assertThrows(IllegalStateException.class, () -> logHandler.publish(new LogRecord(Level.INFO, msg)));
		}
	}

	@Test
	public void testPurgeTaskError() {
		final var msg = IdGenerator.generateId();
		final var env = mock(LogEnvironment.class);
		final Throwable error;
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			final var rec = mock(LogRecord.class);
			when(rec.getMessage()).thenReturn(msg);
			when(rec.getLevel()).thenReturn(Level.INFO);
			logHandler.publish(rec);
			error = assertThrows(NullPointerException.class, logHandler::purge);
			assertDoesNotThrow(() -> Thread.sleep(2500L));
		}
		assertTrue(ERR.toString().startsWith(error.toString()), ERR::toString);
	}

	@Test
	public void testPurgeMaxEventsLimit() {
		System.setProperty("iu.logging.maxEvents", "5");
		final var env = mock(LogEnvironment.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			final Queue<String> control = new ArrayDeque<>();
			for (var i = 0; i < 2; i++) {
				final var msg = IdGenerator.generateId();
				control.offer(msg);
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(2000L));
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				if (i > 6)
					control.offer(msg);
				logHandler.publish(new LogRecord(Level.FINE, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(2000L));

			try (final var sub = logHandler.subscribe()) {
				final Queue<String> collected = new ArrayDeque<>();
				final var stream = sub.stream().spliterator();
				Spliterator<IuLogEvent> split;
				while ((split = stream.trySplit()) != null)
					split.forEachRemaining(a -> collected.add(a.getMessage()));

				assertArrayEquals(control.toArray(), collected.toArray(), () -> control + " " + collected);
			}
		} finally {
			System.getProperties().remove("iu.logging.maxEvents");
		}
	}

	@Test
	public void testPurgeEventTtlLimit() {
		System.setProperty("iu.logging.eventTtl", "PT2.1S");
		final var env = mock(LogEnvironment.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			final Deque<String> control = new ArrayDeque<>();
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(2000L));
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				control.offer(msg);
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(2000L));

			try (final var sub = logHandler.subscribe()) {
				final Deque<String> collected = new ArrayDeque<>();
				final var stream = sub.stream().spliterator();
				Spliterator<IuLogEvent> split;
				while ((split = stream.trySplit()) != null)
					split.forEachRemaining(a -> collected.add(a.getMessage()));

				var result = collected.toArray();
				if (result.length > 10)
					result = Arrays.copyOfRange(result, result.length - 10, result.length);
				assertArrayEquals(control.toArray(), result, () -> control + " " + collected);
			}
		} finally {
			System.getProperties().remove("iu.logging.eventTtl");
		}
	}

	@Test
	public void testFileAndConsole() throws IOException {
		final var env = mock(LogEnvironment.class);
		final var path = Path.of("target", "logs", IdGenerator.generateId());
		final var traceName = IdGenerator.generateId();
		System.setProperty("iu.logging.consoleLevel", "INFO");
		System.setProperty("iu.logging.file.path", path.toString());
		System.setProperty("iu.logging.file.trace", traceName);

		final var outControl = new StringBuilder();
		final var debugControl = new StringBuilder();
		final var infoControl = new StringBuilder();
		final var errorControl = new StringBuilder();
		final var traceControl = new StringBuilder();
		String firstMessage = null;

		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);

			final Queue<String> control = new ArrayDeque<>();
			for (var i = 0; i < 2; i++) {
				final var msg = IdGenerator.generateId();
				control.offer(msg);
				logHandler.publish(new LogRecord(Level.WARNING, msg));
			}
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				control.offer(msg);
				final var rec = new LogRecord(Level.FINE, msg);
				rec.setLoggerName(traceName);
				logHandler.publish(rec);
			}

			try (final var sub = logHandler.subscribe()) {
				final Queue<String> collected = new ArrayDeque<>();
				final var stream = sub.stream().spliterator();
				Spliterator<IuLogEvent> split;
				while ((split = stream.trySplit()) != null) {
					for (final var a : IuIterable.of(StreamSupport.stream(split, false)::iterator)) {
						final var message = a.export();
						final var formatted = a.format();
						debugControl.append(formatted).append(System.lineSeparator());
						if (a.getLevel().intValue() >= Level.WARNING.intValue()) {
							if (firstMessage == null)
								firstMessage = message;
							outControl.append(message).append(System.lineSeparator());
							infoControl.append(formatted).append(System.lineSeparator());
							errorControl.append(formatted).append(System.lineSeparator());
						} else if (traceName.equals(a.getLoggerName()))
							traceControl.append(formatted).append(System.lineSeparator());

						collected.add(a.getMessage());
					}
				}

				assertArrayEquals(control.toArray(), collected.toArray());
			}

		} finally {
			System.getProperties().remove("iu.logging.consoleLevel");
			System.getProperties().remove("iu.logging.file.path");
			System.getProperties().remove("iu.logging.file.trace");
		}

		final var debugLog = path.resolve("debug.log");
		final var now = Instant.now();
		while (!Files.exists(debugLog) && Duration.between(now, Instant.now()).getSeconds() < 5L)
			IuException.unchecked(() -> Thread.sleep(100L));

		try (final var debug = Files.newBufferedReader(debugLog)) {
			assertEquals(debugControl.toString(), IuStream.read(debug).toString(), path.toString());
		}
		try (final var info = Files.newBufferedReader(path.resolve("info.log"))) {
			assertEquals(infoControl.toString(), IuStream.read(info).toString(), path.toString());
		}
		try (final var error = Files.newBufferedReader(path.resolve("error.log"))) {
			assertEquals(errorControl.toString(), IuStream.read(error).toString(), path.toString());
		}
		try (final var trace = Files.newBufferedReader(path.resolve(traceName + ".log"))) {
			assertEquals(traceControl.toString(), IuStream.read(trace).toString(), path.toString());
		}

		try {
			assertEquals(outControl.toString(), OUT.toString(), ERR::toString);
		} catch (AssertionFailedError e) {
			if (firstMessage != null)
				try { // async subject race condition < %1
						// second split occasionally contains duplicates
					assertEquals(firstMessage + outControl, OUT.toString());
				} catch (AssertionFailedError e2) {
					e.addSuppressed(e2);
				}
			throw e;
		}

	}

	@Test
	public void testFilePerApp() throws IOException {
		final var app = IdGenerator.generateId();
		final var env = mock(LogEnvironment.class);
		when(env.getApplication()).thenReturn(app);

		final var path = Path.of("target", "logs", IdGenerator.generateId());
		System.setProperty("iu.logging.file.path", path.toString());

		final String control;
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);

			final var message = IdGenerator.generateId();
			logHandler.publish(new LogRecord(Level.INFO, message));

			try (final var sub = logHandler.subscribe()) {
				final var stream = sub.stream().spliterator();
				Spliterator<IuLogEvent> split;
				final var c = new Consumer<IuLogEvent>() {
					IuLogEvent event;

					@Override
					public void accept(IuLogEvent t) {
						event = t;
					}
				};
				while ((split = stream.trySplit()) != null)
					if (split.tryAdvance(c))
						break;
				control = c.event.format() + System.lineSeparator();
			}
		}
		assertFalse(control.isEmpty());
		assertNotNull(control);

		final var debugLog = path.resolve(app + "_debug.log");
		final var now = Instant.now();
		while (!Files.exists(debugLog) && Duration.between(now, Instant.now()).getSeconds() < 5L)
			IuException.unchecked(() -> Thread.sleep(250L));
		assertTrue(Files.exists(debugLog), debugLog + "\n" + control + "\n" + OUT);

		try (final var debug = Files.newBufferedReader(debugLog)) {
			assertEquals(control, IuStream.read(debug).toString(), path.toString());
		}
		try (final var info = Files.newBufferedReader(path.resolve(app + "_info.log"))) {
			assertEquals(control, IuStream.read(info).toString(), path.toString());
		}
	}

	@Test
	public void testFilePublisherKeys() {
		final Queue<FilePublisherKey> keys = new ArrayDeque<>();
		for (final var endpoint : new String[] { IdGenerator.generateId(), IdGenerator.generateId() })
			for (final var application : new String[] { IdGenerator.generateId(), IdGenerator.generateId() })
				for (final var environment : new String[] { IdGenerator.generateId(), IdGenerator.generateId() })
					keys.offer(new FilePublisherKey(endpoint, application, environment));
		for (final var a : keys)
			for (final var b : keys)
				if (a == b) {
					assertNotEquals(a, new Object());
					assertEquals(a, b);
					assertEquals(a.hashCode(), b.hashCode());
				} else {
					assertNotEquals(a, b);
					assertNotEquals(b, a);
					assertNotEquals(a.hashCode(), b.hashCode());
				}
	}

	@Test
	public void testResolvePath() {
		final var root = Path.of(IdGenerator.generateId());
		final var rel = IdGenerator.generateId();
		assertEquals(root.resolve("test___" + rel), IuLogHandler.resolvePath(root, null, "test://" + rel));
	}

}
