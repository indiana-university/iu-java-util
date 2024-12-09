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
import java.util.Deque;
import java.util.Spliterator;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import iu.logging.Bootstrap;
import iu.logging.IuProcessLogger;

@SuppressWarnings("javadoc")
public class IuLogHandlerTest {

	private static ByteArrayOutputStream ERR = new ByteArrayOutputStream();
	static {
		mockStatic(Files.class).close();
		System.setErr(new PrintStream(ERR));
	}

	@BeforeEach
	public void setup() {
		ERR.reset();
	}

	@Test
	public void testPublishAndSubscribe() {
		final var msg = IdGenerator.generateId();
		final var context = mock(DefaultLogContext.class);
		try (final var mockProcessLogger = mockStatic(IuProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
			mockProcessLogger.when(() -> IuProcessLogger.trace(any())).then(a -> {
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
		try (final var mockProcessLogger = mockStatic(IuProcessLogger.class); //
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
		try (final var mockProcessLogger = mockStatic(IuProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
			final Deque<String> control = new ArrayDeque<>();
			for (var i = 0; i < 2; i++) {
				final var msg = IdGenerator.generateId();
				control.addFirst(msg);
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				if (i > 6)
					control.addFirst(msg);
				logHandler.publish(new LogRecord(Level.FINE, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(200L));

			try (final var sub = logHandler.subscribe()) {
				final Deque<String> collected = new ArrayDeque<>();
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
		try (final var mockProcessLogger = mockStatic(IuProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
			final Deque<String> control = new ArrayDeque<>();
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(1000L));
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				control.addFirst(msg);
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(1000L));

			try (final var sub = logHandler.subscribe()) {
				final Deque<String> collected = new ArrayDeque<>();
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
		try (final var mockProcessLogger = mockStatic(IuProcessLogger.class); //
				final var mockBootstrap = mockStatic(Bootstrap.class); //
				final var logHandler = new IuLogHandler()) {
			mockBootstrap.when(() -> Bootstrap.getDefaultContext()).thenReturn(context);
			final Deque<String> control = new ArrayDeque<>();
			for (var i = 0; i < 2; i++) {
				final var msg = IdGenerator.generateId();
				control.addFirst(msg);
				logHandler.publish(new LogRecord(Level.INFO, msg));
			}
			for (var i = 0; i < 10; i++) {
				final var msg = IdGenerator.generateId();
				control.addFirst(msg);
				logHandler.publish(new LogRecord(Level.FINE, msg));
			}
			assertDoesNotThrow(() -> Thread.sleep(200L));

			try (final var sub = logHandler.subscribe()) {
				final Deque<String> collected = new ArrayDeque<>();
				final var stream = sub.stream().spliterator();
				Spliterator<IuLogEvent> split;
				while ((split = stream.trySplit()) != null)
					split.forEachRemaining(a -> collected.add(a.getMessage()));

				assertArrayEquals(control.toArray(), collected.toArray());
			}
		} finally {
			System.getProperties().remove("iu.logging.consoleLevel");
		}
	}

}
