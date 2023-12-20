package iu.logging;

import edu.iu.logging.IuLoggingEnvironment;

/**
 * Test helper implementation for IuLoggingEnvironment.
 */
public class TestIuLoggingEnvironmentImpl implements IuLoggingEnvironment {
	private String application;
	private String component;
	private String environment;
	private String hostname;
	private RuntimeMode mode;
	private String module;
	private String nodeId;
	private String runtime;

	@Override
	public String getApplication() {
		return application == null ? "Test Application" : application;
	}

	/**
	 * Set application for testing.
	 * 
	 * @param application
	 */
	public void setApplication(String application) {
		this.application = application;
	}

	@Override
	public String getComponent() {
		return component == null ? "Test Component" : component;
	}

	/**
	 * Set component for testing.
	 * 
	 * @param component
	 */
	public void setComponent(String component) {
		this.component = component;
	}

	@Override
	public String getEnvironment() {
		return environment == null ? "Test Environment" : environment;
	}

	/**
	 * Set environment for testing.
	 * 
	 * @param environment
	 */
	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	@Override
	public String getHostname() {
		return hostname == null ? "Test Hostname" : hostname;
	}

	/**
	 * Set hostname for testing.
	 * 
	 * @param hostname
	 */
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	@Override
	public RuntimeMode getMode() {
		return mode == null ? RuntimeMode.TEST : mode;
	}

	/**
	 * Set mode for testing.
	 * 
	 * @param mode
	 */
	public void setMode(RuntimeMode mode) {
		this.mode = mode;
	}

	@Override
	public String getModule() {
		return module == null ? "Test Module" : module;
	}

	/**
	 * Set module for testing.
	 * 
	 * @param module
	 */
	public void setModule(String module) {
		this.module = module;
	}

	@Override
	public String getNodeId() {
		return nodeId == null ? "Test Node Id" : nodeId;
	}

	/**
	 * Set node id for testing.
	 * 
	 * @param nodeId
	 */
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	@Override
	public String getRuntime() {
		return runtime == null ? "Test Runtime" : runtime;
	}

	/**
	 * Set runtime for testing.
	 * 
	 * @param runtime
	 */
	public void setRuntime(String runtime) {
		this.runtime = runtime;
	}
}
