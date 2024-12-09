package iu.logging.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.net.InetAddress;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class LoggingBootstrapIT {

	@Test
	public void testInit() {
		final var nodeId = assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostName());
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		System.setProperty("iu.logging.logPath", "target/logs");
		IuTestLogger.allow("", Level.CONFIG,
				"IU Logging Bootstrap initialized IuLogHandler [logPath=target" + File.separator
						+ "logs] DefaultLogContext [nodeId=" + nodeId + ", endpoint=" + endpoint + ", application="
						+ application + ", environment=" + environment + "]; context: "
						+ ClassLoader.getPlatformClassLoader());
		IuTestLogger.allow("", Level.CONFIG, "Logging configuration updated from .*");
		assertDoesNotThrow(() -> LoggingBootstrap.init(endpoint, application, environment));
	}

}
