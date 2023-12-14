package iu.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
			try (var logEventFactory = mockStatic(LogEventFactory.class)) {
				logEventFactory.when(() -> LogEventFactory.getEnvironmentProperties())
						.thenReturn(new IuLoggingEnvironment() {
						});
				logEventFactory.when(() -> LogEventFactory.getCurrentContext()).thenCallRealMethod();
				logEventFactory.when(() -> LogEventFactory.createEvent(any(LogRecord.class))).thenCallRealMethod();
				logEventFactory.when(() -> LogEventFactory.bootstrap(any(ClassLoader.class))).thenCallRealMethod();
				LogEventFactory.bootstrap(IuLogHandlerTest.class.getClassLoader());
				Logger LOG = Logger.getLogger(IuLogHandlerTest.class.getName());
				System.err.println("before log calls");
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
					System.err.println("calling LOG.log(" + message.logLevel() + ", " + message.message() + ")");
					LOG.log(message.logLevel(), message.message());
				}
				System.err.println("after log calls");

				Level logLevel = LOG.getParent().getHandlers()[0].getLevel();
				System.err.println("LOG.getParent.getLevel(): " + logLevel);
				System.err.println("LOG handler[0] name: " + LOG.getParent().getHandlers()[0].getClass().getName());
				System.err.println("LOG handler[0] level: " + LOG.getParent().getHandlers()[0].getLevel());
				Iterable<IuLogEvent> events = IuLogHandler.getLogEvents();
				if (events != null) {
					int c = 0;
					for (IuLogEvent e : events) {
						c++;
					}
					System.err.println("IuLogHandler.getLogEvents() size: " + c);
				}

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
		MockedStatic<IuLogHandler> handler = mockStatic(IuLogHandler.class);
		handler.when(() -> IuLogHandler.severePurgeTime()).thenReturn(500L);
		handler.when(() -> IuLogHandler.infoPurgeTime()).thenReturn(100L);
		handler.when(() -> IuLogHandler.finePurgeTime()).thenReturn(10L);
		handler.when(() -> IuLogHandler.defaultPurgeTime()).thenReturn(2L);
		handler.when(() -> IuLogHandler.getLogEvents()).thenCallRealMethod();
		return handler;
	}

	/**
	 * Test purge timer.
	 */
	@Test
	public void testPurgeTimer() {
		try (var handler = mockIuLogHandler()) {
			try (var logEventFactory = mockStatic(LogEventFactory.class)) {
				logEventFactory.when(() -> LogEventFactory.getEnvironmentProperties())
						.thenReturn(new IuLoggingEnvironment() {
						});
				logEventFactory.when(() -> LogEventFactory.getCurrentContext()).thenCallRealMethod();
				logEventFactory.when(() -> LogEventFactory.createEvent(any(LogRecord.class))).thenCallRealMethod();
				logEventFactory.when(() -> LogEventFactory.bootstrap(any(ClassLoader.class))).thenCallRealMethod();
				LogEventFactory.bootstrap(IuLogHandlerTest.class.getClassLoader());
				Logger LOG = Logger.getLogger(IuLogHandlerTest.class.getName());
				System.err.println("before log calls");
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
					System.err.println("calling LOG.log(" + message.logLevel() + ", " + message.message() + ")");
					LOG.log(message.logLevel(), message.message());
				}
				System.err.println("after log calls");
				try {
					System.err.println("wait 1s for purge timer to start");
					Thread.sleep(1000);
					System.err.println("purge timer should have started");
					Thread.sleep(150);
					System.err.println("purge timer should have purged all but severe by now");
					Iterable<IuLogEvent> events = IuLogHandler.getLogEvents();
					if (events != null) {
						int c = 0;
						for (IuLogEvent e : events) {
							c++;
							if (e.getLevel().intValue() < Level.SEVERE.intValue()) {
								System.err.println("a NON-SEVERE log exists. " + e.getMessage());
							} else {
								System.err.println("found a SEVERE log. " + e.getMessage());
							}
						}
						System.err.println("IuLogHandler.getLogEvents() size: " + c);
					}
				} catch (InterruptedException e) {
					System.err.println("testPurgeTimer sleep was interrupted");
				}
			}
		}
	}
}
