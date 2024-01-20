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
package iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IuAsynchronousSubscription;
import edu.iu.IuException;
import edu.iu.logging.IuLogEvent;
import edu.iu.logging.IuLoggingEnvironment;

/**
 * Test class for testing logging.
 */
public class IuLogHandlerTest {

//	private static final Logger LOG;
//	static {
//		LogEventFactory.bootstrap(IuLogHandlerTest.class.getClassLoader());
//		LOG = Logger.getLogger(IuLogHandlerTest.class.getName());
//	}

	record LogMessage(Level logLevel, String message) {
	};

	/**
	 * Test all log levels. Not all are expected to log messages or be added to the
	 * queue.
	 */
	@Test
	public void testLogLevels() {
		try (var handler = mockIuLogHandler()) {
			try (var logEventFactory = mockStatic(LogEventFactory.class, CALLS_REAL_METHODS)) {
				logEventFactory.when(() -> LogEventFactory.getEnvironmentProperties())
						.thenReturn(new IuLoggingEnvironment() {
						});
				LogEventFactory.bootstrap(IuLogHandlerTest.class.getClassLoader());
				Logger LOG = Logger.getLogger(IuLogHandlerTest.class.getName());
				List<LogMessage> logMessages = Arrays.asList(new LogMessage[] {
						new LogMessage(Level.FINEST, "Test finest 1"), new LogMessage(Level.FINER, "Test finer 1"),
						new LogMessage(Level.FINE, "Test fine 1"), new LogMessage(Level.CONFIG, "Test config 1"),
						new LogMessage(Level.INFO, "Test info 1"), new LogMessage(Level.WARNING, "Test warning 1"),
						new LogMessage(Level.SEVERE, "Test severe 1"), new LogMessage(Level.FINEST, "Test finest 2"),
						new LogMessage(Level.FINER, "Test finer 2"), new LogMessage(Level.FINE, "Test fine 2"),
						new LogMessage(Level.CONFIG, "Test config 2"), new LogMessage(Level.INFO, "Test info 2"),
						new LogMessage(Level.WARNING, "Test warning 2"),
						new LogMessage(Level.SEVERE, "Test severe 2") });
				for (LogMessage message : logMessages) {
					LOG.log(message.logLevel(), message.message());
				}

				Level logLevel = LOG.getParent().getHandlers()[0].getLevel();
				Iterable<IuLogEvent> events = IuLogHandler.getLogEvents();
				List<String> messageList = new ArrayList<>();
				events.forEach(v -> messageList.add(v.getMessage()));
				for (LogMessage message : logMessages) {
					if (logLevel.intValue() <= message.logLevel().intValue())
						assertTrue(messageList.contains(message.message()), message.message() + " was not in events");
				}
			}
		}
	}

	private MockedStatic<IuLogHandler> mockIuLogHandler() {
		MockedStatic<IuLogHandler> handler = mockStatic(IuLogHandler.class, CALLS_REAL_METHODS);
		handler.when(() -> IuLogHandler.severePurgeTime()).thenReturn(500L);
		handler.when(() -> IuLogHandler.infoPurgeTime()).thenReturn(100L);
		handler.when(() -> IuLogHandler.finePurgeTime()).thenReturn(10L);
		handler.when(() -> IuLogHandler.defaultPurgeTime()).thenReturn(2L);
		handler.when(() -> IuLogHandler.defaultEventBufferSize()).thenReturn(5);
		return handler;
	}

	/**
	 * Test purge timer.
	 */
	@Test
	public void testPurgeTimer() {
		try (MockedStatic<IuLogHandler> handler = mockIuLogHandler()) {
			try (var logEventFactory = mockStatic(LogEventFactory.class, CALLS_REAL_METHODS)) {
				logEventFactory.when(() -> LogEventFactory.getEnvironmentProperties())
						.thenReturn(new IuLoggingEnvironment() {
						});
				logEventFactory.when(() -> LogEventFactory.getDefaultLogLevel()).thenReturn(Level.ALL);
				LogEventFactory.bootstrap(IuLogHandlerTest.class.getClassLoader());
				Logger LOG = Logger.getLogger(IuLogHandlerTest.class.getName());
				// wait and purge prior to adding more logs in case other tests have run
				IuException.unchecked(() -> Thread.sleep(IuLogHandler.severePurgeTime()));
				IuLogHandler.purgeByTime();
				List<LogMessage> logMessages = Arrays.asList(new LogMessage[] {
						new LogMessage(Level.FINEST, "Test finest 1"), new LogMessage(Level.FINER, "Test finer 1"),
						new LogMessage(Level.FINE, "Test fine 1"), new LogMessage(Level.CONFIG, "Test config 1"),
						new LogMessage(Level.INFO, "Test info 1"), new LogMessage(Level.WARNING, "Test warning 1"),
						new LogMessage(Level.SEVERE, "Test severe 1"), new LogMessage(Level.SEVERE, "Test severe 2"),
						new LogMessage(Level.WARNING, "Test warning 2"), new LogMessage(Level.INFO, "Test info 2"),
						new LogMessage(Level.CONFIG, "Test config 2"), new LogMessage(Level.FINE, "Test fine 2"),
						new LogMessage(Level.FINER, "Test finer 2"), new LogMessage(Level.FINEST, "Test finest 2") });
				for (LogMessage message : logMessages) {
					LOG.log(message.logLevel(), message.message());
				}
				try {
					// fake wait for purge time of FINE to pass
					Thread.sleep(IuLogHandler.finePurgeTime());
					IuLogHandler.purgeByTime();
					// fake wait for purge time of INFO to pass
					Thread.sleep(IuLogHandler.infoPurgeTime());
					IuLogHandler.purgeByTime();
					// purge should have purged all but severe by now
					Iterable<IuLogEvent> events = IuLogHandler.getLogEvents();
					if (events != null) {
						int c = 0;
						for (IuLogEvent e : events) {
							assertEquals(Level.SEVERE.intValue(), e.getLevel().intValue());
							c++;
						}
						assertEquals(2, c, "Incorrect expected number of severe log events.");
					}
					// wait for actual purge timer to run
					Thread.sleep(TimeUnit.SECONDS.toMillis(15L));
				} catch (InterruptedException e) {
					System.err.println("testPurgeTimer sleep was interrupted");
				}
			}
		}
	}

	/**
	 * Test flush.
	 */
	@Test
	public void testFlush() {
		new IuLogHandler().flush();
	}

	/**
	 * Test stream.
	 */
	@Test
	public void testStream() {
		try (var handler = mockIuLogHandler()) {
			try (var logEventFactory = mockStatic(LogEventFactory.class, CALLS_REAL_METHODS)) {
				logEventFactory.when(() -> LogEventFactory.getEnvironmentProperties())
						.thenReturn(new IuLoggingEnvironment() {
						});
				LogEventFactory.bootstrap(IuLogHandlerTest.class.getClassLoader());
				Logger LOG = Logger.getLogger(IuLogHandlerTest.class.getName());
				List<LogMessage> logMessages = Arrays.asList(new LogMessage[] {
						new LogMessage(Level.FINEST, "Test finest 1"), new LogMessage(Level.FINER, "Test finer 1"),
						new LogMessage(Level.FINE, "Test fine 1"), new LogMessage(Level.CONFIG, "Test config 1"),
						new LogMessage(Level.INFO, "Test info 1"), new LogMessage(Level.WARNING, "Test warning 1"),
						new LogMessage(Level.SEVERE, "Test severe 1"), new LogMessage(Level.FINEST, "Test finest 2"),
						new LogMessage(Level.FINER, "Test finer 2"), new LogMessage(Level.FINE, "Test fine 2"),
						new LogMessage(Level.CONFIG, "Test config 2"), new LogMessage(Level.INFO, "Test info 2"),
						new LogMessage(Level.WARNING, "Test warning 2"),
						new LogMessage(Level.SEVERE, "Test severe 2") });
				for (LogMessage message : logMessages) {
					LOG.log(message.logLevel(), message.message());
				}

				IuAsynchronousSubscription<IuLogEvent> events = IuLogHandler.subscribe();
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {
						events.close();
					}}, 1000L);
				
				Level logLevel = LOG.getParent().getHandlers()[0].getLevel();
				List<String> messageList = new ArrayList<>();
				events.stream().forEach(v -> messageList.add(v.getMessage()));
				for (LogMessage message : logMessages) {
					if (logLevel.intValue() <= message.logLevel().intValue())
						assertTrue(messageList.contains(message.message()), message.message() + " was not in events");
				}
			}
		}
	}

}
