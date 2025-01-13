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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;
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

	final static IuLoggingBootstrap BOOT = assertDoesNotThrow(() -> new IuLoggingBootstrap(false));

	static {
		assertDoesNotThrow(() -> IuLogContext.initialize());
	}

	@Test
	public void testInit() throws IOException {
		IuTestLogger.allow("", Level.CONFIG);
		final var nodeId = IdGenerator.generateId();
		final var development = ThreadLocalRandom.current().nextBoolean();
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();

		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try (final var loader = new URLClassLoader(new URL[0])) {
			current.setContextClassLoader(loader);
			IuLogContext.initializeContext(nodeId, development, endpoint, application, environment, module, runtime,
					component);

			final var header = IdGenerator.generateId();
			final var message = IdGenerator.generateId();
			final var context = mock(IuLogContext.class);

			final Deque<IuLogEvent> events = new ArrayDeque<>();
			final var bootstrap = assertDoesNotThrow(() -> new IuLoggingBootstrap(true));
			try (final var sub = IuLoggingBootstrap.subscribe()) {
				new Thread(() -> sub.forEach(events::push)).start();

				assertDoesNotThrow(() -> IuLogContext.follow(context, header, () -> {
					Logger.getLogger(IuLoggingBootstrapIT.class.getName()).info(message);
					return null;
				}));

				final var error = new IllegalStateException();
				try (final var mockIuException = mockStatic(IuException.class)) {
					mockIuException.when(() -> IuException.suppress(any(), any())).thenReturn(error);
					mockIuException.when(() -> IuException.checked(any(Throwable.class))).thenReturn(error);
					assertSame(error, assertThrows(IllegalStateException.class, () -> bootstrap.destroy()));
				}

			} finally {
				assertDoesNotThrow(bootstrap::destroy);
			}
			assertEquals(3, events.size(), events::toString);
			events.pop();
			final var event = events.pop();
			System.out.println(event);
			assertEquals(message, event.getMessage());
		} finally {
			current.setContextClassLoader(restore);
		}
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
