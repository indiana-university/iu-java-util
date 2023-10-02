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
import java.util.Objects;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.opentest4j.AssertionFailedError;

/**
 * Asserts that calls to {@link Logger#log(LogRecord)} unrelated to JUnit must
 * be expected, in order, by the test case being evaluated.
 */
public final class IuTestLogger {

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
			assertEquals(loggerName, record.getLoggerName());
			assertEquals(level, record.getLevel());

			var thrown = record.getThrown();
			if (thrownClass == null)
				assertNull(thrown);
			else {
				assertSame(thrownClass, thrown.getClass());
				if (thrownTest != null)
					assertTrue(thrownTest.test(thrownClass.cast(thrown)), "Thrown exception mismatch " + this);
			}

			var message = record.getMessage();
			assertTrue(pattern.matcher(message).matches(), message + " doesn't match " + this);
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

	static boolean isPlatformLogger(String loggerName) {
		return loggerName.startsWith("org.junit.") || //
				loggerName.startsWith("org.mockito.") || //
				loggerName.startsWith("org.apache.") || //
				loggerName.startsWith("java.") || //
				loggerName.startsWith("javax.") || //
				loggerName.startsWith("jakarta.") || //
				loggerName.startsWith("jdk.") || //
				loggerName.startsWith("com.sun.") || //
				loggerName.startsWith("com.oracle.") || //
				loggerName.startsWith("sun.");
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
