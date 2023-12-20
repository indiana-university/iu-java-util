package iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import edu.iu.logging.IuLoggingContext;
import edu.iu.logging.IuLoggingEnvironment;

/**
 * Test class for LoggingEnvironment.
 */
public class LogEventFactoryTest {

	private interface TestIuLoggingEnvironmentInterface {
		public void setMockDefaults(boolean mockDefaults);
	}

	private static class TestIuLoggingEnvironment implements IuLoggingEnvironment, TestIuLoggingEnvironmentInterface {
		private boolean mockDefaults;

		@Override
		public String getApplication() {
			return mockDefaults ? null : "Test Application";
		}

		@Override
		public String getComponent() {
			return mockDefaults ? null : "Test Component";
		}

		@Override
		public String getEnvironment() {
			return mockDefaults ? null : "Test Environment";
		}

		@Override
		public String getHostname() {
			return mockDefaults ? null : "Test Hostname";
		}

		@Override
		public RuntimeMode getMode() {
			return mockDefaults ? RuntimeMode.TEST : RuntimeMode.DEVELOPMENT;
		}

		@Override
		public String getModule() {
			return mockDefaults ? null : "Test Module";
		}

		@Override
		public String getNodeId() {
			return mockDefaults ? null : "Test Node Id";
		}

		public TestIuLoggingEnvironment(boolean mockDefaults) {
			this.mockDefaults = mockDefaults;
		}

		@Override
		public void setMockDefaults(boolean mockDefaults) {
			this.mockDefaults = mockDefaults;
		}
	}

//	private MockedStatic<ServiceLoader> mockConditionalEnvironmentProperties(boolean mockDefaults) {
//		MockedStatic<ServiceLoader> serviceLoader = mockStatic(ServiceLoader.class, Answers.RETURNS_DEEP_STUBS);
//		env.setMockDefaults(mockDefaults);
//		serviceLoader.when(() -> ServiceLoader.load(IuLoggingEnvironment.class).findFirst().get()).thenReturn(env);
//		return serviceLoader;
//	}

	/**
	 * Test LoggingEnvironment Application default method.
	 */
	@Test
	public void testEnvironmentDefaults() {
		try (var logEventFactory = mockStatic(LogEventFactory.class)) {
			logEventFactory.when(() -> LogEventFactory.getEnvironmentProperties())
					.thenReturn(new IuLoggingEnvironment() {
					});
			logEventFactory.when(() -> LogEventFactory.getApplication()).thenCallRealMethod();
			logEventFactory.when(() -> LogEventFactory.getComponent()).thenCallRealMethod();
			logEventFactory.when(() -> LogEventFactory.getEnvironment()).thenCallRealMethod();
			logEventFactory.when(() -> LogEventFactory.getModule()).thenCallRealMethod();
			logEventFactory.when(() -> LogEventFactory.getNodeId()).thenCallRealMethod();
			logEventFactory.when(() -> LogEventFactory.isDevelopment()).thenCallRealMethod();
			assertNull(LogEventFactory.getApplication());
			assertNull(LogEventFactory.getComponent());
			assertNull(LogEventFactory.getEnvironment());
			assertNull(LogEventFactory.getModule());
			assertNull(LogEventFactory.getNodeId());
			assertFalse(LogEventFactory.isDevelopment());
		}
	}

//	/**
//	 * Test LoggingEnvironment Component default method.
//	 */
//	@Test
//	public void testLoggingEnvironmentComponentDefault() {
//		try (var logEventFactory = mockStatic(LogEventFactory.class)) {
//			assertNull(LogEventFactory.getComponent());
//		}
//	}
//
//	/**
//	 * Test LoggingEnvironment Environment default method.
//	 */
//	@Test
//	public void testLoggingEnvironmentEnvironmentDefault() {
//		try (var logEventFactory = mockStatic(LogEventFactory.class)) {
//			assertNull(LogEventFactory.getEnvironment());
//		}
//	}
//
//	/**
//	 * Test LoggingEnvironment Module default method.
//	 */
//	@Test
//	public void testLoggingEnvironmentModuleDefault() {
//		try (var logEventFactory = mockStatic(LogEventFactory.class)) {
//			assertNull(LogEventFactory.getModule());
//		}
//	}
//
//	/**
//	 * Test LoggingEnvironment Node Id default method.
//	 */
//	@Test
//	public void testLoggingEnvironmentNodeIdDefault() {
//		try (var logEventFactory = mockStatic(LogEventFactory.class)) {
//			assertNull(LogEventFactory.getNodeId());
//		}
//	}
//
//	/**
//	 * Test LoggingEnvironment Is Development default method.
//	 */
//	@Test
//	public void testLoggingEnvironmentIsDevelopmentDefault() {
//		try (var logEventFactory = mockStatic(LogEventFactory.class)) {
//			assertFalse(LogEventFactory.isDevelopment());
//		}
//	}

	/**
	 * Test LoggingEnvironment Is Development default method.
	 */
	@Test
	public void testLoggingEnvironmentIsDevelopmentTrue() {
		try (var logEventFactory = mockStatic(LogEventFactory.class)) {
			logEventFactory.when(() -> LogEventFactory.getEnvironmentProperties())
					.thenReturn(new TestIuLoggingEnvironment(false));
			logEventFactory.when(() -> LogEventFactory.isDevelopment()).thenCallRealMethod();
			assertTrue(LogEventFactory.isDevelopment());
		}
	}

	/**
	 * Test bootstrap method when no root logger is found.
	 */
	@Test
	public void testBootstrapNoRootLogger() {
		try (var logManager = mockStatic(LogManager.class, Answers.RETURNS_DEEP_STUBS)) {
			logManager.when(() -> LogManager.getLogManager().getLogger("")).thenReturn(null);
			var e = assertThrows(ExceptionInInitializerError.class,
					() -> LogEventFactory.bootstrap(LogEventFactoryTest.class.getClassLoader()));
			assertEquals("No root logger found.", e.getMessage());
		}
	}

	/**
	 * Test bootstrap when no log handlers have been added yet.
	 */
	@Test
	public void testBootstrapNoLogHandlers() {
		try (var logManager = mockStatic(LogManager.class, Answers.RETURNS_DEEP_STUBS)) {
			Logger mockLogger = mock(Logger.class);
			when(mockLogger.getHandlers()).thenReturn(new Handler[0]);
			when(mockLogger.getLevel()).thenReturn(Level.ALL);
			when(mockLogger.getUseParentHandlers()).thenReturn(true);
			when(mockLogger.getFilter()).thenReturn(null);
			logManager.when(() -> LogManager.getLogManager().getLogger("")).thenReturn(mockLogger);
			LogEventFactory.bootstrap(LogEventFactoryTest.class.getClassLoader());
		}
	}

	/**
	 * Test bootstrap method.
	 */
	@Test
	public void testBootstrap() {
		LogEventFactory.bootstrap(LogEventFactoryTest.class.getClassLoader());
		Handler[] rootHandlers = Logger.getLogger("").getHandlers();
		assertNotNull(rootHandlers, "There should be a Handler array.");
		assertEquals(1, rootHandlers.length, "There should be one handler");
		assertTrue(rootHandlers[0] instanceof IuLogHandler, "The handler should be an IuLogHandler.");
	}

	/**
	 * Test Environment Properties already initialized.
	 */
	@Test
	public void testGetEnvironment() {
		LogEventFactory.getEnvironmentProperties();
		LogEventFactory.getEnvironmentProperties();
	}

	/**
	 * Test default value of static getCurrentContext method.
	 */
	@Test
	public void testGetCurrentContextDefault() {
		IuLoggingContext context = LogEventFactory.getCurrentContext();
		assertNull(context.getAuthenticatedPrincipal());
		assertNull(context.getCalledUrl());
		assertNull(context.getRemoteAddr());
		assertNull(context.getReqNum());
		assertNull(context.getUserPrincipal());
	}

	/**
	 * Test static bound method with default context
	 */
	@Test
	public void testBoundDefaultContext() {
		IuLoggingContext context = new IuLoggingContext() {

		};

		LogEventFactory.bound(context, () -> {
			IuLoggingContext contextInRunnable = LogEventFactory.getCurrentContext();
			assertNull(contextInRunnable.getAuthenticatedPrincipal());
			assertNull(contextInRunnable.getCalledUrl());
			assertNull(contextInRunnable.getRemoteAddr());
			assertNull(contextInRunnable.getReqNum());
			assertNull(contextInRunnable.getUserPrincipal());
		});
	}

	/**
	 * Test static bound method with overridden context methods.
	 */
	@Test
	public void testBoundOverriddenContext() {
		IuLoggingContext context = new IuLoggingContext() {
			@Override
			public String getAuthenticatedPrincipal() {
				return "Test Authenticated Principal";
			}

			@Override
			public String getCalledUrl() {
				return "Test Called URL";
			}

			@Override
			public String getRemoteAddr() {
				return "Test Remote Address";
			}

			@Override
			public String getReqNum() {
				return "Test Req Num";
			}

			@Override
			public String getUserPrincipal() {
				return "Test User Principal";
			}
		};

		LogEventFactory.bound(context, () -> {
			IuLoggingContext contextInRunnable = LogEventFactory.getCurrentContext();
			assertEquals("Test Authenticated Principal", contextInRunnable.getAuthenticatedPrincipal(),
					"Incorrect Bound Overridden Authenticated Princpal");
			assertEquals("Test Called URL", contextInRunnable.getCalledUrl(), "Incorrect Bound Overridden Called URL");
			assertEquals("Test Remote Address", contextInRunnable.getRemoteAddr(),
					"Incorrect Bound Overridden Remote Address");
			assertEquals("Test Req Num", contextInRunnable.getReqNum(), "Incorrect Bound Overridden Req Num");
			assertEquals("Test User Principal", contextInRunnable.getUserPrincipal(),
					"Incorrect Bound Overridden User Principal");
		});

		// Outside the bound method, the context should be default
		IuLoggingContext currentContext = LogEventFactory.getCurrentContext();
		assertNull(currentContext.getAuthenticatedPrincipal());
		assertNull(currentContext.getCalledUrl());
		assertNull(currentContext.getRemoteAddr());
		assertNull(currentContext.getReqNum());
		assertNull(currentContext.getUserPrincipal());
	}

	/**
	 * Test createEvent.
	 */
	@Test
	public void testCreateEvent() {
		try (var logEventFactory = mockStatic(LogEventFactory.class)) {
			logEventFactory.when(() -> LogEventFactory.getEnvironmentProperties())
					.thenReturn(new IuLoggingEnvironment() {
					});
			logEventFactory.when(() -> LogEventFactory.createEvent(any(LogRecord.class))).thenCallRealMethod();
			logEventFactory.when(() -> LogEventFactory.getCurrentContext()).thenCallRealMethod();
			LogRecord testRecord = new LogRecord(Level.FINE, "Test Message");
			testRecord.setInstant(Instant.EPOCH);
			testRecord.setLoggerName("Test Logger Name");
			testRecord.setSourceClassName("Test Source Class Name");
			testRecord.setSourceMethodName("Test Source Method Name");
			testRecord.setThrown(new Throwable("Test Thrown"));
			LogEvent event = LogEventFactory.createEvent(testRecord);

			assertNull(event.getApplication());
			assertNull(event.getAuthenticatedPrincipal());
			assertNull(event.getCalledUrl());
			assertNull(event.getComponent());
			assertNull(event.getEnvironment());
			assertNull(event.getHostname());
			assertEquals(Instant.EPOCH, event.getInstant(), "Incorrect Overridden Instant");
			assertEquals(Level.FINE, event.getLevel(), "Incorrect Overridden Level");
			assertEquals("Test Logger Name", event.getLoggerName(), "Incorrect Overridden Logger Name");
			assertEquals("Test Message", event.getMessage(), "Incorrect Overridden Message");
			assertNull(event.getMode());
			assertNull(event.getModule());
			assertNull(event.getNodeId());
			assertNull(event.getRemoteAddr());
			assertNull(event.getReqNum());
			assertNull(event.getRuntime());
			assertEquals("Test Source Class Name", event.getSourceClassName(),
					"Incorrect Overridden Source Class Name");
			assertEquals("Test Source Method Name", event.getSourceMethodName(),
					"Incorrect Overridden Source Method Name");
			assertEquals("main", event.getThread());
			assertEquals("java.lang.Throwable: Test Thrown", event.getThrown().split(System.lineSeparator(), 2)[0],
					"Incorrect Overridden Thrown");
			assertNull(event.getUserPrincipal());
		}
	}

}
