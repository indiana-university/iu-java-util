/*
 * Copyright © 2026 Indiana University
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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import java.util.ServiceConfigurationError;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SuppressWarnings("javadoc")
@ExtendWith(IuTestListener.class)
public class IuListenerTest {

	@SuppressWarnings("unused")
	private static final Logger LOG = Logger.getLogger(IuListener.class.getName());

	final Queue<LogRecord> logRecords = new ArrayDeque<>();
	final Handler logHandler = new Handler() {
		{
			setLevel(Level.ALL);
		}

		@Override
		public void publish(LogRecord record) {
			logRecords.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
		}
	};

	@BeforeEach
	void setup() {
		logRecords.clear();
		final var log = LogManager.getLogManager().getLogger(IuListener.class.getName());
		log.setLevel(Level.WARNING);
		log.setUseParentHandlers(false);
		log.addHandler(logHandler);
	}

	@AfterEach
	void tearDown() {
		final var log = LogManager.getLogManager().getLogger(IuListener.class.getName());
		log.setLevel(Level.INFO);
		log.setUseParentHandlers(true);
		log.removeHandler(logHandler);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testObserveWithNoListeners() {
		final var event = mock(IuObservableEvent.class);
		when(IuTestListener.loader.iterator()).thenReturn((Iterator) IuIterable.empty().iterator());
		assertDoesNotThrow(() -> IuListener.observe(event));
		assertTrue(logRecords.isEmpty());
	}


	@Test
	public void testObserveWithServiceConfigurationError() {
		final var event = mock(IuObservableEvent.class);
		when(IuTestListener.loader.iterator()).thenThrow(ServiceConfigurationError.class);
		assertDoesNotThrow(() -> IuListener.observe(event));
		
		final var record = logRecords.poll();
		assertEquals(Level.WARNING, record.getLevel());
		assertTrue(record.getMessage().startsWith("event listener failure; "));
		assertInstanceOf(ServiceConfigurationError.class, record.getThrown());
	}

	@Test
	public void testObserveListenerReceivesEvent() throws Throwable {
		final var event = mock(IuObservableEvent.class);
		assertDoesNotThrow(() -> IuListener.observe(event));
		verify(IuTestListener.delegate).accept(event);
		assertTrue(logRecords.isEmpty());
	}

	@Test
	public void testObserveListenerFailureLogsAtWarning() throws Throwable {
		final var error = new RuntimeException("listener failure");
		doThrow(error).when(IuTestListener.delegate).accept(any());
		final var event = mock(IuObservableEvent.class);
		assertDoesNotThrow(() -> IuListener.observe(event));

		assertFalse(logRecords.isEmpty());
		final var record = logRecords.poll();
		assertEquals(Level.WARNING, record.getLevel());
		assertTrue(record.getMessage().startsWith("event listener failure; "));
		assertSame(error, record.getThrown());
	}

	@Test
	public void testObserveListenerFailureSuppressedWhenConfigNotLoggable() throws Throwable {
		final var log = LogManager.getLogManager().getLogger(IuListener.class.getName());
		log.setLevel(Level.OFF);

		final var error = new RuntimeException("listener failure");
		doThrow(error).when(IuTestListener.delegate).accept(any());

		final var event = mock(IuObservableEvent.class);
		assertDoesNotThrow(() -> IuListener.observe(event));
		assertTrue(logRecords.isEmpty());
	}

}
