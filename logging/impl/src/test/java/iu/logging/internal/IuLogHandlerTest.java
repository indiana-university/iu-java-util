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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import iu.logging.Bootstrap;

@SuppressWarnings("javadoc")
public class IuLogHandlerTest {

	private static ByteArrayOutputStream OUT = new ByteArrayOutputStream();
	private static ByteArrayOutputStream ERR = new ByteArrayOutputStream();

	static {
		mockStatic(Files.class).close();
		System.setOut(new PrintStream(OUT));
		System.setErr(new PrintStream(ERR));
	}

	@BeforeEach
	public void setup() {
		OUT.reset();
		ERR.reset();
	}

	@Test
	public void testPublishAndSubscribe() {
		final var msg = IdGenerator.generateId();
		final var context = mock(DefaultLogContext.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
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
		final var context = mock(DefaultLogContext.class);
		final Throwable error;
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
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
		final var context = mock(DefaultLogContext.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
			final Queue<String> control = new ArrayDeque<>();
			for (var i = 0; i < 2; i++) {
				final var msg = IdGenerator.generateId();
				control.offer(msg);
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
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

				assertArrayEquals(control.toArray(), collected.toArray());
			}
		} finally {
			System.getProperties().remove("iu.logging.maxEvents");
		}
	}

	@Test
	public void testPurgeEventTtlLimit() {
		System.setProperty("iu.logging.eventTtl", "PT1S");
		final var context = mock(DefaultLogContext.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
			final Queue<String> control = new ArrayDeque<>();
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(1000L));
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				control.offer(msg);
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(1000L));

			try (final var sub = logHandler.subscribe()) {
				final Queue<String> collected = new ArrayDeque<>();
				final var stream = sub.stream().spliterator();
				Spliterator<IuLogEvent> split;
				while ((split = stream.trySplit()) != null)
					split.forEachRemaining(a -> collected.add(a.getMessage()));

				assertArrayEquals(control.toArray(), collected.toArray());
			}
		} finally {
			System.getProperties().remove("iu.logging.eventTtl");
		}
	}

	@Test
	public void testPurgeNoLimits() {
		final var context = mock(DefaultLogContext.class);
		System.setProperty("iu.logging.consoleLevel", "INFO");
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
			final var outControl = new StringBuilder();
			final Queue<String> control = new ArrayDeque<>();
			for (var i = 0; i < 2; i++) {
				final var msg = IdGenerator.generateId();
				control.offer(msg);
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				control.offer(msg);
				logHandler.publish(new LogRecord(Level.FINE, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(200L));

			try (final var sub = logHandler.subscribe()) {
				final Queue<String> collected = new ArrayDeque<>();
				final var stream = sub.stream().spliterator();
				Spliterator<IuLogEvent> split;
				while ((split = stream.trySplit()) != null)
					split.forEachRemaining(a -> {
						if (a.getLevel().intValue() >= Level.INFO.intValue())
							outControl.append(a.format());
						collected.add(a.getMessage());
					});

				assertArrayEquals(control.toArray(), collected.toArray());
			}
			assertEquals(outControl.toString(), OUT.toString());
		} finally {
			System.getProperties().remove("iu.logging.consoleLevel");
		}
	}

}
