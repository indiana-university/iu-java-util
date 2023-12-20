package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import edu.iu.logging.IuLoggingEnvironment.RuntimeMode;
import iu.logging.TestIuLoggingEnvironmentImpl;

/**
 * Test class for IuLoggingEnvironment.
 */
public class IuLoggingEnvironmentTest {

	/**
	 * Test default methods.
	 */
	@Test
	public void testIuLoggingEnvironmentDefaults() {
		IuLoggingEnvironment environment = new IuLoggingEnvironment() {
		};

		assertNull(environment.getApplication());
		assertNull(environment.getComponent());
		assertNull(environment.getEnvironment());
		assertNull(environment.getHostname());
		assertNull(environment.getMode());
		assertNull(environment.getModule());
		assertNull(environment.getNodeId());
		assertNull(environment.getRuntime());
	}

	/**
	 * Test overridden methods.
	 */
	@Test
	public void testIuLoggingEnvironmentOverridden() {
		IuLoggingEnvironment environment = new TestIuLoggingEnvironmentImpl();

		assertEquals("Test Application", environment.getApplication(), "Incorrect Overridden Application");
		assertEquals("Test Component", environment.getComponent(), "Incorrect Overridden Component");
		assertEquals("Test Environment", environment.getEnvironment(), "Incorrect Overridden Environment");
		assertEquals("Test Hostname", environment.getHostname(), "Incorrect Overridden Hostname");
		assertEquals(RuntimeMode.TEST, environment.getMode(), "Incorrect Overridden Runtime Mode");
		assertEquals("Test Module", environment.getModule(), "Incorrect Overridden Module");
		assertEquals("Test Node Id", environment.getNodeId(), "Incorrect Overridden Node Id");
		assertEquals("Test Runtime", environment.getRuntime(), "Incorrect Overridden Runtime");
	}

	/**
	 * Test all RuntimeModes.
	 */
	@Test
	public void testRuntimeMode() {
		assertEquals(3, RuntimeMode.values().length);
		assertEquals(RuntimeMode.DEVELOPMENT, RuntimeMode.valueOf(RuntimeMode.class, "DEVELOPMENT"));
		assertEquals(RuntimeMode.TEST, RuntimeMode.valueOf(RuntimeMode.class, "TEST"));
		assertEquals(RuntimeMode.PRODUCTION, RuntimeMode.valueOf(RuntimeMode.class, "PRODUCTION"));
	}
	
	/**
	 * Test bootstrap. Just a pass-through to LogEventFactory.bootstrap, so not checking boostrap results.
	 */
	@Test
	public void testBootstrap() {
		IuLoggingEnvironment.bootstrap(Thread.currentThread().getContextClassLoader());
	}
}
