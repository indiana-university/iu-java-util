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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

@SuppressWarnings("javadoc")
public class LoggingTest {

	private static final Logger LOG = Logger.getLogger(LoggingTest.class.getName());

	@Test
	public void testCoversPlatformLoggers() {
		// TODO: consider moving list to iu-test.properties
		assertTrue(IuTestLogger.isPlatformLogger("org.junit."));
		assertTrue(IuTestLogger.isPlatformLogger("org.mockito."));
		assertTrue(IuTestLogger.isPlatformLogger("org.apache."));
		assertTrue(IuTestLogger.isPlatformLogger("java."));
		assertTrue(IuTestLogger.isPlatformLogger("javax."));
		assertTrue(IuTestLogger.isPlatformLogger("jakarta."));
		assertTrue(IuTestLogger.isPlatformLogger("jdk."));
		assertTrue(IuTestLogger.isPlatformLogger("com.sun."));
		assertTrue(IuTestLogger.isPlatformLogger("com.oracle."));
		assertTrue(IuTestLogger.isPlatformLogger("sun."));
		Logger.getLogger("java.test.platformlogger").info("Should be logged on console and not cause the test to fail");
	}

	@Test
	public void testLoggingFailsWithoutExpectedMessage() {
		assertThrows(AssertionFailedError.class, () -> LOG.finest(() -> "not expected"));
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

}
