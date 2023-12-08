package iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.time.Instant;
import java.util.ServiceLoader;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import edu.iu.logging.IuLoggingContext;
import edu.iu.logging.IuLoggingEnvironment;
import edu.iu.logging.IuLoggingEnvironment.RuntimeMode;
import iu.logging.IuLogHandler;
import iu.logging.LogEventFactory;

/**
 * Test class for LoggingEnvironment.
 */
public class LogEventFactoryTest {

	private static final Logger LOG;
	private static final TestIuLoggingEnvironment env = new TestIuLoggingEnvironment(true);
	static {
		LogEventFactory.bootstrap(LogEventFactoryTest.class.getClassLoader());
		LOG = Logger.getLogger(LogEventFactoryTest.class.getName());
	}

	private interface TestIuLoggingEnvironmentInterface {
		public void setMockDefaults(boolean mockDefaults);
	}
	private static class TestIuLoggingEnvironment implements IuLoggingEnvironment, TestIuLoggingEnvironmentInterface {
		private boolean mockDefaults;

		@Override
		public String getApplication() {
			LOG.warning("TestIuLoggingEnvironment" + this.hashCode() + " mockDefaults: " + mockDefaults
					+ " getApplication() returning: " + (mockDefaults ? null : "Test Application"));
			return mockDefaults ? null : "Test Application";
		}

		@Override
		public String getComponent() {
			LOG.warning("TestIuLoggingEnvironment" + this.hashCode() + " mockDefaults: " + mockDefaults
					+ " getComponent() returning: " + (mockDefaults ? null : "Test Component"));
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
			LOG.warning("TestIuLoggingEnvironment" + this.hashCode() + " mockDefaults: " + mockDefaults
					+ " getMode() returning: " + (mockDefaults ? RuntimeMode.TEST : RuntimeMode.DEVELOPMENT));
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

	private MockedStatic<ServiceLoader> mockConditionalEnvironmentProperties(boolean mockDefaults) {
		MockedStatic<ServiceLoader> serviceLoader = mockStatic(ServiceLoader.class, Answers.RETURNS_DEEP_STUBS);
		LOG.warning("begin mockConditionalEnvironmentProperties serviceLoader:" + serviceLoader
				+ serviceLoader.hashCode() + " mockDefaults: " + mockDefaults);
		env.setMockDefaults(mockDefaults);
//		IuLoggingEnvironment env1 = null;
//		IuLoggingEnvironment env = new TestIuLoggingEnvironment(mockDefaults);
//		LOG.warning("mockConditionalEnvironmentProperties. serviceLoader should return " + env + env.hashCode());
//
		serviceLoader.when(() -> ServiceLoader.load(IuLoggingEnvironment.class).findFirst().get()).thenReturn(env);
//		thenAnswer(invocation -> {
//			LOG.warning("serviceLoader:" + serviceLoader + serviceLoader.hashCode() + " mockDefaults: " + mockDefaults + " returning loader: " + (mockDefaults ? "" + env1 : "" + env2 + env2.hashCode()));
//			return mockDefaults ? env1 : env2;
//		});
		LOG.warning(
				"end mockConditionalEnvironmentProperties serviceLoader:" + serviceLoader + serviceLoader.hashCode());
		IuLoggingEnvironment mockReturn = ServiceLoader.load(IuLoggingEnvironment.class).findFirst().get();
		LOG.warning("serviceLoader:" + serviceLoader + serviceLoader.hashCode() + " will return " + mockReturn
				+ mockReturn.hashCode() + " "+ mockReturn.getClass().getName());
		return serviceLoader;
	}

	/**
	 * Test LoggingEnvironment Application default method.
	 */
	@Test
	public void TestLoggingEnvironmentApplicationDefault() {
		try (var serviceLoader = mockConditionalEnvironmentProperties(true)) {
			LOG.warning(
					"TestLoggingEnvironmentApplicationDefault about to call LoggingEnvironment.getApplication() serviceLoader:"
							+ serviceLoader + serviceLoader.hashCode());
			IuLoggingEnvironment mockReturn = ServiceLoader.load(IuLoggingEnvironment.class).findFirst().get();
			LOG.warning("TestLoggingEnvironmentApplicationDefault serviceLoader:" + serviceLoader + serviceLoader.hashCode() + " will return " + mockReturn
					+ mockReturn.hashCode());
			assertNull(LogEventFactory.getApplication());
		}
	}

	/**
	 * Test LoggingEnvironment Component default method.
	 */
	@Test
	public void TestLoggingEnvironmentComponentDefault() {
		try (var serviceLoader = mockConditionalEnvironmentProperties(true)) {
			LOG.warning(
					"TestLoggingEnvironmentComponentDefault about to call LoggingEnvironment.getComponent() serviceLoader:"
							+ serviceLoader + serviceLoader.hashCode());
			IuLoggingEnvironment mockReturn = ServiceLoader.load(IuLoggingEnvironment.class).findFirst().get();
			LOG.warning("TestLoggingEnvironmentComponentDefault serviceLoader:" + serviceLoader + serviceLoader.hashCode() + " will return " + mockReturn
					+ mockReturn.hashCode());
			assertNull(LogEventFactory.getComponent());
		}
	}

	/**
	 * Test LoggingEnvironment Environment default method.
	 */
	@Test
//	@Disabled
	public void TestLoggingEnvironmentEnvironmentDefault() {
		try (var serviceLoader = mockConditionalEnvironmentProperties(true)) {
//			mockConditionalEnvironmentProperties(serviceLoader, true);
			assertNull(LogEventFactory.getEnvironment());
//			serviceLoader.close();
		}
	}

	/**
	 * Test LoggingEnvironment Module default method.
	 */
	@Test
//	@Disabled
	public void TestLoggingEnvironmentModuleDefault() {
		try (var serviceLoader = mockConditionalEnvironmentProperties(true)) {
//			mockConditionalEnvironmentProperties(serviceLoader, true);
//			LoggingEnvironment.getModule();
			assertNull(LogEventFactory.getModule());
//			serviceLoader.close();
		}
	}

	/**
	 * Test LoggingEnvironment Node Id default method.
	 */
	@Test
//	@Disabled
	public void TestLoggingEnvironmentNodeIdDefault() {
		try (var serviceLoader = mockConditionalEnvironmentProperties(true)) {
//			mockConditionalEnvironmentProperties(serviceLoader, true);
//			LoggingEnvironment.getNodeId();
			assertNull(LogEventFactory.getNodeId());
//			serviceLoader.close();
		}
	}

	/**
	 * Test LoggingEnvironment Is Development default method.
	 */
	@Test
//	@Disabled
	public void TestLoggingEnvironmentIsDevelopmentDefault() {
		try (var serviceLoader = mockConditionalEnvironmentProperties(true)) {
			LOG.warning(
					"TestLoggingEnvironmentIsDevelopmentDefault about to call LoggingEnvironment.isDevelopment() serviceLoader:"
							+ serviceLoader + serviceLoader.hashCode());
			IuLoggingEnvironment mockReturn = ServiceLoader.load(IuLoggingEnvironment.class).findFirst().get();
			LOG.warning("TestLoggingEnvironmentIsDevelopmentDefault serviceLoader:" + serviceLoader + serviceLoader.hashCode() + " will return " + mockReturn
					+ mockReturn.hashCode());
			assertFalse(LogEventFactory.isDevelopment());
		}
	}

	/**
	 * Test LoggingEnvironment Is Development default method.
	 */
	@Test
//	@Disabled
	public void TestLoggingEnvironmentIsDevelopmentTrue() {
		try (var serviceLoader = mockConditionalEnvironmentProperties(false)) {
			LOG.warning(
					"TestLoggingEnvironmentIsDevelopmentTrue about to call LoggingEnvironment.isDevelopment() serviceLoader:"
							+ serviceLoader + serviceLoader.hashCode());
			IuLoggingEnvironment mockReturn = ServiceLoader.load(IuLoggingEnvironment.class).findFirst().get();
			LOG.warning("TestLoggingEnvironmentIsDevelopmentTrue serviceLoader:" + serviceLoader + serviceLoader.hashCode() + " will return " + mockReturn
					+ mockReturn.hashCode());
			assertTrue(LogEventFactory.isDevelopment());
		}
	}

	/**
	 * Test bootstrap method.
	 */
	@Test
//	@Disabled
	public void TestBootstrap() {
		Logger rootLogger = Logger.getLogger("");
		Handler[] rootHandlers = Logger.getLogger("").getHandlers();
		assertNotNull(rootHandlers);
		System.err.println("rootHandlers.length: " + rootHandlers.length);
		assertEquals(rootLogger, Logger.getLogger(""));
		System.err.println("first handler instance of IuLogHandler? " + (rootHandlers[0] instanceof IuLogHandler));
//		assertEquals(1, rootHandlers.length);
		assertTrue(rootHandlers[0] instanceof IuLogHandler);
	}

	/**
	 * Test Environment Properties already initialized.
	 */
	@Test
//	@Disabled
	public void TestEnvironmentPropertiesNeverInitialized() {
		try (MockedStatic<ServiceLoader> serviceLoader = mockStatic(ServiceLoader.class, Answers.RETURNS_DEEP_STUBS)) {
			serviceLoader.when(() -> ServiceLoader.load(IuLoggingEnvironment.class).findFirst().get()).thenReturn(null);
			LogEventFactory.getEnvironment();
			LogEventFactory.getEnvironment();
//			serviceLoader.reset();
//			serviceLoader.clearInvocations();
		}
	}

	/**
	 * Test default value of static getCurrentContext method.
	 */
	@Test
	public void TestGetCurrentContextDefault() {
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
	public void TestBoundDefaultContext() {
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
	public void TestBoundOverriddenContext() {
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
	public void TestCreateEvent() {
		try (var serviceLoader = mockConditionalEnvironmentProperties(true)) {
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
			assertEquals(RuntimeMode.TEST, event.getMode(), "Incorrect Overridden Runtime Mode");
			assertNull(event.getModule());
			assertNull(event.getNodeId());
			assertNull(event.getRemoteAddr());
			assertNull(event.getReqNum());
			assertNull(event.getRuntime());
			assertEquals("Test Source Class Name", event.getSourceClassName(), "Incorrect Overridden Source Class Name");
			assertEquals("Test Source Method Name", event.getSourceMethodName(), "Incorrect Overridden Source Method Name");
			assertEquals("main", event.getThread());
			assertEquals("Test Thrown", event.getThrown(), "Incorrect Overridden Thrown");
			assertNull(event.getUserPrincipal());		
		}
	}

}
