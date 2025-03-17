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
package edu.iu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.opentest4j.AssertionFailedError;

import edu.iu.IuObject;

/**
 * Asserts that calls to {@link Logger#log(LogRecord)} must be expected by the
 * test case being evaluated, and that all expected log events <em>must</em>
 * occur in order.
 * 
 * <p>
 * This mechanism is tied in automatically when depending on
 * {@code iu-java-test} for unit testing. Any use of
 * {@link Logger#log(LogRecord)} will cause the test to fail unless the log
 * event is explicitly expected to be the next log event. Log event expectations
 * <em>require</em>:
 * </p>
 * <ul>
 * <li>Logger name to match exactly</li>
 * <li>Log level to match exactly</li>
 * <li>Log message to match a {@link Pattern regular expression}</li>
 * <li>Thrown exception class to match exactly, or for an exception to not be
 * thrown</li>
 * <li>Thrown exception to meet additional criteria if defined by
 * {@link Predicate}</li>
 * </ul>
 * 
 * <p>
 * <strong>Platform-level</strong> loggers are exempt, and will be logged
 * normally as configured by {@link java.logging} before the test framework is
 * configured, i.e., INFO level and higher logged to console. The following
 * logger names are considered platform loggers.
 * </p>
 * <ul>
 * <li>org.junit</li>
 * <li>org.mockito</li>
 * <li>org.apiguardian</li>
 * <li>net.bytebuddy</li>
 * <li>org.objenesis</li>
 * <li>org.opentest4j</li>
 * <li>Any logger name for which {@link IuObject#isPlatformName(String)} returns
 * true</li>
 * <li>Any logger name prefixed by the comma-separated
 * {@link IuTest#getProperty(String) test property value}
 * {@code iu.util.test.platformLoggers}</li>
 * </ul>
 */
public final class IuTestLogger {

	private static class LogRecordMatcher<T extends Throwable> {
		private final String loggerName;
		private final Level level;
		private final Class<T> thrownClass;
		private final Predicate<T> thrownTest;
		private final String pattern;

		private LogRecordMatcher(String loggerName, Level level, Class<T> thrownClass, Predicate<T> thrownTest,
				String pattern) {
			this.loggerName = loggerName;
			this.level = level;
			this.thrownClass = thrownClass;
			this.thrownTest = thrownTest;
			this.pattern = pattern;
		}

		private boolean isExpected(LogRecord record) {
			if (!loggerName.equals(record.getLoggerName()))
				return false;
			if (level.intValue() != record.getLevel().intValue())
				return false;

			var thrown = record.getThrown();
			if (thrownClass == null) {
				if (thrown != null)
					return false;
			} else if (thrown == null)
				return false;
			else {
				if (thrownClass != thrown.getClass())
					return false;

				if (thrownTest != null && !thrownTest.test(thrownClass.cast(thrown)))
					return false;
			}

			var message = record.getMessage();
			return pattern.equals(message)
					|| Pattern.compile(pattern, Pattern.MULTILINE | Pattern.DOTALL).matcher(message).matches();
		}

		private boolean isAllowed(LogRecord record) {
			if (!record.getLoggerName().startsWith(loggerName))
				return false;
			if (level.intValue() < record.getLevel().intValue())
				return false;

			if (thrownClass != null) {
				var thrown = record.getThrown();
				if (thrown == null //
						|| thrownClass != thrown.getClass())
					return false;

				if (thrownTest != null && !thrownTest.test(thrownClass.cast(thrown)))
					return false;
			}

			var message = record.getMessage();
			return pattern.equals(message)
					|| Pattern.compile(pattern, Pattern.MULTILINE | Pattern.DOTALL).matcher(message).matches();
		}

		@Override
		public String toString() {
			return "LogRecordMatcher [loggerName=" + loggerName + ", level=" + level + ", thrownClass=" + thrownClass
					+ ", pattern=" + pattern + "]";
		}

	}

	private static class IuTestLogHandler extends Handler {
		private String activeTest;
		private Queue<LogRecordMatcher<?>> expectedMessages = new ConcurrentLinkedQueue<>();
		private Queue<LogRecordMatcher<?>> allowedMessages = new ConcurrentLinkedQueue<>();
		private Queue<Throwable> unexpectedMessages = new ConcurrentLinkedQueue<>();

		private void reset(String activeTest) {
			this.activeTest = activeTest;
			expectedMessages.clear();
			allowedMessages.clear();
		}

		private void assertExpectedMessages() {
			StringBuilder sb = new StringBuilder();
			Queue<Throwable> suppressed = new ArrayDeque<>();
			if (!expectedMessages.isEmpty()) {
				sb.append("Not all expected log messages were logged\n");
				while (!expectedMessages.isEmpty())
					sb.append(expectedMessages.poll()).append('\n');
			}
			if (!unexpectedMessages.isEmpty()) {
				sb.append("Unexpected messages were logged");
				while (!unexpectedMessages.isEmpty())
					suppressed.add(unexpectedMessages.poll());
			}
			if (sb.length() > 0) {
				final var e = new AssertionFailedError(sb.toString());
				suppressed.forEach(e::addSuppressed);
				throw e;
			}
		}

		@Override
		public void publish(LogRecord record) {
			var loggerName = record.getLoggerName();
			if (isPlatformLogger(loggerName)) {
				for (var handler : originalRootHandlers)
					handler.publish(record);
				return;
			}

			if (activeTest == null)
				return;

			final Queue<PatternSyntaxException> regexErrors = new ArrayDeque<>();
			for (var allowedMessage : allowedMessages)
				try {
					if (allowedMessage.isAllowed(record))
						return;
				} catch (PatternSyntaxException e) {
					regexErrors.add(e);
				}

			final var expectedIterator = expectedMessages.iterator();

			while (expectedIterator.hasNext())
				try {
					if (expectedIterator.next().isExpected(record)) {
						expectedIterator.remove();
						return;
					}
				} catch (PatternSyntaxException e) {
					regexErrors.add(e);
				}

			final var unexpected = new AssertionFailedError("Unexpected log message " + record.getLevel() + " "
					+ record.getLoggerName() + " " + record.getMessage(), record.getThrown());
			regexErrors.forEach(unexpected::addSuppressed);
			unexpectedMessages.add(unexpected);

			throw unexpected;
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
			reset(null);
		}
	}

	private static Handler[] originalRootHandlers;
	private static IuTestLogHandler testHandler;
	private static Level originalLevel;
	private static Logger root;
	private static Set<String> propertyDefinedPlatformLoggers;

	/**
	 * Determines if a logger name is related to a platform logger, and so should
	 * omitted from test expectations.
	 * 
	 * @param loggerName logger name
	 * @return true if name is associated with a platform logger
	 */
	static boolean isPlatformLogger(String loggerName) {
		if (loggerName.startsWith("org.junit.") //
				|| loggerName.startsWith("org.mockito.") //
				|| loggerName.startsWith("org.apiguardian.") //
				|| loggerName.startsWith("net.bytebuddy.") //
				|| loggerName.startsWith("org.objenesis.") //
				|| loggerName.startsWith("org.opentest4j."))
			return true;

		if (IuObject.isPlatformName(loggerName))
			return true;

		if (propertyDefinedPlatformLoggers == null) {
			var propertyDefinedPlatformLoggerNames = IuTest.getProperty("iu.util.test.platformLoggers");
			if (propertyDefinedPlatformLoggerNames == null)
				propertyDefinedPlatformLoggers = Collections.emptySet();
			else {
				Set<String> propertyDefinedPlatformLoggers = new HashSet<>();
				for (var propertyDefinedPlatformLogger : List.of(propertyDefinedPlatformLoggerNames.split(","))) {
					if (propertyDefinedPlatformLogger.charAt(propertyDefinedPlatformLogger.length() - 1) == '.')
						propertyDefinedPlatformLogger = propertyDefinedPlatformLogger.substring(0,
								propertyDefinedPlatformLogger.length() - 1);
					propertyDefinedPlatformLoggers.add(propertyDefinedPlatformLogger);
				}
				IuTestLogger.propertyDefinedPlatformLoggers = propertyDefinedPlatformLoggers;
			}
		}

		if (propertyDefinedPlatformLoggers.contains(loggerName))
			return true;

		for (var propertyDefinedPlatformLogger : propertyDefinedPlatformLoggers)
			if (loggerName.startsWith(propertyDefinedPlatformLogger + '.'))
				return true;

		return false;
	}

	/**
	 * Initialization hook.
	 */
	static void init() {
		root = LogManager.getLogManager().getLogger("");
		originalLevel = root.getLevel();
		originalRootHandlers = root.getHandlers();

		for (var rootHandler : originalRootHandlers)
			root.removeHandler(rootHandler);

		testHandler = new IuTestLogHandler();
		testHandler.setLevel(Level.ALL);
		root.addHandler(testHandler);
		root.setLevel(Level.ALL);
	}

	/**
	 * Test start hook.
	 * 
	 * @param name test name
	 */
	static void startTest(String name) {
		testHandler.activeTest = name;
	}

	/**
	 * Test finish hook.
	 * 
	 * @param name test name
	 */
	static void finishTest(String name) {
		try {
			assertEquals(testHandler.activeTest, name);
			testHandler.assertExpectedMessages();
		} finally {
			testHandler.reset(null);
			propertyDefinedPlatformLoggers = null;
		}
	}

	/**
	 * Test destroy hook.
	 */
	static void destroy() {
		testHandler.flush();
		testHandler.close();
		root.removeHandler(testHandler);

		for (var handler : originalRootHandlers)
			root.addHandler(handler);

		root.setLevel(originalLevel);
	}

	/**
	 * Allows a log messages from a logger.
	 * 
	 * <p>
	 * Messages <em>may</em> be logged zero or more times, and will be exempt from
	 * expectation checks.
	 * </p>
	 * 
	 * @param loggerName Logger name prefix
	 * @param level      maximum log level to allow
	 */
	public static void allow(String loggerName, Level level) {
		assertNotNull(testHandler.activeTest);
		testHandler.allowedMessages.offer(new LogRecordMatcher<>(loggerName, level, null, null, ".*"));
	}

	/**
	 * Allows a log message with or without an exception.
	 * 
	 * <p>
	 * The message <em>may</em> be logged zero or more times, and will be exempt
	 * from expectation checks.
	 * </p>
	 * 
	 * @param loggerName Logger name prefix
	 * @param level      maximum log level to allow
	 * @param message    regular expression to match against the message
	 */
	public static void allow(String loggerName, Level level, String message) {
		assertNotNull(testHandler.activeTest);
		testHandler.allowedMessages.offer(new LogRecordMatcher<>(loggerName, level, null, null, message));
	}

	/**
	 * Allows a log message with a thrown exception.
	 * 
	 * <p>
	 * The message <em>may</em> be logged zero or more times, and will be exempt
	 * from expectation checks.
	 * </p>
	 * 
	 * @param loggerName  Logger name prefix
	 * @param level       maximum log level to allow
	 * @param message     regular expression to match against the message
	 * @param thrownClass Expected exception class, must match exactly
	 */
	public static void allow(String loggerName, Level level, String message, Class<? extends Throwable> thrownClass) {
		assertNotNull(testHandler.activeTest);
		testHandler.allowedMessages
				.offer(new LogRecordMatcher<>(loggerName, level, Objects.requireNonNull(thrownClass), null, message));
	}

	/**
	 * Allows a log message with a thrown exception.
	 * 
	 * <p>
	 * The message <em>may</em> be logged zero or more times, and will be exempt
	 * from expectation checks.
	 * </p>
	 * 
	 * @param <T>         Thrown exception type
	 * 
	 * @param loggerName  Logger name prefix
	 * @param level       maximum log level to allow
	 * @param message     regular expression to match against the message
	 * @param thrownClass Expected exception class, must match exactly
	 * @param thrownTest  Expected exception test
	 */
	public static <T extends Throwable> void allow(String loggerName, Level level, String message, Class<T> thrownClass,
			Predicate<T> thrownTest) {
		assertNotNull(testHandler.activeTest);
		testHandler.allowedMessages.offer(
				new LogRecordMatcher<>(loggerName, level, Objects.requireNonNull(thrownClass), thrownTest, message));
	}

	/**
	 * Expects a log message with no thrown exception.
	 * 
	 * @param loggerName Logger name, must match exactly
	 * @param level      level, must match exactly
	 * @param message    regular expression to match against the message
	 */
	public static void expect(String loggerName, Level level, String message) {
		assertNotNull(testHandler.activeTest);
		testHandler.expectedMessages.offer(new LogRecordMatcher<>(loggerName, level, null, null, message));
	}

	/**
	 * Expects a log message with a thrown exception.
	 * 
	 * @param loggerName  Logger name, must match exactly
	 * @param level       level, must match exactly
	 * @param message     regular expression to match against the message
	 * @param thrownClass Expected exception class, must match exactly
	 */
	public static void expect(String loggerName, Level level, String message, Class<? extends Throwable> thrownClass) {
		assertNotNull(testHandler.activeTest);
		testHandler.expectedMessages
				.offer(new LogRecordMatcher<>(loggerName, level, Objects.requireNonNull(thrownClass), null, message));
	}

	/**
	 * Expects a log message with a thrown exception.
	 * 
	 * @param <T>         Thrown exception type
	 * 
	 * @param loggerName  Logger name, must match exactly
	 * @param level       level, must match exactly
	 * @param message     regular expression to match against the message
	 * @param thrownClass Expected exception class, must match exactly
	 * @param thrownTest  Expected exception test
	 */
	public static <T extends Throwable> void expect(String loggerName, Level level, String message,
			Class<T> thrownClass, Predicate<T> thrownTest) {
		assertNotNull(testHandler.activeTest);
		testHandler.expectedMessages.offer(
				new LogRecordMatcher<>(loggerName, level, Objects.requireNonNull(thrownClass), thrownTest, message));
	}

	/**
	 * Asserts that all expected messages declared so far have been logged.
	 */
	public static void assertExpectedMessages() {
		assertNotNull(testHandler.activeTest);
		testHandler.assertExpectedMessages();
	}

	/**
	 * Clears an unexpected log message from the pending queue.
	 * 
	 * @param unexpected error related to an unexpected log message
	 */
	static void clearUnexpected(Throwable unexpected) {
		testHandler.unexpectedMessages.remove(unexpected);
	}

	private IuTestLogger() {
	}
}
