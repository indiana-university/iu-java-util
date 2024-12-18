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
package iu.logging.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.logging.IuLogContext;
import edu.iu.logging.IuLogEvent;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuLoggingBootstrapIT {

	@Test
	public void testInit() {
		final var nodeId = assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostName());
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		IuTestLogger.expect("", Level.CONFIG,
				"IU Logging Bootstrap initialized IuLogHandler \\[logEvents=\\d+, maxEvents=100000, eventTtl=PT24H, purge=iu-java-logging-purge/\\d+, closed=false\\] DefaultLogContext \\[nodeId="
						+ nodeId + ", endpoint=" + endpoint + ", application=" + application + ", environment="
						+ environment + "\\]; context: "
						+ ClassLoader.getSystemClassLoader().toString().replace("$", "\\$"));
		IuTestLogger.allow("", Level.CONFIG, "Logging configuration updated from .*");

		final var header = IdGenerator.generateId();
		final var message = IdGenerator.generateId();
		final var context = mock(IuLogContext.class);

		final Deque<IuLogEvent> events = new ArrayDeque<>();
		final var bootstrap = assertDoesNotThrow(() -> new IuLoggingBootstrap(true));
		try (final var sub = IuLoggingBootstrap.subscribe()) {
			new Thread(() -> sub.forEach(events::push)).start();
			assertDoesNotThrow(() -> IuLoggingBootstrap.initializeContext(endpoint, application, environment));

			IuTestLogger.expect("iu.logging.internal.ProcessLogger", Level.INFO, "begin 1: " + header);
			IuTestLogger.expect(IuLoggingBootstrapIT.class.getName(), Level.INFO, message);
			IuTestLogger.expect("iu.logging.internal.ProcessLogger", Level.INFO,
					"complete 1: " + header + ".*" + message + ".*");

			assertDoesNotThrow(() -> IuLogContext.follow(context, header, () -> {
				Logger.getLogger(IuLoggingBootstrapIT.class.getName()).info(message);
				return null;
			}));

		} finally {
			assertDoesNotThrow(bootstrap::destroy);
		}
		assertTrue(events.size() == 4 || events.size() == 5, Integer.toString(events.size()));
		events.pop();
		final var event = events.pop();
		assertEquals(message, event.getMessage());
	}

	@Test
	public void testInitExecption() {
		final var error = new Exception();
		try (final var mockException = mockStatic(IuException.class, CALLS_REAL_METHODS)) {
			mockException.when(() -> IuException.checkedInvocation(any())).thenThrow(error);
			assertSame(error, assertThrows(IllegalStateException.class, IuLoggingBootstrap::new).getCause());
		}
	}

}
