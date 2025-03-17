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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

@SuppressWarnings("javadoc")
public class LoggingTest {

	private static final Logger LOG = Logger.getLogger(LoggingTest.class.getName());
	
	@BeforeAll
	public static void setupClass() {
		LOG.fine("covers logging outside of a test case");
	}

	@Test
	public void testStandardPlatformLoggers() {
		assertTrue(IuTestLogger.isPlatformLogger("java."));
		assertTrue(IuTestLogger.isPlatformLogger("org.junit."));
		assertTrue(IuTestLogger.isPlatformLogger("org.mockito."));
		assertTrue(IuTestLogger.isPlatformLogger("org.apiguardian."));
		assertTrue(IuTestLogger.isPlatformLogger("org.objenesis."));
		assertTrue(IuTestLogger.isPlatformLogger("org.opentest4j."));
		assertTrue(IuTestLogger.isPlatformLogger("net.bytebuddy."));
		assertFalse(IuTestLogger.isPlatformLogger("edu.iu."));
		Logger.getLogger("java.test.platformlogger").info("Should be logged on console and not cause the test to fail");
	}

	@Test
	public void testCustomPlatformLoggers() {
		try (var mockIuTest = mockStatic(IuTest.class, CALLS_REAL_METHODS)) {
			mockIuTest.when(() -> IuTest.getProperty("iu.util.test.platformLoggers"))
					.thenReturn("custom.platform.a,custom.platform.b.");
			assertTrue(IuTestLogger.isPlatformLogger("java.")); // doesn't supersede standard list
			assertFalse(IuTestLogger.isPlatformLogger("edu.iu.")); // still not a platform logger
			assertTrue(IuTestLogger.isPlatformLogger("custom.platform.a."));
			assertTrue(IuTestLogger.isPlatformLogger("custom.platform.b."));
			assertTrue(IuTestLogger.isPlatformLogger("custom.platform.a"));
			assertTrue(IuTestLogger.isPlatformLogger("custom.platform.b"));
			assertFalse(IuTestLogger.isPlatformLogger("custom.platform.ab"));
			Logger.getLogger("custom.platform.b.platformlogger")
					.info("Should be logged on console and not cause the test to fail");
		}
	}

	@Test
	public void testLoggingFailsWithoutExpectedMessage() {
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class, () -> LOG.finest(() -> "not expected")));
	}

	@Test
	public void testLoggingSucceedsWithAnExpectedMessage() {
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINER, "expected");
		LOG.finer(() -> "expected");
	}

	@Test
	public void testLoggingSucceedsWithAnExpectedMessageAndException() {
		class ExpectedException extends Exception {
			private static final long serialVersionUID = 1L;
		}
		IuTestLogger.expect(LoggingTest.class.getName(), Level.CONFIG, "expected", ExpectedException.class);
		LOG.log(Level.CONFIG, new ExpectedException(), () -> "expected");
	}

	@Test
	public void testLoggingSucceedsWithAnExpectedMessageAndExceptionWithTest() {
		class ExpectedException extends Exception {
			private static final long serialVersionUID = 1L;

			private ExpectedException(String message) {
				super(message);
			}
		}
		IuTestLogger.expect(LoggingTest.class.getName(), Level.CONFIG, "expected", ExpectedException.class,
				e -> e.getMessage().equals("exception message"));
		LOG.log(Level.CONFIG, new ExpectedException("exception message"), () -> "expected");
	}

	@Test
	public void testFailsUnlessExpectedMessageIsLogged() {
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINER, "expected");
		IuTestExtension.expectFailure();
	}

	@Test
	public void testExpectedMessageLoggerNameMismatch() {
		IuTestLogger.expect("wrong name", Level.FINER, "expected");
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class, () -> LOG.finer(() -> "expected")));
		Logger.getLogger("wrong name").finer(() -> "expected");
	}

	@Test
	public void testExpectedMessageLevelMismatch() {
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "expected");
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class, () -> LOG.finer(() -> "expected")));
		LOG.fine("expected");
	}

	@Test
	public void testExpectedMessageRequiresException() {
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "expected", Exception.class);
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class, () -> LOG.fine(() -> "expected")));
		LOG.log(Level.FINE, new Exception(), () -> "expected");
	}

	@Test
	public void testExpectedMessageRequiresNoException() {
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "expected");
		IuTestLogger.clearUnexpected(
				assertThrows(AssertionFailedError.class, () -> LOG.log(Level.FINE, new Exception(), () -> "expected")));
		LOG.fine(() -> "expected");
	}

	@Test
	public void testExpectedMessageRequiresSameExceptionClass() {
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "expected", Exception.class);
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class,
				() -> LOG.log(Level.FINE, new RuntimeException(), () -> "expected")));
		LOG.log(Level.FINE, new Exception(), () -> "expected");
	}

	@Test
	public void testExpectedMessageRequiresMatchingException() {
		final var e = new Exception();
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "expected", Exception.class, a -> a == e);
		IuTestLogger.clearUnexpected(
				assertThrows(AssertionFailedError.class, () -> LOG.log(Level.FINE, new Exception(), () -> "expected")));
		LOG.log(Level.FINE, e, () -> "expected");
	}

	@Test
	public void testAllowedMessagesDoesntExpect() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed");
	}

	@Test
	public void testAllowedAllAllowsAll() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER);
		LOG.finer("some message");
		LOG.finest("another message");
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class, () -> LOG.fine("level too high")));
	}

	@Test
	public void testAllowedMessagesAllowsMoreThanOnce() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed");
		LOG.finer("allowed");
		LOG.finer("allowed");
	}

	@Test
	public void testAllowedMessagesDoesntAllowDifferentLogger() {
		IuTestLogger.allow("wrong logger", Level.FINER, "allowed");
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class, () -> LOG.finer(() -> "allowed")));
	}

	@Test
	public void testAllowedMessagesDoesntAllowsOnlyFinerLevel() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed");
		LOG.finest(() -> "allowed");
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class, () -> LOG.fine(() -> "allowed")));
	}

	@Test
	public void testAllowedWithoutDoesntAllowsWithThrown() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed");
		LOG.log(Level.FINER, new Throwable(), () -> "allowed");
	}

	@Test
	public void testAllowedWithDoesntAllowsWithoutThrown() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed", Throwable.class);
		IuTestLogger.clearUnexpected(assertThrows(AssertionFailedError.class, () -> LOG.finer(() -> "allowed")));
	}

	@Test
	public void testAllowedWithDoesntAllowsWrongThrownClass() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed", Throwable.class);
		IuTestLogger.clearUnexpected(
				assertThrows(AssertionFailedError.class, () -> LOG.log(Level.FINER, new Error(), () -> "allowed")));
	}

	@Test
	public void testWithAllowsWithThrown() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed", Throwable.class);
		LOG.log(Level.FINER, new Throwable(), () -> "allowed");
	}

	@Test
	public void testWithAllowsWithThrownAndTest() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed", Throwable.class, t -> true);
		LOG.log(Level.FINER, new Throwable(), () -> "allowed");
	}

	@Test
	public void testWithDoesntAllowsWithSameThrownButFailedTest() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed", Throwable.class, t -> false);
		IuTestLogger.clearUnexpected(
				assertThrows(AssertionFailedError.class, () -> LOG.log(Level.FINER, new Throwable(), () -> "allowed")));
	}

	@Test
	public void testAllowedMessages() {
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "allowed");
		IuTestLogger.allow("testAllowedMessages", Level.INFO, "allowed info");
		IuTestLogger.allow("testAllowedMessages", Level.WARNING, "allowed warning", IllegalStateException.class,
				t -> t.getMessage().equals("a"));
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINER, "throw", IllegalArgumentException.class);
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "expected");
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINER, "threw", UnsupportedOperationException.class);
		IuTestLogger.expect("testAllowedMessages", Level.WARNING, "expected warning", IllegalStateException.class,
				t -> t.getMessage().equals("b"));
		LOG.finer("allowed");
		LOG.fine("expected");
		LOG.log(Level.FINER, "threw", new UnsupportedOperationException());

		final var log = Logger.getLogger("testAllowedMessages");
		log.info("allowed info");
		log.log(Level.WARNING, new IllegalStateException("b"), () -> "expected warning");
		log.log(Level.WARNING, new IllegalStateException("a"), () -> "allowed warning");

		LOG.finer("allowed");
	}

	@Test
	public void testDetectsUnexpected() throws InterruptedException {
		final var t = new Thread(() -> {
			try {
				LOG.info("unexpected");
			} catch (Throwable e) {
			}
		});
		t.start();
		t.join();
		IuTestExtension.expectFailure();
	}

	@Test
	public void testAssertExpected() {
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "expected");
		assertThrows(AssertionFailedError.class, () -> IuTestLogger.assertExpectedMessages());
		assertThrows(AssertionFailedError.class, () -> LOG.fine("expected"));
		assertThrows(AssertionFailedError.class, () -> IuTestLogger.assertExpectedMessages());

		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "expected");
		LOG.fine("expected");
		IuTestLogger.assertExpectedMessages();
	}
	
	@Test
	public void testPatternMatch() {
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "ex.e.te.");
		LOG.fine("expected");
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "ex{e.te.");
		assertThrows(AssertionFailedError.class, () -> LOG.fine("expected"));
		IuTestLogger.expect(LoggingTest.class.getName(), Level.FINE, "excepted");
		assertThrows(AssertionFailedError.class, () -> LOG.fine("expected"));
		assertThrows(AssertionFailedError.class, () -> IuTestLogger.assertExpectedMessages());
		IuTestLogger.allow(LoggingTest.class.getName(), Level.FINE, "ex{e.te.");
		assertThrows(AssertionFailedError.class, () -> LOG.fine("expected"));
		assertThrows(AssertionFailedError.class, () -> IuTestLogger.assertExpectedMessages());
	}

}
