package edu.iu.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;

import edu.iu.logging.IuLoggingEnvironment;

public class TestLoggingEnvironmentProperties implements IuLoggingEnvironment {

	private final Properties properties;

	public TestLoggingEnvironmentProperties() {
		properties = new Properties();
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/iu.properties")) {
			properties.load(in);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public String getApplication() {
		return properties.getProperty("application");
	}

	@Override
	public String getComponent() {
		return properties.getProperty("component");
	}

	@Override
	public String getEndpoint() {
		return properties.getProperty("endpoint");
	}

	@Override
	public String getEnvironment() {
		return properties.getProperty("environment");
	}

	@Override
	public String getHostname() {
		return properties.getProperty("hostname");
	}

	@Override
	public String getModule() {
		return properties.getProperty("module");
	}

	@Override
	public RuntimeMode getMode() {
		String mode = properties.getProperty("mode");
		if (RuntimeMode.PRODUCTION.name().equalsIgnoreCase(mode)) {
			return RuntimeMode.PRODUCTION;
		}
		if (RuntimeMode.TEST.name().equalsIgnoreCase(mode)) {
			return RuntimeMode.TEST;
		}
		if (RuntimeMode.DEVELOPMENT.name().equalsIgnoreCase(mode)) {
			return RuntimeMode.DEVELOPMENT;
		}
		return null;
	}

	@Override
	public String getNodeId() {
		return getApplication() + "/test";
	}

}
