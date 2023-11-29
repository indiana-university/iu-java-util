package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import edu.iu.logging.IuLoggingEnvironment.RuntimeMode;

public class IuLoggingEnvironmentTest {
	
	class LoggingEnvironmentHelper implements IuLoggingEnvironment {
		private String application;
		private String component;
		private String endpoint;
		private String environment;
		private String hostname;
		private RuntimeMode mode;
		private String module;
		private String nodeId;

		@Override
		public String getApplication() {
			return application == null ? "Test Application" : application;
		}
		
		public void setApplication(String application) {
			this.application = application;
		}

		@Override
		public String getComponent() {
			return component == null ? "Test Component" : component;
		}

		public void setComponent(String component) {
			this.component = component;
		}

		@Override
		public String getEndpoint() {
			return endpoint == null ? "Test Endpoint" : endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}

		@Override
		public String getEnvironment() {
			return environment == null ? "Test Environment" : environment;
		}

		public void setEnvironment(String environment) {
			this.environment = environment;
		}

		@Override
		public String getHostname() {
			return hostname == null ? "Test Hostname" : hostname;
		}

		public void setHostname(String hostname) {
			this.hostname = hostname;
		}

		@Override
		public RuntimeMode getMode() {
			return mode == null ? RuntimeMode.TEST : mode;
		}

		public void setMode(RuntimeMode mode) {
			this.mode = mode;
		}

		@Override
		public String getModule() {
			return module == null ? "Test Module" : module;
		}

		public void setModule(String module) {
			this.module = module;
		}

		@Override
		public String getNodeId() {
			return nodeId == null ? "Test Node Id" : nodeId;
		}

		public void setNodeId(String nodeId) {
			this.nodeId = nodeId;
		}
	}

	@Test
	public void TestIuLoggingEnvironmentDefaults() {
		IuLoggingEnvironment environment = new IuLoggingEnvironment() {};

		assertNull(environment.getApplication());
		assertNull(environment.getComponent());
		assertNull(environment.getEndpoint());
		assertNull(environment.getEnvironment());
		assertNull(environment.getHostname());
		assertNull(environment.getMode());
		assertNull(environment.getModule());
		assertNull(environment.getNodeId());
	}

	@Test
	public void TestIuLoggingEnvironmentOverridden() {
		IuLoggingEnvironment environment = new LoggingEnvironmentHelper();

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
	public void TestRuntimeMode() {
		assertEquals(3, RuntimeMode.values().length);
		assertEquals(RuntimeMode.DEVELOPMENT, RuntimeMode.valueOf(RuntimeMode.class, "DEVELOPMENT"));
		assertEquals(RuntimeMode.TEST, RuntimeMode.valueOf(RuntimeMode.class, "TEST"));
		assertEquals(RuntimeMode.PRODUCTION, RuntimeMode.valueOf(RuntimeMode.class, "PRODUCTION"));
	}
}
