package iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IuException;
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
}
