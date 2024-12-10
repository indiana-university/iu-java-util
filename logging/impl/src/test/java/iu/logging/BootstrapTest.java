package iu.logging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import iu.logging.internal.IuLogHandler;

@SuppressWarnings("javadoc")
public class BootstrapTest {

	private String nodeId = IuException.unchecked(() -> InetAddress.getLocalHost().getHostName());
	private String endpoint;
	private String application;
	private String environment;

	@BeforeEach
	public void setup() {
		endpoint = IdGenerator.generateId();
		application = IdGenerator.generateId();
		environment = IdGenerator.generateId();
		System.setProperty("iu.logging.endpoint", endpoint);
		System.setProperty("iu.logging.application", application);
		System.setProperty("iu.logging.environment", environment);
		System.setProperty("iu.logging.logPath", "target/logs");
	}

	@AfterEach
	public void teardown() {
		System.getProperties().remove("iu.logging.logPath");
	}

	@Test
	public void testInitNoConfig() {
		System.setProperty("iu.config", "target/" + IdGenerator.generateId());
		try {
			final var root = mock(Logger.class);
			when(root.getHandlers()).thenReturn(new Handler[] { new ConsoleHandler() });

			final var logManager = mock(LogManager.class);
			when(logManager.getLogger("")).thenReturn(root);

			try (final var mockLogManager = mockStatic(LogManager.class)) {
				mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
				Bootstrap.init(ClassLoader.getSystemClassLoader());

				verify(root).addHandler(Bootstrap.HANDLER);
				verify(root)
						.config("IU Logging Bootstrap initialized " + Bootstrap.HANDLER + " DefaultLogContext [nodeId="
								+ nodeId + ", endpoint=" + endpoint + ", application=" + application + ", environment="
								+ environment + "]; context: " + ClassLoader.getSystemClassLoader());
			}
		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testInitWithConfig() {
		System.setProperty("iu.config", "target/test-classes");
		try {
			final var root = mock(Logger.class);
			when(root.getHandlers()).thenReturn(new Handler[] { new ConsoleHandler() });

			final var logManager = mock(LogManager.class);
			when(logManager.getLogger("")).thenReturn(root);

			try (final var mockLogManager = mockStatic(LogManager.class)) {
				mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
				Bootstrap.init(ClassLoader.getSystemClassLoader());

				verify(root).addHandler(Bootstrap.HANDLER);
				verify(root)
						.config("IU Logging Bootstrap initialized " + Bootstrap.HANDLER + " DefaultLogContext [nodeId="
								+ nodeId + ", endpoint=" + endpoint + ", application=" + application + ", environment="
								+ environment + "]; context: " + ClassLoader.getSystemClassLoader());
			}
		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testInitAreadyInitialized() {
		final var root = mock(Logger.class);
		final var handler = mock(IuLogHandler.class);
		when(root.getHandlers()).thenReturn(new Handler[] { handler });

		final var logManager = mock(LogManager.class);
		when(logManager.getLogger("")).thenReturn(root);

		try (final var mockLogManager = mockStatic(LogManager.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
			Bootstrap.init(ClassLoader.getSystemClassLoader());
			verify(root, never()).addHandler(any());
			verify(root, never()).config(any(String.class));
		}
	}

}
