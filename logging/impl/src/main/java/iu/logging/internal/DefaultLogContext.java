package iu.logging.internal;

import java.net.InetAddress;
import java.util.logging.Level;

import edu.iu.IuException;
import iu.logging.LogContext;

/**
 * {@link LogContext} to fall back on, by context, when none is active on the
 * current thread.
 */
public class DefaultLogContext implements LogContext {

	private final String nodeId = IuException.unchecked(() -> InetAddress.getLocalHost().getHostName());
	private final String endpoint;
	private final String application;
	private final String environment;

	/**
	 * Constructor
	 * 
	 * @param endpoint    {@link #endpoint}
	 * @param application {@link #application}
	 * @param environment {@link #environment}
	 */
	public DefaultLogContext(String endpoint, String application, String environment) {
		this.endpoint = endpoint;
		this.application = application;
		this.environment = environment;
	}

	@Override
	public String getRequestId() {
		return null;
	}

	@Override
	public String getNodeId() {
		return nodeId;
	}

	@Override
	public String getEndpoint() {
		return endpoint;
	}

	@Override
	public String getApplication() {
		return application;
	}

	@Override
	public String getEnvironment() {
		return environment;
	}

	@Override
	public String getModule() {
		return null;
	}

	@Override
	public String getComponent() {
		return null;
	}

	@Override
	public Level getLevel() {
		return null;
	}

	@Override
	public boolean isDevelopment() {
		return false;
	}

	@Override
	public String getCallerIpAddress() {
		return null;
	}

	@Override
	public String getCalledUrl() {
		return null;
	}

	@Override
	public String getCallerPrincipalName() {
		return null;
	}

	@Override
	public String getImpersonatedPrincipalName() {
		return null;
	}

	@Override
	public String toString() {
		return "DefaultLogContext [nodeId=" + nodeId + ", endpoint=" + endpoint + ", application=" + application
				+ ", environment=" + environment + "]";
	}

}
