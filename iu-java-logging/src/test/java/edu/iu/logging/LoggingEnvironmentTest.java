package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Handler;
import java.util.logging.Logger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import edu.iu.logging.IuLoggingEnvironment.RuntimeMode;
import iu.logging.AuditUtilTest;
import iu.logging.IuLogHandler;

public class LoggingEnvironmentTest {

	private static final Logger LOG;
	static {
		LoggingEnvironment.bootstrap(LoggingEnvironmentTest.class.getClassLoader());
		LOG = Logger.getLogger(LoggingEnvironmentTest.class.getName());
	}

	@Test
	public void TestLoggingEnvironmentDefaults() {
		assertNull(LoggingEnvironment.getApplication());
		assertNull(LoggingEnvironment.getComponent());
		assertNull(LoggingEnvironment.getEndpoint());
		assertNull(LoggingEnvironment.getEnvironment());
//		assertNull(LoggingEnvironment.getHostname());
//		assertNull(LoggingEnvironment.getMode());
		assertNull(LoggingEnvironment.getModule());
		assertNull(LoggingEnvironment.getNodeId());
		assertEquals(false, LoggingEnvironment.isDevelopment());
	}

	@Test
	public void TestBootstrap() {
		Logger rootLogger = Logger.getLogger("");
		Handler[] rootHandlers = Logger.getLogger("").getHandlers();
		assertNotNull(rootHandlers);
		System.err.println("rootHandlers.length: " + rootHandlers.length);
		assertEquals(rootLogger, Logger.getLogger(""));
		System.err.println("first handler instance of IuLogHandler? " + (rootHandlers[0] instanceof IuLogHandler));
		assertEquals(1, rootHandlers.length);
		assertTrue(rootHandlers[0] instanceof IuLogHandler);
	}

	@Test
	@Disabled
	public void TestIuLoggingEnvironmentOverridden() {
		IuLoggingEnvironment environment = new IuLoggingEnvironment() {
			@Override
			public String getApplication() {
				return "Test Application";
			}

			@Override
			public String getComponent() {
				return "Test Component";
			}

			@Override
			public String getEndpoint() {
				return "Test Endpoint";
			}

			@Override
			public String getEnvironment() {
				return "Test Environment";
			}

			@Override
			public String getHostname() {
				return "Test Hostname";
			}

			@Override
			public RuntimeMode getMode() {
				return RuntimeMode.TEST;
			}

			@Override
			public String getModule() {
				return "Test Module";
			}

			@Override
			public String getNodeId() {
				return "Test Node Id";
			}
		};

		assertEquals("Test Application", environment.getApplication(), "Incorrect Overridden Application");
		assertEquals("Test Component", environment.getComponent(), "Incorrect Overridden Component");
		assertEquals("Test Endpoint", environment.getEndpoint(), "Incorrect Overridden Endpoint");
		assertEquals("Test Environment", environment.getEnvironment(), "Incorrect Overridden Environment");
		assertEquals("Test Hostname", environment.getHostname(), "Incorrect Overridden Hostname");
		assertEquals(RuntimeMode.TEST, environment.getMode(), "Incorrect Overridden Runtime Mode");
		assertEquals("Test Module", environment.getModule(), "Incorrect Overridden Module");
		assertEquals("Test Node Id", environment.getNodeId(), "Incorrect Overridden Node Id");
	}

	@Test
	@Disabled
	public void TestRuntimeMode() {
		assertEquals(3, RuntimeMode.values().length);
		assertEquals(RuntimeMode.DEVELOPMENT, RuntimeMode.valueOf(RuntimeMode.class, "DEVELOPMENT"));
		assertEquals(RuntimeMode.TEST, RuntimeMode.valueOf(RuntimeMode.class, "TEST"));
		assertEquals(RuntimeMode.PRODUCTION, RuntimeMode.valueOf(RuntimeMode.class, "PRODUCTION"));
	}
}
