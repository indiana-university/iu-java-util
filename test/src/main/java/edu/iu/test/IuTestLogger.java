/*
 * Copyright © 2023 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.opentest4j.AssertionFailedError;

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
 * <li>java</li>
 * <li>javax</li>
 * <li>jakarta</li>
 * <li>jdk</li>
 * <li>com.sun</li>
 * <li>com.oracle.</li>
 * <li>oracle</li>
 * <li>sun</li>
 * <li>Any logger name prefixed by the comma-separated
 * {@link IuTest#getProperty(String) test property value}
 * {@code iu.util.test.platformLoggers}</li>
 * </ul>
 */
public final class IuTestLogger {

	private static final Set<String> STANDARD_PLATFORM_LOGGER_NAMES = Set.of( //
			"org.junit", "org.mockito", "java", "javax", "jakarta", "jdk", "com.sun", "com.oracle", "oracle", "sun");

	private static class LogRecordMatcher<T extends Throwable> {
		private final String loggerName;
		private final Level level;
		private final Class<T> thrownClass;
		private final Predicate<T> thrownTest;
		private final Pattern pattern;

		private LogRecordMatcher(String loggerName, Level level, Class<T> thrownClass, Predicate<T> thrownTest,
				Pattern pattern) {
			this.loggerName = loggerName;
			this.level = level;
			this.thrownClass = thrownClass;
			this.thrownTest = thrownTest;
			this.pattern = pattern;
		}

		private void assertMatches(LogRecord record) {
			var thrown = record.getThrown();
			try {
				assertEquals(loggerName, record.getLoggerName());
				assertEquals(level, record.getLevel());

				if (thrownClass == null)
					assertNull(thrown);
				else {
					assertSame(thrownClass, thrown.getClass());
					if (thrownTest != null)
						assertTrue(thrownTest.test(thrownClass.cast(thrown)), "Thrown exception mismatch " + this);
				}

				var message = record.getMessage();
				assertTrue(pattern.matcher(message).matches(), message + " doesn't match " + this);
			} catch (AssertionFailedError e) {
				if (thrown != null)
					e.addSuppressed(thrown);
				throw e;
			}
		}

		@Override
		public String toString() {
			return "LogRecordMatcher [loggerName=" + loggerName + ", level=" + level + ", thrownClass=" + thrownClass
					+ ", pattern=" + pattern + "]";
		}

	}

	private static class IuTestLogHandler extends Handler {
		private String activeTest;
		private Queue<LogRecordMatcher<?>> expectedMessages = new ArrayDeque<>();

		@Override
		public void publish(LogRecord record) {
			var loggerName = record.getLoggerName();
			if (isPlatformLogger(loggerName)) {
				for (var handler : originalRootHandlers)
					handler.publish(record);
				return;
			}

			if (expectedMessages.isEmpty())
				throw new AssertionFailedError("Unexpected log message " + record.getLevel() + " "
						+ record.getLoggerName() + " " + record.getMessage(), record.getThrown());
			else
				expectedMessages.poll().assertMatches(record);

		}

		private void assertExpectedMessages() {
			if (!expectedMessages.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				sb.append("Not all expected log messages were logged\n");
				while (!expectedMessages.isEmpty())
					sb.append(expectedMessages.poll());
				throw new AssertionFailedError(sb.toString());
			}
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
		}
	}

	private static Handler[] originalRootHandlers;
	private static IuTestLogHandler testHandler;
	private static Level originalLevel;
	private static Logger root;
	private static Set<String> propertyDefinedPlatformLoggers;

	static boolean isPlatformLogger(String loggerName) {
		if (STANDARD_PLATFORM_LOGGER_NAMES.contains(loggerName))
			return true;

		for (var standardPlatformLoggerName : STANDARD_PLATFORM_LOGGER_NAMES)
			if (loggerName.startsWith(standardPlatformLoggerName + '.'))
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

	static void startTest(String name) {
		assertNull(testHandler.activeTest);
		testHandler.activeTest = name;
	}

	static void finishTest(String name) {
		assertTrue(testHandler.activeTest.equals(name));
		testHandler.activeTest = null;
		testHandler.assertExpectedMessages();
		propertyDefinedPlatformLoggers = null;
	}

	static void destroy() {
		testHandler.flush();
		testHandler.close();
		root.removeHandler(testHandler);

		for (var handler : originalRootHandlers)
			root.addHandler(handler);

		root.setLevel(originalLevel);
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
		testHandler.expectedMessages
				.offer(new LogRecordMatcher<>(loggerName, level, null, null, Pattern.compile(message)));
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
		testHandler.expectedMessages.offer(new LogRecordMatcher<>(loggerName, level,
				Objects.requireNonNull(thrownClass), null, Pattern.compile(message)));
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
		testHandler.expectedMessages.offer(new LogRecordMatcher<>(loggerName, level,
				Objects.requireNonNull(thrownClass), thrownTest, Pattern.compile(message)));
	}

	private IuTestLogger() {
	}
}
